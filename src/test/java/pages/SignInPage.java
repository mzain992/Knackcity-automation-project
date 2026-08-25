package pages;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SignInPage {

    private static final Duration EXPLICIT_WAIT = Duration.ofSeconds(15);
    private static final Duration SCROLL_RETRY_WAIT = Duration.ofSeconds(5);
    private static final Duration ACTION_WAIT = Duration.ofSeconds(2);
    // Confirmed on-device: the Sign In button can be covered by the on-screen keyboard
    // after typing into Password, the same issue fixed on the Signup form.
    private static final int MAX_SCROLL_ATTEMPTS = 3;

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // ---------------- Locators ----------------

    private static final By EMAIL_FIELD = By.xpath("//android.widget.EditText[@text=\"Enter your email\"]");
    // BUG FIX: previously anchored on the password="true"/"false" attribute, but that
    // attribute is present on EVERY EditText (it's "false" on Email too, not just the
    // actual password field), so this filter matched *any* text field — with no index,
    // findElement returned the FIRST match in document order, which is Email (it comes
    // before Password on screen), not Password. Anchored on the stable "Password *"
    // label text instead via the following:: axis — correct regardless of what's typed
    // or the field's masked/unmasked state, unlike both the placeholder text (changes
    // once typed) and the password attribute (not unique, and flips on toggle).
    private static final By PASSWORD_FIELD =
            By.xpath("//android.widget.TextView[@text=\"Password *\"]/following::android.widget.EditText[1]");

    // The eye icon has no resource-id and an empty content-desc/text (confirmed
    // on-device), so it can't be targeted directly by value. It's reliably the
    // ViewGroup immediately following the password EditText.
    private static final By EYE_ICON = By.xpath(
            "//android.widget.TextView[@text=\"Password *\"]/following::android.widget.EditText[1]/following-sibling::android.view.ViewGroup[1]");

    private static final By FORGOT_PASSWORD_LINK = By.xpath("//android.view.ViewGroup[@content-desc=\"Forgot Password?\"]");
    // BUG FIX: previously matched on an exact-empty content-desc string, which proved
    // unreliable via live XPath findElement on this UiAutomator2 setup. Every screen in
    // this app shares one header component, resource-id="custom-header" (confirmed
    // on-device identical on the Forgot Password screen and the Signup Terms page), and
    // the back button is reliably its immediately-following clickable sibling.
    private static final By FORGOT_PASSWORD_BACK_BUTTON =
            By.xpath("//*[@resource-id=\"custom-header\"]/following-sibling::android.view.ViewGroup[1]");

    // Same app-wide pattern as every other primary button: the clickable node is the
    // ViewGroup wrapper (content-desc exact), not the inner TextView. Confirmed on-device.
    private static final By SIGN_IN_BUTTON = By.xpath("//android.view.ViewGroup[@content-desc=\"Sign In\"]");

    // Confirmed on-device: successful sign-in lands on the same shared home screen as
    // successful signup — "Welcome to Knackcity!" (the greeting name above it varies per
    // account, so this anchors on the static line instead).
    private static final By SIGNIN_SUCCESS_MARKER = By.xpath("//android.widget.TextView[@text=\"Welcome to Knackcity!\"]");

    private static final By CONTINUE_WITH_GOOGLE = By.xpath("//android.widget.TextView[@text=\"Continue with Google\"]");
    // Native Google account chooser. The row for each account is a
    // clickable LinearLayout[resource-id="container"], but the exact node this xpath
    // targets is a non-clickable inner LinearLayout — confirmed on-device that tapping it
    // still registers correctly since its bounds sit fully inside the clickable parent
    // row (same coordinate-tap-through-a-non-clickable-child pattern already relied on
    // elsewhere in this app, e.g. button labels). Targets the first listed account.
    private static final By FIRST_GOOGLE_ACCOUNT = By.xpath(
            "//android.support.v7.widget.RecyclerView[@resource-id=\"com.google.android.gms:id/list\"]"
                    + "/android.widget.LinearLayout[1]/android.widget.LinearLayout");

    public SignInPage(AndroidDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, EXPLICIT_WAIT);
    }

    // ---------------- Internal helpers ----------------

    private void pauseForAction() {
        try {
            Thread.sleep(ACTION_WAIT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Hides the on-screen keyboard after typing. Confirmed on-device: a keyboard left
     * open after sendKeys() can cover the Sign In button, blocking clicks/scroll — same
     * fix applied to the Signup form.
     */
    private void hideKeyboardIfShown() {
        try {
            driver.hideKeyboard();
        } catch (Exception ignored) {
            // No keyboard was showing — fine.
        }
    }

    private void scrollDown() {
        Map<String, Object> params = new HashMap<>();
        params.put("left", 50);
        params.put("top", 400);
        params.put("width", 620);
        params.put("height", 900);
        params.put("direction", "down");
        params.put("percent", 0.8);
        try {
            driver.executeScript("mobile: scrollGesture", params);
        } catch (Exception e) {
            System.out.println("[SignInPage] scrollDown failed: " + e.getMessage());
        }
    }

    /**
     * Locates a clickable element, scrolling down and retrying if it isn't visible yet
     * (e.g. the Sign In button hidden behind the keyboard after typing the password).
     */
    private WebElement findClickableWithScroll(By locator, String label) {
        for (int attempt = 1; attempt <= MAX_SCROLL_ATTEMPTS; attempt++) {
            try {
                WebDriverWait attemptWait = attempt == 1 ? wait : new WebDriverWait(driver, SCROLL_RETRY_WAIT);
                return attemptWait.until(ExpectedConditions.elementToBeClickable(locator));
            } catch (Exception e) {
                if (attempt == MAX_SCROLL_ATTEMPTS) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
                System.out.println("[SignInPage] '" + label + "' not visible yet, scrolling down (attempt " + attempt + "/" + MAX_SCROLL_ATTEMPTS + ")");
                scrollDown();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private boolean click(By locator, String actionName) {
        try {
            WebElement element = findClickableWithScroll(locator, actionName);
            element.click();
            System.out.println("[SignInPage] Clicked '" + actionName + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignInPage] '" + actionName + "' failed: " + e.getMessage());
            return false;
        }
    }

    private boolean type(By locator, String text, String fieldName) {
        try {
            WebElement element = findClickableWithScroll(locator, fieldName);
            element.click();
            element.clear();
            element.sendKeys(text);
            hideKeyboardIfShown();
            String masked = fieldName.toLowerCase().contains("password") ? "****" : text;
            System.out.println("[SignInPage] Filled '" + fieldName + "' with '" + masked + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignInPage] Could not enter text into '" + fieldName + "': " + e.getMessage());
            return false;
        }
    }

    private boolean isDisplayed(By locator) {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(locator)).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- Screen marker ----------------

    /**
     * Checks for the Password field specifically, not just the email field — the
     * Forgot Password screen shares the same "Enter your email" field (confirmed
     * on-device), so checking email alone gives a false positive there.
     */
    public boolean isSignInScreenDisplayed() {
        return isDisplayed(PASSWORD_FIELD);
    }

    // ---------------- Form fields ----------------

    public boolean enterEmail(String email) {
        return type(EMAIL_FIELD, email, "Email");
    }

    public boolean enterPassword(String password) {
        return type(PASSWORD_FIELD, password, "Password");
    }

    /**
     * Clicks the eye icon to toggle password masking. Returns whether the click
     * succeeded — use isPasswordMasked() before/after to verify the actual show/hide
     * behavior (the EditText's password attribute flips true/false, confirmed on-device).
     */
    public boolean togglePasswordVisibility() {
        return click(EYE_ICON, "togglePasswordVisibility");
    }

    /**
     * True if the password field is currently masking its content (password="true" in
     * the accessibility tree — confirmed on-device this flips to "false" once the eye
     * icon reveals the plaintext value).
     */
    public boolean isPasswordMasked() {
        try {
            WebElement field = wait.until(ExpectedConditions.presenceOfElementLocated(PASSWORD_FIELD));
            return "true".equals(field.getAttribute("password"));
        } catch (Exception e) {
            System.out.println("[SignInPage] Could not read password masking state: " + e.getMessage());
            return true; // fail safe: assume still masked if we can't tell
        }
    }

    // ---------------- Forgot Password ----------------

    public boolean clickForgotPassword() {
        return click(FORGOT_PASSWORD_LINK, "clickForgotPassword");
    }

    /**
     * Verifies navigation only (per spec — detailed Forgot Password cases come later):
     * clicking the link should take us away from the Sign In screen. Checks for the
     * Password field's absence rather than the email field's, since the Forgot Password
     * screen shares the same "Enter your email" field but has no password field
     * (confirmed on-device — it shows "Send Verification Code" instead of "Sign In").
     */
    public boolean isStillOnSignInScreenAfterForgotPasswordClick() {
        return isSignInScreenDisplayed();
    }

    /**
     * Clicks the Forgot Password screen's back arrow, which should return to Sign In.
     */
    public boolean clickForgotPasswordBackButton() {
        return click(FORGOT_PASSWORD_BACK_BUTTON, "clickForgotPasswordBackButton");
    }

    // ---------------- Sign In ----------------

    public boolean clickSignInButton() {
        return click(SIGN_IN_BUTTON, "clickSignInButton");
    }

    public boolean isSignInSuccessful() {
        return isDisplayed(SIGNIN_SUCCESS_MARKER);
    }

    // ---------------- Google Sign In ----------------

    public boolean clickContinueWithGoogle() {
        return click(CONTINUE_WITH_GOOGLE, "clickContinueWithGoogle");
    }

    public boolean selectFirstGoogleAccount() {
        return click(FIRST_GOOGLE_ACCOUNT, "selectFirstGoogleAccount");
    }
}
