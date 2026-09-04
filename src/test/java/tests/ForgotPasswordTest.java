package tests;

import base.BaseTest;
import java.time.Duration;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.ForgotPasswordPage;
import pages.OnboardingPage;
import pages.SignInPage;

/**
 * End-to-end coverage of the <b>Forgot Password</b> flow only.
 *
 * <p>Nothing in the existing Sign-In / onboarding / Google flows is modified — this test
 * reuses {@link OnboardingPage} to reach the Sign In screen, {@link SignInPage} to open
 * (and back out of) "Forgot Password?" and to perform the final login, and the new
 * {@link ForgotPasswordPage} for the reset-specific screens.
 *
 * <h2>Flow</h2>
 * <pre>
 *   Forgot Password → Back → Forgot Password        (intentional back-and-forward nav)
 *   → enter email → Send Verification Code → OTP screen
 *   → wait out the 60s cooldown → Resend OTP → enter OTP manually → Verify Code
 *   → "Email Verified successfully"
 *   → New Password screen: wrong confirm → mismatch error → matching confirm → error clears
 *   → show/hide password
 *   → Reset Password → Sign In screen
 *   → login with the updated credentials → successful login
 * </pre>
 *
 * <h2>Manual OTP</h2>
 * The OTP is entered by a human directly on the device. The test does not sleep blindly:
 * it waits explicitly for the "Resend OTP" option (the 60s cooldown) and then polls the
 * OTP field until a code is entered (bounded by {@code -DotpEntryWaitSeconds}, default 180).
 */
public class ForgotPasswordTest extends BaseTest {

    // ---- Test data (per the test plan) ----
    /** Recovery account — must be a registered address so an OTP is actually sent. */
    private static final String RECOVERY_EMAIL = "usama@yopmail.com";
    /**
     * New password to set, and then log in with.
     *
     * <p>NOTE: the backend rejects a new password it has seen before for this account
     * (confirmed on-device — "Reset Password" silently no-ops with a previously-used value).
     * Use a value that has not been used for {@link #RECOVERY_EMAIL} yet, and update this
     * when re-running. Override without editing via {@code -DforgotNewPassword=...}.
     */
    private static final String NEW_PASSWORD = System.getProperty("forgotNewPassword", "Onyx@1234");
    /** Deliberately-wrong confirm value used to trigger the mismatch validation. */
    private static final String MISMATCHED_CONFIRM_PASSWORD = "Xani123";
    /** Minimum digits that count as a complete OTP (adjust if the app uses a longer code). */
    private static final int OTP_MIN_DIGITS = 4;

    private void log(String message) {
        System.out.println("[ForgotPasswordTest] " + message);
    }

    /** How long to allow for the human to type the OTP on the device (bounded, configurable). */
    private Duration manualOtpWait() {
        return Duration.ofSeconds(Long.getLong("otpEntryWaitSeconds", 180L));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Current text of the Sign In email field (its placeholder when the field is empty). */
    private String currentSignInEmailText() {
        try {
            return driver.findElement(By.xpath("(//android.widget.EditText)[1]")).getText();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Logs in on the Sign In screen after the password reset.
     *
     * <p>Issue found &amp; handled here: right after "Reset Password" the app animates back
     * to the Sign In screen and the text inputs re-mount as it settles, so values typed too
     * early are silently dropped (observed on-device as both fields ending up blank with
     * "required" errors, and an intermittent StaleObjectException). This waits for the real
     * Sign In screen, lets the animation finish, then types the email and reads it back —
     * refilling until it sticks — before entering the password and submitting. The fix is
     * kept entirely in this test; the shared {@link SignInPage} is not touched.
     */
    private void loginAfterReset(SignInPage signInPage, ForgotPasswordPage forgotPasswordPage,
                                 String email, String password) {
        Assert.assertTrue(forgotPasswordPage.isBackOnSignInScreen(),
                "Did not land on the real Sign In screen after 'Reset Password'");
        sleep(2500); // let the enter-animation finish so the inputs stop re-mounting

        boolean emailStuck = false;
        for (int attempt = 1; attempt <= 3 && !emailStuck; attempt++) {
            signInPage.enterEmail(email);
            emailStuck = currentSignInEmailText().toLowerCase().contains(email.toLowerCase());
            if (!emailStuck) {
                log("Sign In screen swallowed the email (attempt " + attempt + "/3) — settling and retrying");
                sleep(1500);
            }
        }
        Assert.assertTrue(emailStuck, "Email field would not retain its value on the Sign In screen");

        Assert.assertTrue(signInPage.enterPassword(password), "Failed to enter the updated password");
        Assert.assertTrue(signInPage.clickSignInButton(), "Failed to tap the Sign In button");
        Assert.assertTrue(signInPage.isSignInSuccessful(),
                "Login with the updated password did not succeed");
    }

    /**
     * Full Forgot Password → reset → re-login journey. Every screen transition and every
     * validation state is asserted; a failure at any step fails the test with a message
     * that says which step.
     */
    @Test
    public void testForgotPasswordEndToEndFlow() {
        // This whole flow hinges on a verification code only a human can read from the
        // inbox, so it is OFF by default to keep unattended runs green. Enable with
        // -DotpFlow=true (and attend the device to type the OTP).
        if (!Boolean.getBoolean("otpFlow")) {
            throw new SkipException("Forgot Password is a manual-OTP flow — disabled by default; "
                    + "run with -DotpFlow=true and enter the OTP on the device.");
        }

        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignInPage signInPage = new SignInPage(driver);
        ForgotPasswordPage forgotPasswordPage = new ForgotPasswordPage(driver);

        // ---------------------------------------------------------------------------
        // STEP 0: reach the Sign In screen (reuse the existing onboarding + Sign In path).
        // ---------------------------------------------------------------------------
        log("STEP 0: Navigating onboarding → Welcome → Sign In");
        onboardingPage.clickSkipButton();
        onboardingPage.clickSignInButton();
        Assert.assertTrue(signInPage.isSignInScreenDisplayed(),
                "Precondition failed: Sign In screen was not displayed");

        // ---------------------------------------------------------------------------
        // STEP 1: Forgot Password → Back → Forgot Password (intentional back-and-forward).
        // ---------------------------------------------------------------------------
        log("STEP 1a: Tapping 'Forgot Password?'");
        Assert.assertTrue(signInPage.clickForgotPassword(), "Failed to tap 'Forgot Password?'");
        Assert.assertTrue(forgotPasswordPage.isForgotPasswordScreenDisplayed(),
                "Forgot Password screen was not displayed on first open");

        log("STEP 1b: Tapping Back → should return to the Sign In screen");
        Assert.assertTrue(signInPage.clickForgotPasswordBackButton(),
                "Failed to tap the Back button on the Forgot Password screen");
        Assert.assertTrue(signInPage.isSignInScreenDisplayed(),
                "Did not return to the Sign In screen after tapping Back");

        log("STEP 1c: Tapping 'Forgot Password?' again → screen should re-open");
        Assert.assertTrue(signInPage.clickForgotPassword(), "Failed to re-tap 'Forgot Password?'");
        Assert.assertTrue(forgotPasswordPage.isForgotPasswordScreenDisplayed(),
                "Forgot Password screen was not displayed after re-opening");

        // ---------------------------------------------------------------------------
        // STEP 2: enter the recovery email and request the verification code.
        // ---------------------------------------------------------------------------
        log("STEP 2: Entering recovery email and requesting the verification code");
        Assert.assertTrue(forgotPasswordPage.enterEmail(RECOVERY_EMAIL),
                "Failed to enter the recovery email");
        Assert.assertTrue(forgotPasswordPage.clickSendVerificationCode(),
                "Failed to tap 'Send Verification Code'");
        Assert.assertTrue(forgotPasswordPage.isOtpScreenDisplayed(),
                "App did not navigate to the OTP verification screen");

        // ---------------------------------------------------------------------------
        // STEP 3: OTP verification. Honour the 60s resend cooldown, resend, then let the
        //         tester enter the OTP on the device before submitting.
        // ---------------------------------------------------------------------------
        log("STEP 3a: Waiting out the 60s cooldown for the 'Resend OTP' option");
        Assert.assertTrue(forgotPasswordPage.waitForResendOtpOption(),
                "'Resend OTP' did not become available within the cooldown window");
        Assert.assertTrue(forgotPasswordPage.isResendOtpDisplayed(),
                "'Resend OTP' option is not displayed");

        log("STEP 3b: Tapping 'Resend OTP'");
        Assert.assertTrue(forgotPasswordPage.clickResendOtp(), "Failed to tap 'Resend OTP'");

        log("STEP 3c: Waiting for the OTP to be entered manually on the device");
        if (!forgotPasswordPage.waitForManualOtpEntry(OTP_MIN_DIGITS, manualOtpWait())) {
            // No human entered the OTP in time — skip (don't fail) this manual-OTP test.
            throw new SkipException("No OTP entered on the device within " + manualOtpWait().getSeconds()
                    + "s — skipping this manual-OTP Forgot Password test. Re-run attended, or raise -DotpEntryWaitSeconds.");
        }

        log("STEP 3d: Submitting the OTP via 'Verify Code'");
        Assert.assertTrue(forgotPasswordPage.clickVerifyCode(), "Failed to tap 'Verify Code'");
        Assert.assertTrue(forgotPasswordPage.isEmailVerifiedMessageDisplayed(),
                "The 'Email Verified successfully' confirmation was not shown");

        // ---------------------------------------------------------------------------
        // STEP 4: New Password screen — including the confirm-password mismatch validation.
        // ---------------------------------------------------------------------------
        log("STEP 4a: Verifying the New Password screen is displayed");
        Assert.assertTrue(forgotPasswordPage.isNewPasswordScreenDisplayed(),
                "The New Password screen was not displayed after OTP verification");

        log("STEP 4b: Entering the new password");
        Assert.assertTrue(forgotPasswordPage.enterNewPassword(NEW_PASSWORD),
                "Failed to enter the new password");

        log("STEP 4c: Entering a NON-matching confirm password → mismatch error expected");
        Assert.assertTrue(forgotPasswordPage.enterConfirmPassword(MISMATCHED_CONFIRM_PASSWORD),
                "Failed to enter the (wrong) confirm password");
        Assert.assertTrue(forgotPasswordPage.isPasswordMismatchErrorDisplayed(),
                "Expected a password-mismatch validation message, but none was shown");

        log("STEP 4d: Clearing and re-entering a MATCHING confirm password → error should clear");
        Assert.assertTrue(forgotPasswordPage.clearConfirmPassword(),
                "Failed to clear the confirm password field");
        Assert.assertTrue(forgotPasswordPage.enterConfirmPassword(NEW_PASSWORD),
                "Failed to enter the matching confirm password");
        Assert.assertTrue(forgotPasswordPage.isPasswordMismatchErrorCleared(Duration.ofSeconds(5)),
                "The password-mismatch message is still shown after entering matching passwords");

        // ---------------------------------------------------------------------------
        // STEP 5: Password show / hide toggle on the New Password screen.
        // ---------------------------------------------------------------------------
        log("STEP 5a: Verifying the password starts masked");
        Assert.assertTrue(forgotPasswordPage.isNewPasswordMasked(),
                "New Password field was not masked by default");

        log("STEP 5b: Tapping the eye icon → password should be revealed");
        Assert.assertTrue(forgotPasswordPage.togglePasswordVisibility(),
                "Failed to tap the eye icon (show)");
        Assert.assertFalse(forgotPasswordPage.isNewPasswordMasked(),
                "New Password still masked after tapping the eye icon");

        log("STEP 5c: Tapping the eye icon again → password should be masked again");
        Assert.assertTrue(forgotPasswordPage.togglePasswordVisibility(),
                "Failed to tap the eye icon (hide)");
        Assert.assertTrue(forgotPasswordPage.isNewPasswordMasked(),
                "New Password was not re-masked after tapping the eye icon again");

        // ---------------------------------------------------------------------------
        // STEP 6: Reset the password and confirm we land back on the Sign In screen.
        // ---------------------------------------------------------------------------
        log("STEP 6: Tapping 'Reset Password' and returning to the Sign In screen");
        // resetPasswordAndReturnToSignIn() taps, waits, logs any on-screen error and retries
        // once. Verified against controls UNIQUE to the Sign In screen — the New Password
        // screen also has a "Password *" field, so SignInPage.isSignInScreenDisplayed()
        // alone would false-positive if the reset had silently stayed put.
        Assert.assertTrue(forgotPasswordPage.resetPasswordAndReturnToSignIn(),
                "Password was not reset / app did not return to the Sign In screen "
                        + "(a previously-used NEW_PASSWORD is silently rejected — try a fresh one)");

        // ---------------------------------------------------------------------------
        // STEP 7: Log in with the updated credentials.
        // ---------------------------------------------------------------------------
        log("STEP 7: Logging in with the updated password (" + RECOVERY_EMAIL + ")");
        loginAfterReset(signInPage, forgotPasswordPage, RECOVERY_EMAIL, NEW_PASSWORD);

        log("TEST PASSED: Forgot Password flow completed and login with the new password succeeded");
    }
}
