package pages;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class OnboardingPage {

    private static final Duration EXPLICIT_WAIT = Duration.ofSeconds(10);
    private static final Duration SHORT_WAIT = Duration.ofSeconds(3);
    private static final Duration PERMISSION_WAIT = Duration.ofSeconds(5);
    private static final Duration TRANSITION_WAIT = Duration.ofSeconds(2);
    // Bumped 3 -> 5. On a fresh cold start (BaseTest clears app data every test) the
    // onboarding carousel can take well over 30s to become interactive — the app shows a
    // splash + an always-animating SVG, and a late system permission dialog can sit on top
    // of it. 3 x 10s was measured on-device to sometimes not be enough, spuriously failing
    // navigation before the test logic ran. Each retry now also re-dismisses any permission
    // dialog and settles briefly (see waitForElementWithRetry).
    private static final int MAX_RETRIES = 5;

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // NOTE: content-desc is literally "Next, " (React Native auto-joins nested Text nodes'
    // accessibility labels with ", "). UiAutomator2's XPath engine fails to exact-match this
    // value (@content-desc="Next, ") — confirmed on-device it returns zero results even though
    // the node genuinely exists — so this uses contains() instead, which matches reliably.
    private static final By NEXT_BUTTON = By.xpath("//android.view.ViewGroup[contains(@content-desc,\"Next\")]");
    // Use contains() rather than an exact match — same trailing ", " quirk documented for
    // NEXT_BUTTON above (React Native joins nested Text labels with ", "). Exact-matching
    // @content-desc="Skip" was an intermittent source of "Skip button not found" failures.
    private static final By SKIP_BUTTON = By.xpath("//android.view.ViewGroup[contains(@content-desc,\"Skip\")]");

    // On every fresh app launch (BaseTest clears app data before each test), Android may
    // show a system runtime-permission dialog before the onboarding carousel is even
    // visible — e.g. for location/notifications. Same resource-id pattern already
    // confirmed working elsewhere in this codebase (SignupPage's photo-picker permission
    // handling). Defaults to Allow so the app can function normally during automation.
    private static final By PERMISSION_ALLOW_BUTTON =
            By.id("com.android.permissioncontroller:id/permission_allow_button");

    // The onboarding carousel (Next x4 or Skip) does NOT lead straight to the signup form —
    // it leads to an intermediate "Welcome to KnackCity" screen with two calls to action:
    // Get Started (-> signup) and Sign In (-> login). Get Started has a clean, unique
    // resource-id; Sign In follows the app-wide ViewGroup-wrapper pattern (content-desc
    // exact, no trailing-comma quirk). Both confirmed on-device.
    private static final By GET_STARTED_BUTTON = By.xpath("//*[@resource-id=\"get-started-button\"]");
    // contains() for the same trailing ", " reason as NEXT_BUTTON / SKIP_BUTTON.
    private static final By SIGN_IN_BUTTON = By.xpath("//android.view.ViewGroup[contains(@content-desc,\"Sign In\")]");

    // Once Get Started has been clicked once, noReset=true means later sessions in the same
    // suite run boot straight into the signup form itself, skipping both the carousel AND
    // the Welcome screen. Same marker SignupPage uses for its full name field.
    private static final By SIGNUP_FORM_MARKER = By.xpath("//android.widget.EditText[@text=\"Enter your full name\"]");

    public OnboardingPage(AndroidDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, EXPLICIT_WAIT);
    }

    private WebElement waitForElementWithRetry(By locator, String elementName) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("[OnboardingPage] Waiting for '" + elementName + "' (attempt " + attempt + "/" + MAX_RETRIES + ")");
                return wait.until(ExpectedConditions.elementToBeClickable(locator));
            } catch (Exception e) {
                lastException = e;
                // Keep the log to one line — the full Selenium dump is noise on a retry.
                System.out.println("[OnboardingPage] '" + elementName + "' not clickable yet (attempt "
                        + attempt + "/" + MAX_RETRIES + ")");
                if (attempt < MAX_RETRIES) {
                    // A late-appearing system permission dialog is the most common thing
                    // sitting on top of the carousel; clear it, settle, then retry.
                    handleInitialPermissionIfPresent();
                    pauseForTransition();
                }
            }
        }
        throw new RuntimeException("[OnboardingPage] '" + elementName + "' not found after " + MAX_RETRIES + " attempts", lastException);
    }

    private void pauseForTransition() {
        try {
            Thread.sleep(TRANSITION_WAIT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isDisplayedWithinShortWait(By locator) {
        try {
            WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT);
            return shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * With noReset=true, the app remembers how far onboarding got across sessions:
     * once the carousel has been completed once (via Next or Skip), later sessions in the
     * same suite run boot straight to the Welcome screen with no carousel; once Get Started
     * has been clicked once, later sessions boot straight into the signup form itself,
     * skipping both the carousel AND the Welcome screen.
     */
    private boolean isPastOnboardingCarousel() {
        return isDisplayedWithinShortWait(GET_STARTED_BUTTON) || isDisplayedWithinShortWait(SIGNUP_FORM_MARKER);
    }

    private boolean isPastWelcomeScreen() {
        return isDisplayedWithinShortWait(SIGNUP_FORM_MARKER);
    }

    /**
     * Dismisses the initial system permission dialog if it appears on app launch.
     * Best-effort: uses a short wait since the dialog may or may not show up, and does
     * nothing if it never appears.
     */
    private void handleInitialPermissionIfPresent() {
        try {
            WebDriverWait permissionWait = new WebDriverWait(driver, PERMISSION_WAIT);
            WebElement allowButton = permissionWait.until(ExpectedConditions.elementToBeClickable(PERMISSION_ALLOW_BUTTON));
            allowButton.click();
            System.out.println("[OnboardingPage] Handled initial permission dialog (Allow)");
            pauseForTransition();
        } catch (Exception e) {
            // No permission dialog appeared — fine, nothing to dismiss.
        }
    }

    public void clickNextButton() {
        WebElement nextButton = waitForElementWithRetry(NEXT_BUTTON, "Next button");
        nextButton.click();
        System.out.println("[OnboardingPage] Clicked Next button");
        pauseForTransition();
    }

    public void clickSkipButton() {
        handleInitialPermissionIfPresent();
        if (isPastOnboardingCarousel()) {
            System.out.println("[OnboardingPage] Onboarding carousel already completed in a prior session — skipping Skip button click");
            return;
        }
        WebElement skipButton = waitForElementWithRetry(SKIP_BUTTON, "Skip button");
        skipButton.click();
        System.out.println("[OnboardingPage] Clicked Skip button");
        pauseForTransition();
    }

    public void clickNextButtonFourTimes() {
        handleInitialPermissionIfPresent();
        if (isPastOnboardingCarousel()) {
            System.out.println("[OnboardingPage] Onboarding carousel already completed in a prior session — skipping Next button clicks");
            return;
        }
        for (int i = 1; i <= 4; i++) {
            System.out.println("[OnboardingPage] Next click " + i + " of 4");
            clickNextButton();
        }
    }

    public void clickGetStartedButton() {
        if (isPastWelcomeScreen()) {
            System.out.println("[OnboardingPage] Already past the Welcome screen (signup form already showing) — skipping Get Started button click");
            return;
        }
        WebElement getStartedButton = waitForElementWithRetry(GET_STARTED_BUTTON, "Get Started button");
        getStartedButton.click();
        System.out.println("[OnboardingPage] Clicked Get Started button");
        pauseForTransition();
    }

    /**
     * Clicks Sign In directly from the Welcome screen (as opposed to reaching Sign In
     * via the Signup screen's own "Sign In" link).
     */
    public void clickSignInButton() {
        WebElement signInButton = waitForElementWithRetry(SIGN_IN_BUTTON, "Sign In button (Welcome screen)");
        signInButton.click();
        System.out.println("[OnboardingPage] Clicked Sign In button (Welcome screen)");
        pauseForTransition();
    }
}
