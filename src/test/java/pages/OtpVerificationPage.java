package pages;

import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Shared page object for the app's OTP / email-verification screen.
 *
 * <p>The exact same screen (locators and behaviour) is reached from two flows:
 * <ul>
 *   <li><b>Signup</b> — after tapping "Create Account" the app verifies the email before
 *       finishing signup ({@link SignupPage} delegates here).</li>
 *   <li><b>Forgot Password</b> — after "Send Verification Code" ({@link ForgotPasswordPage}
 *       delegates here).</li>
 * </ul>
 * Keeping it in one place avoids the two flows drifting apart.
 *
 * <p>The OTP itself is always entered by a human on the device — {@link #waitForManualOtpEntry}
 * polls the field(s) for a typed code rather than sleeping blindly.
 */
public class OtpVerificationPage {

    private static final Duration EXPLICIT_WAIT = Duration.ofSeconds(15);
    private static final Duration ACTION_WAIT = Duration.ofSeconds(2);
    private static final Duration SHORT_WAIT = Duration.ofSeconds(3);
    // "Resend OTP" only appears after a fixed ~60s server-side cooldown; wait for it
    // explicitly with a small buffer instead of a hard-coded sleep.
    private static final Duration RESEND_OTP_WAIT = Duration.ofSeconds(90);
    private static final int MAX_SCROLL_ATTEMPTS = 3;

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // ---------------- Locators (supplied by the app team) ----------------

    /** Primary submit button on the OTP screen — also serves as the screen marker. */
    private static final By VERIFY_CODE_BUTTON =
            By.xpath("//android.view.ViewGroup[@content-desc=\"Verify Code\"]");
    /** Appears only after the 60s cooldown. */
    private static final By RESEND_OTP_LINK =
            By.xpath("//android.widget.TextView[@text=\"Resend OTP\"]");
    /** OTP input(s). Some builds split the code across several boxes — match them all. */
    private static final By OTP_INPUT_FIELDS = By.xpath("//android.widget.EditText");

    public OtpVerificationPage(AndroidDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, EXPLICIT_WAIT);
    }

    // ---------------- Internal helpers (same style as the other page objects) ----------------

    private void pauseForAction() {
        sleepQuietly(ACTION_WAIT.toMillis());
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Hides the on-screen keyboard so it can't cover the "Verify Code" button below. */
    private void hideKeyboardIfShown() {
        try {
            driver.hideKeyboard();
        } catch (Exception ignored) {
            // No keyboard showing — fine.
        }
    }

    private WebElement findClickableWithScroll(By locator, String label) {
        for (int attempt = 1; attempt <= MAX_SCROLL_ATTEMPTS; attempt++) {
            try {
                WebDriverWait attemptWait = attempt == 1 ? wait : new WebDriverWait(driver, SHORT_WAIT);
                return attemptWait.until(ExpectedConditions.elementToBeClickable(locator));
            } catch (Exception e) {
                if (attempt == MAX_SCROLL_ATTEMPTS) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
                System.out.println("[OtpVerificationPage] '" + label + "' not ready, retrying (" + attempt + ")");
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private boolean click(By locator, String actionName) {
        try {
            findClickableWithScroll(locator, actionName).click();
            System.out.println("[OtpVerificationPage] Clicked '" + actionName + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[OtpVerificationPage] '" + actionName + "' failed: " + e.getMessage());
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

    private boolean isPresentWithin(By locator, Duration timeout) {
        try {
            new WebDriverWait(driver, timeout).until(ExpectedConditions.presenceOfElementLocated(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- Screen marker ----------------

    /** True when the OTP verification screen is showing (its "Verify Code" button is present). */
    public boolean isOtpScreenDisplayed() {
        return isDisplayed(VERIFY_CODE_BUTTON);
    }

    // ---------------- Resend OTP ----------------

    /**
     * Explicitly waits out the ~60s cooldown by waiting for "Resend OTP" to appear
     * (up to {@link #RESEND_OTP_WAIT}). Preferred over a blind 60s sleep.
     */
    public boolean waitForResendOtpOption() {
        System.out.println("[OtpVerificationPage] Waiting up to " + RESEND_OTP_WAIT.getSeconds()
                + "s for the 'Resend OTP' option (60s server cooldown)...");
        return isPresentWithin(RESEND_OTP_LINK, RESEND_OTP_WAIT);
    }

    /** True if "Resend OTP" is currently visible. */
    public boolean isResendOtpDisplayed() {
        return isDisplayed(RESEND_OTP_LINK);
    }

    /** Taps "Resend OTP" (only valid once the cooldown has elapsed). */
    public boolean clickResendOtp() {
        return click(RESEND_OTP_LINK, "clickResendOtp");
    }

    // ---------------- Manual OTP entry ----------------

    /**
     * Gives a human time to read the email and type the OTP directly on the device. Polls
     * the OTP field(s) and returns as soon as a code of at least {@code minDigits} digits
     * is present, or when {@code maxWait} elapses (whichever comes first).
     *
     * @return true if an OTP of the expected length was detected before the timeout.
     */
    public boolean waitForManualOtpEntry(int minDigits, Duration maxWait) {
        System.out.println("======================================================================");
        System.out.println("  ACTION REQUIRED: enter the verification code (OTP) ON THE DEVICE.");
        System.out.println("  Waiting up to " + maxWait.getSeconds() + "s for a " + minDigits
                + "+ digit code to be entered...");
        System.out.println("======================================================================");
        long deadline = System.currentTimeMillis() + maxWait.toMillis();
        int stableCount = -1;
        int stableStreak = 0;
        while (System.currentTimeMillis() < deadline) {
            int digits = currentOtpDigitCount();
            // Wait for the count to STOP CHANGING (two consecutive equal polls) once it's at
            // or past the minimum — so a 6-digit code isn't acted on when only 4 are typed,
            // which left "Verify Code" disabled/covered and the tap failing.
            if (digits >= minDigits && digits == stableCount) {
                stableStreak++;
                if (stableStreak >= 2) {
                    System.out.println("[OtpVerificationPage] OTP entry settled at " + digits + " digits — continuing.");
                    sleepQuietly(500);
                    return true;
                }
            } else {
                stableStreak = 0;
            }
            stableCount = digits;
            sleepQuietly(1500);
        }
        System.out.println("[OtpVerificationPage] Timed out waiting for a manual OTP.");
        return false;
    }

    /** Total digits currently held across all OTP EditTexts on screen (best-effort). */
    private int currentOtpDigitCount() {
        int digits = 0;
        try {
            for (WebElement field : driver.findElements(OTP_INPUT_FIELDS)) {
                String value = field.getText();
                if (value == null) {
                    continue;
                }
                for (int i = 0; i < value.length(); i++) {
                    if (Character.isDigit(value.charAt(i))) {
                        digits++;
                    }
                }
            }
        } catch (Exception ignored) {
            // Screen changed / stale — treat as "nothing entered yet".
        }
        return digits;
    }

    // ---------------- Verify Code ----------------

    /** Taps "Verify Code" to submit the entered OTP (hides the keyboard first — it can cover the button). */
    public boolean clickVerifyCode() {
        hideKeyboardIfShown();
        sleepQuietly(500);
        return click(VERIFY_CODE_BUTTON, "clickVerifyCode");
    }
}
