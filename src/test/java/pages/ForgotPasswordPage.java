package pages;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Page object for the complete <b>Forgot Password</b> flow:
 * <pre>
 *   Sign In → "Forgot Password?" → enter email → Send Verification Code
 *          → OTP screen → (60s cooldown) → Resend OTP → enter OTP → Verify Code
 *          → "Email Verified successfully" → New Password screen
 *          → new + confirm password (with mismatch validation) → show/hide
 *          → Reset Password → back on the Sign In screen
 * </pre>
 *
 * <p>Only the Forgot Password screens are modelled here. Reaching the Sign In screen,
 * tapping "Forgot Password?" / its Back button, and the final login are all done with the
 * existing {@link OnboardingPage} / {@link SignInPage} objects — this class deliberately
 * does not duplicate or change any of that.
 *
 * <p>Conventions mirror {@link SignInPage}: every interaction goes through the small
 * {@code type} / {@code click} / {@code isDisplayed} helpers, which wrap an explicit
 * {@link WebDriverWait} and never throw (they return a boolean the caller asserts on).
 */
public class ForgotPasswordPage {

    private static final Duration EXPLICIT_WAIT = Duration.ofSeconds(15);
    private static final Duration ACTION_WAIT = Duration.ofSeconds(2);
    private static final Duration SHORT_WAIT = Duration.ofSeconds(3);
    private static final int MAX_SCROLL_ATTEMPTS = 3;

    // Lower/upper alphabets for case-insensitive translate() comparisons in XPath.
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final AndroidDriver driver;
    private final WebDriverWait wait;
    // The OTP screen is identical to the one signup uses — delegate all OTP handling to the
    // shared page object rather than keeping a second copy here.
    private final OtpVerificationPage otpPage;

    // ---------------- Locators ----------------

    // Forgot Password (enter-email) screen. The email field shares the same placeholder as
    // the Sign In screen; the positional fallback keeps it matching once text is typed
    // (an EditText's @text switches from placeholder to the typed value on first keystroke).
    private static final By EMAIL_FIELD = By.xpath(
            "//android.widget.EditText[@text=\"Enter your email\"] | (//android.widget.EditText)[1]");
    private static final By SEND_VERIFICATION_CODE_BUTTON =
            By.xpath("//android.view.ViewGroup[@content-desc=\"Send Verification Code\"]");

    // OTP verification screen — locators/handling live in the shared OtpVerificationPage.
    // Success confirmation after Verify Code. Matched leniently (case-insensitive,
    // substring) so minor copy changes ("Email Verified successfully" vs
    // "Email verified successfully.") don't break the assertion.
    private static final By EMAIL_VERIFIED_MESSAGE = By.xpath(
            "//*[contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"verified successfully\")"
                    + " or contains(translate(@content-desc,\"" + UPPER + "\",\"" + LOWER + "\"),\"verified successfully\")]");

    // New Password screen.
    // On-device the fields are labelled "Password *" / "Confirm Password *" (the plan's
    // "New Password" / "Confirm Password" is the screen heading, not the field text), and
    // a filled EditText's @text is the mask ("••••••••"), never a placeholder. So these
    // anchor on the stable label via the following:: axis — exactly the pattern used in
    // SignInPage/SignupPage — with a positional fallback (New Password screen has two
    // EditTexts: new password then confirm).
    private static final By NEW_PASSWORD_FIELD = By.xpath(
            "//android.widget.TextView[@text=\"Password *\"]/following::android.widget.EditText[1]"
                    + " | (//android.widget.EditText)[1]");
    private static final By CONFIRM_PASSWORD_FIELD = By.xpath(
            "//android.widget.TextView[@text=\"Confirm Password *\"]/following::android.widget.EditText[1]"
                    + " | (//android.widget.EditText)[2]");
    private static final By RESET_PASSWORD_BUTTON =
            By.xpath("//android.view.ViewGroup[@content-desc=\"Reset Password\"]");
    // Password-mismatch validation. The app renders field errors as TextViews with a
    // resource-id of "error-<field>" (confirmed pattern elsewhere, e.g. "error-email"),
    // and mismatch copy typically contains "match" ("Passwords do not match"). OR-matches
    // both shapes, lower-casing for a case-insensitive compare.
    // Confirmed on-device: with a non-matching confirm value the screen shows a TextView
    // whose (lower-cased) text contains "match" ("Passwords do not match" family). No other
    // text on the New Password screen contains "match", so this substring test is safe
    // here; the "error-<field>" resource-id shape is also matched as a fallback.
    private static final By PASSWORD_MISMATCH_ERROR = By.xpath(
            "//android.widget.TextView[starts-with(@resource-id,\"error-\")"
                    + " or contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"match\")]");
    // Eye (show/hide) icon for the New Password field. The plan suggests
    // (//android.widget.TextView[@text=""])[2], but on this build that node's text is a
    // private-use glyph (not empty) and the tappable element is the CLICKABLE ViewGroup
    // wrapping it — same shape as SignInPage.EYE_ICON. Anchored structurally as the first
    // ViewGroup following the New Password EditText (its show/hide affordance), with a
    // following-sibling variant first.
    private static final By NEW_PASSWORD_EYE_ICON = By.xpath(
            "//android.widget.TextView[@text=\"Password *\"]/following::android.widget.EditText[1]"
                    + "/following-sibling::android.view.ViewGroup[1]"
                    + " | //android.widget.TextView[@text=\"Password *\"]/following::android.widget.EditText[1]"
                    + "/following::android.view.ViewGroup[1]");

    // The REAL Sign In screen, reached after a successful reset. Checked via controls that
    // are unique to it — SignInPage.isSignInScreenDisplayed() keys off a "Password *" field,
    // which the New Password screen ALSO has, so it would false-positive there.
    private static final By SIGN_IN_SCREEN_STRICT_MARKER = By.xpath(
            "//android.view.ViewGroup[@content-desc=\"Forgot Password?\"]"
                    + " | //android.view.ViewGroup[@content-desc=\"Continue with Google\"]"
                    + " | //android.widget.TextView[@text=\"Welcome Back\"]");

    public ForgotPasswordPage(AndroidDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, EXPLICIT_WAIT);
        this.otpPage = new OtpVerificationPage(driver);
    }

    // ---------------- Internal helpers (same style as SignInPage) ----------------

    private void pauseForAction() {
        try {
            Thread.sleep(ACTION_WAIT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Hides the on-screen keyboard after typing so it can't cover a button below. */
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
            System.out.println("[ForgotPasswordPage] scrollDown failed: " + e.getMessage());
        }
    }

    /** Locates a clickable element, scrolling down and retrying if it isn't visible yet. */
    private WebElement findClickableWithScroll(By locator, String label) {
        for (int attempt = 1; attempt <= MAX_SCROLL_ATTEMPTS; attempt++) {
            try {
                WebDriverWait attemptWait = attempt == 1 ? wait : new WebDriverWait(driver, SHORT_WAIT);
                return attemptWait.until(ExpectedConditions.elementToBeClickable(locator));
            } catch (Exception e) {
                if (attempt == MAX_SCROLL_ATTEMPTS) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
                System.out.println("[ForgotPasswordPage] '" + label + "' not visible yet, scrolling down ("
                        + attempt + "/" + MAX_SCROLL_ATTEMPTS + ")");
                scrollDown();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** Taps an element (with scroll-into-view retry). Returns false instead of throwing. */
    private boolean click(By locator, String actionName) {
        try {
            findClickableWithScroll(locator, actionName).click();
            System.out.println("[ForgotPasswordPage] Clicked '" + actionName + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[ForgotPasswordPage] '" + actionName + "' failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Types {@code text} into a field (tap → clear → sendKeys → hide keyboard).
     * A null/empty {@code text} just taps and clears the field (used by
     * {@link #clearConfirmPassword()}).
     */
    private boolean type(By locator, String text, String fieldName) {
        try {
            WebElement element = findClickableWithScroll(locator, fieldName);
            element.click();
            element.clear();
            if (text != null && !text.isEmpty()) {
                element.sendKeys(text);
            }
            hideKeyboardIfShown();
            String shown = (text == null || text.isEmpty()) ? "<empty>"
                    : (fieldName.toLowerCase().contains("password") ? "****" : text);
            System.out.println("[ForgotPasswordPage] Filled '" + fieldName + "' with '" + shown + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[ForgotPasswordPage] Could not enter text into '" + fieldName + "': " + e.getMessage());
            return false;
        }
    }

    /** Visibility check on the caller's timeout — used by the "...Displayed()" markers. */
    private boolean isDisplayed(By locator) {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(locator)).isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /** Presence check with a caller-chosen timeout. */
    private boolean isPresentWithin(By locator, Duration timeout) {
        try {
            new WebDriverWait(driver, timeout).until(ExpectedConditions.presenceOfElementLocated(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** True once the element is gone (or was never there) within the timeout. */
    private boolean isGoneWithin(By locator, Duration timeout) {
        try {
            return new WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (Exception e) {
            // Still visible after the timeout.
            return false;
        }
    }

    // ================= Step: enter-email screen =================

    /** The Forgot Password (enter-email) screen is identified by its Send Verification Code button. */
    public boolean isForgotPasswordScreenDisplayed() {
        return isDisplayed(SEND_VERIFICATION_CODE_BUTTON);
    }

    /**
     * True when we're back on the REAL Sign In screen after "Reset Password" — verified via
     * controls unique to that screen (not a bare "Password *" field, which the New Password
     * screen also has).
     */
    public boolean isBackOnSignInScreen() {
        return isDisplayed(SIGN_IN_SCREEN_STRICT_MARKER);
    }

    /** Enters the recovery email address. */
    public boolean enterEmail(String email) {
        return type(EMAIL_FIELD, email, "Email");
    }

    /** Taps "Send Verification Code" to request an OTP. */
    public boolean clickSendVerificationCode() {
        return click(SEND_VERIFICATION_CODE_BUTTON, "clickSendVerificationCode");
    }

    // ================= Step: OTP verification screen (delegated to OtpVerificationPage) =================

    /** The OTP screen is identified by its "Verify Code" button. */
    public boolean isOtpScreenDisplayed() {
        return otpPage.isOtpScreenDisplayed();
    }

    /** Explicitly waits out the 60s cooldown for the "Resend OTP" option to appear. */
    public boolean waitForResendOtpOption() {
        return otpPage.waitForResendOtpOption();
    }

    /** True if the "Resend OTP" option is currently visible. */
    public boolean isResendOtpDisplayed() {
        return otpPage.isResendOtpDisplayed();
    }

    /** Taps "Resend OTP" (only valid once the cooldown has elapsed). */
    public boolean clickResendOtp() {
        return otpPage.clickResendOtp();
    }

    /** Polls the OTP field(s) until a manually-typed code appears (or {@code maxWait} elapses). */
    public boolean waitForManualOtpEntry(int minDigits, Duration maxWait) {
        return otpPage.waitForManualOtpEntry(minDigits, maxWait);
    }

    /** Taps "Verify Code" to submit the entered OTP. */
    public boolean clickVerifyCode() {
        return otpPage.clickVerifyCode();
    }

    /**
     * True if the "Email Verified successfully" confirmation is shown after Verify Code.
     * Given the message can be a short-lived toast, it is polled for up to the full
     * explicit-wait window.
     */
    public boolean isEmailVerifiedMessageDisplayed() {
        return isDisplayed(EMAIL_VERIFIED_MESSAGE);
    }

    // ================= Step: New Password screen =================

    /** The New Password screen is identified by the New Password field + Reset button. */
    public boolean isNewPasswordScreenDisplayed() {
        return isDisplayed(RESET_PASSWORD_BUTTON);
    }

    public boolean enterNewPassword(String password) {
        return type(NEW_PASSWORD_FIELD, password, "New Password");
    }

    public boolean enterConfirmPassword(String password) {
        return type(CONFIRM_PASSWORD_FIELD, password, "Confirm Password");
    }

    /** Clears the Confirm Password field (used between the wrong and matching entries). */
    public boolean clearConfirmPassword() {
        return type(CONFIRM_PASSWORD_FIELD, "", "Confirm Password");
    }

    /** True while a password-mismatch validation/error message is visible. */
    public boolean isPasswordMismatchErrorDisplayed() {
        return isDisplayed(PASSWORD_MISMATCH_ERROR);
    }

    /** True once the password-mismatch message has disappeared (waits up to {@code timeout}). */
    public boolean isPasswordMismatchErrorCleared(Duration timeout) {
        return isGoneWithin(PASSWORD_MISMATCH_ERROR, timeout);
    }

    /**
     * True if the New Password field is currently masking its content
     * (password="true" in the accessibility tree).
     */
    public boolean isNewPasswordMasked() {
        try {
            WebElement field = wait.until(ExpectedConditions.presenceOfElementLocated(NEW_PASSWORD_FIELD));
            return "true".equals(field.getAttribute("password"));
        } catch (Exception e) {
            System.out.println("[ForgotPasswordPage] Could not read password masking state: " + e.getMessage());
            return true; // fail safe: assume still masked if we can't tell
        }
    }

    /** Taps the eye icon to toggle New Password visibility. */
    public boolean togglePasswordVisibility() {
        return click(NEW_PASSWORD_EYE_ICON, "togglePasswordVisibility");
    }

    /** Taps "Reset Password" to submit the new password. */
    public boolean clickResetPassword() {
        return click(RESET_PASSWORD_BUTTON, "clickResetPassword");
    }

    /**
     * Submits the new password and waits to land back on the Sign In screen. If the first
     * tap doesn't take (the app can no-op briefly while the request is in flight) it logs
     * any on-screen error and retries once.
     *
     * @return true if we reached the Sign In screen.
     */
    public boolean resetPasswordAndReturnToSignIn() {
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (!clickResetPassword()) {
                return false;
            }
            if (isBackOnSignInScreen()) {
                return true;
            }
            String error = getVisibleErrorText();
            System.out.println("[ForgotPasswordPage] Still on the New Password screen after 'Reset Password' "
                    + "(attempt " + attempt + "/2)"
                    + (error != null ? " — on-screen message: \"" + error + "\"" : " — no error message shown"));
        }
        return false;
    }

    /**
     * Best-effort read of any inline validation / error text currently on the New Password
     * screen. For diagnostics only — returns the message or null if none matched.
     */
    public String getVisibleErrorText() {
        By errorText = By.xpath(
                "//android.widget.TextView[starts-with(@resource-id,\"error-\")"
                        + " or contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"invalid\")"
                        + " or contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"already\")"
                        + " or contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"previous\")"
                        + " or contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"must\")"
                        + " or contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"failed\")"
                        + " or contains(translate(@text,\"" + UPPER + "\",\"" + LOWER + "\"),\"try again\")]");
        try {
            return new WebDriverWait(driver, SHORT_WAIT)
                    .until(ExpectedConditions.visibilityOfElementLocated(errorText))
                    .getText();
        } catch (Exception e) {
            return null;
        }
    }
}
