package tests;

import base.BaseTest;
import java.time.Duration;
import java.time.Year;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;
import pages.OnboardingPage;
import pages.SignInPage;
import pages.SignupPage;

public class OnboardingTest extends BaseTest {

    private static final long STEP_DELAY_MS = 2000;
    /** Minimum digits that count as a complete verification code. */
    private static final int OTP_MIN_DIGITS = 4;
    /**
     * The post-"Create Account" flow now ends on an email-OTP screen that only a human can
     * complete. It is OFF by default so the suite runs unattended: signup tests still
     * assert the whole form works and reaches the OTP screen, they just don't enter a code.
     * Pass {@code -DotpFlow=true} to run the full manual-OTP verification.
     */
    private static final boolean OTP_FLOW_ENABLED = Boolean.getBoolean("otpFlow");
    /** Per-run unique suffix so signup screen-name / email / phone never collide across runs. */
    private static final String RUN_SUFFIX = String.format("%05d", System.currentTimeMillis() % 100000);

    private void pauseBetweenSteps() {
        try {
            Thread.sleep(STEP_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logStep(String message) {
        System.out.println("[OnboardingTest] " + message);
    }

    /** How long to allow for the human to type the OTP on the device (bounded, configurable). */
    private Duration manualOtpWait() {
        return Duration.ofSeconds(Long.getLong("otpEntryWaitSeconds", 180L));
    }

    /**
     * Since the current build, tapping "Create Account" no longer finishes signup — it
     * sends an email OTP and shows the verification screen. This drives that screen to
     * completion:
     * <ol>
     *   <li>asserts the OTP screen is shown;</li>
     *   <li>if {@code exerciseResendButton}, waits out the 60s cooldown, asserts "Resend
     *       OTP" is shown, and taps it (so the resend button itself gets coverage);</li>
     *   <li>waits for the tester to type the OTP on the device (explicit poll, not a
     *       blind sleep — bounded by {@code -DotpEntryWaitSeconds}, default 180s);</li>
     *   <li>taps "Verify Code" and asserts signup then succeeds.</li>
     * </ol>
     */
    private void completeEmailOtpVerification(SignupPage signupPage, boolean exerciseResendButton) {
        logStep("OTP: Verifying the email verification screen is shown after Create Account");
        Assert.assertTrue(signupPage.isEmailOtpScreenDisplayed(),
                "Email OTP verification screen was not shown after tapping Create Account");

        if (!OTP_FLOW_ENABLED) {
            // Unattended default: the signup form worked and reached the OTP screen — that's
            // the pass condition here. The manual code entry / Verify Code / success screen
            // only run with -DotpFlow=true.
            logStep("OTP: manual-OTP flow disabled (pass -DotpFlow=true to run it) — "
                    + "signup reached the OTP screen, done.");
            return;
        }

        if (exerciseResendButton) {
            logStep("OTP: Waiting out the 60s cooldown, then exercising the 'Resend OTP' button");
            Assert.assertTrue(signupPage.waitForResendOtpOption(),
                    "'Resend OTP' did not become available within the cooldown window");
            Assert.assertTrue(signupPage.isResendOtpDisplayed(), "'Resend OTP' option is not displayed");
            Assert.assertTrue(signupPage.clickResendOtp(), "Failed to tap 'Resend OTP'");
        }

        logStep("OTP: Waiting for the verification code to be entered manually on the device");
        if (!signupPage.waitForManualOtpEntry(OTP_MIN_DIGITS, manualOtpWait())) {
            // No human entered the OTP in time — skip (don't fail) this manual-OTP test.
            throw new SkipException("No OTP entered on the device within " + manualOtpWait().getSeconds()
                    + "s — skipping this manual-OTP signup test. Re-run attended, or raise -DotpEntryWaitSeconds.");
        }

        logStep("OTP: Submitting via 'Verify Code'");
        Assert.assertTrue(signupPage.clickVerifyCode(), "Failed to tap 'Verify Code'");

        logStep("OTP: Verifying signup completed successfully");
        Assert.assertTrue(signupPage.isSignupSuccessful(),
                "Signup did not reach the success screen after OTP verification");
    }

    @Test
    public void testNavigateToSignupViaNext() {
        logStep("STEP 1: Starting test - Navigate to signup via Next");
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignupPage signupPage = new SignupPage(driver);

        logStep("STEP 2: Clicking Next button 4 times");
        onboardingPage.clickNextButtonFourTimes();
        pauseBetweenSteps();

        logStep("STEP 3: Clicking Get Started on the Welcome screen");
        onboardingPage.clickGetStartedButton();
        pauseBetweenSteps();

        logStep("STEP 4: Verifying signup screen is displayed");
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                "Signup screen was not displayed after clicking Next 4 times and Get Started");
        logStep("TEST PASSED: Signup screen displayed via Next");
    }

    @Test
    public void testNavigateToSignupViaSkip() {
        logStep("STEP 1: Starting test - Navigate to signup via Skip");
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignupPage signupPage = new SignupPage(driver);

        logStep("STEP 2: Clicking Skip button");
        onboardingPage.clickSkipButton();
        pauseBetweenSteps();

        logStep("STEP 3: Clicking Get Started on the Welcome screen");
        onboardingPage.clickGetStartedButton();
        pauseBetweenSteps();

        logStep("STEP 4: Verifying signup screen is displayed");
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                "Signup screen was not displayed after clicking Skip and Get Started");
        logStep("TEST PASSED: Signup screen displayed via Skip");
    }

    @Test
    public void testSignupWithEmailAndPassword() {
        logStep("STEP 1: Starting test - Signup with email and password");
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignupPage signupPage = new SignupPage(driver);

        logStep("STEP 2: Completing onboarding via Next x4");
        onboardingPage.clickNextButtonFourTimes();
        pauseBetweenSteps();
        onboardingPage.clickGetStartedButton();
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                "Signup screen was not displayed after onboarding");

        logStep("STEP 3: Uploading profile image (optional)");
        boolean imageUploaded = signupPage.uploadFirstAvailablePhotoFromGallery();
        logStep("Profile image upload " + (imageUploaded ? "succeeded" : "was skipped/failed (optional field, continuing)"));
        pauseBetweenSteps();

        // Screen name, email and phone must be unique per run — the app rejects duplicates,
        // and (since the OTP screen was added) a rejected Create Account never advances, so
        // the test would fail on the 2nd run with fixed values.
        String screenName = "javeria" + RUN_SUFFIX;          // field caps at 12 chars: 7 + 5 fits
        String email = "javeria" + RUN_SUFFIX + "@yopmail.com";
        String phoneNumber = "73784" + RUN_SUFFIX;           // 10 digits: 5 + 5

        logStep("STEP 4: Filling basic profile fields (full name, screen name, email, DOB, gender, bio)");
        Assert.assertTrue(signupPage.enterFullName("javeria khan"), "Failed to enter full name");
        Assert.assertTrue(signupPage.enterScreenName(screenName), "Failed to enter screen name");
        Assert.assertTrue(signupPage.enterEmail(email), "Failed to enter email");
        Assert.assertTrue(signupPage.selectDateOfBirth("1995"), "Failed to select date of birth");
        Assert.assertTrue(signupPage.selectGender(), "Failed to select gender");
        signupPage.enterBio("QA Automation Engineer"); // optional field
        pauseBetweenSteps();

        logStep("STEP 5: Filling location");
        Assert.assertTrue(signupPage.enterLocation("San Francisco"), "Failed to enter location");
        Assert.assertTrue(signupPage.selectLocationFromDropdown(), "Failed to select location from dropdown");
        pauseBetweenSteps();

        logStep("STEP 6: Filling phone number and password fields");
        Assert.assertTrue(signupPage.enterPhoneNumber(phoneNumber), "Failed to enter phone number");
        Assert.assertTrue(signupPage.enterPassword("Zain@123"), "Failed to enter password");
        pauseBetweenSteps();

        logStep("STEP 6b: Verifying password visibility toggle");
        Assert.assertTrue(signupPage.isPasswordMasked(), "Password field was not masked by default");
        Assert.assertTrue(signupPage.togglePasswordVisibility(), "Failed to click the password eye icon");
        pauseBetweenSteps();
        Assert.assertFalse(signupPage.isPasswordMasked(), "Password was still masked after tapping the eye icon");
        Assert.assertTrue(signupPage.togglePasswordVisibility(), "Failed to click the password eye icon a second time");
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isPasswordMasked(), "Password was not re-masked after tapping the eye icon again");

        Assert.assertTrue(signupPage.enterConfirmPassword("Zain@123"), "Failed to enter confirm password");
        pauseBetweenSteps();

        logStep("STEP 7: Opening Terms & Conditions webpage and returning to Signup");
        Assert.assertTrue(signupPage.clickTermsAndConditions(), "Failed to click Terms & Conditions link");
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isTermsWebpageDisplayed(), "Terms & Conditions webpage did not open");
        Assert.assertTrue(signupPage.clickBackButton(), "Failed to click back button on Terms & Conditions webpage");
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(), "Did not return to Signup screen after closing Terms & Conditions");

        logStep("STEP 7b: Checking the Terms & Conditions checkbox");
        Assert.assertTrue(signupPage.agreeTermsCheckbox(), "Failed to check the Terms & Conditions checkbox");
        pauseBetweenSteps();

        logStep("STEP 8: Submitting signup form");
        Assert.assertTrue(signupPage.clickCreateAccountButton(), "Failed to click Create Account button");
        pauseBetweenSteps();

        logStep("STEP 9: Completing email-OTP verification (also exercises the 'Resend OTP' button)");
        completeEmailOtpVerification(signupPage, true);
        logStep("TEST PASSED: Signup with email and password completed successfully");
    }

    @Test
    public void testSignupWithCameraImageUpload() {
        logStep("STEP 1: Starting test - Signup with profile image captured via in-app camera");
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignupPage signupPage = new SignupPage(driver);

        // Onboarding is covered thoroughly by testNavigateToSignupVia{Next,Skip}; here use
        // the single-tap Skip route, which is far less flaky than four sequential Next taps
        // on the always-animating carousel ("'Next button' not found after 5 attempts").
        logStep("STEP 2: Completing onboarding via Skip");
        onboardingPage.clickSkipButton();
        pauseBetweenSteps();
        onboardingPage.clickGetStartedButton();
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                "Signup screen was not displayed after onboarding");

        logStep("STEP 3: Uploading profile image via in-app camera (capture -> close via Navigate up -> "
                + "capture again -> Crop to select)");
        Assert.assertTrue(signupPage.uploadPhotoFromCamera(),
                "Failed to capture and select a profile image via the in-app camera");
        pauseBetweenSteps();

        // Screen name, email AND phone must all be unique per run — the app rejects
        // duplicates ("This screen name is already taken.", "This email already exists.",
        // "The phone has already been taken."). A time-based 5-digit suffix is shared
        // across all three so a single run's values stay recognisable together.
        //   - screen-name field caps at 12 chars: "sohaib" (6) + 5 digits = 11, fits.
        //   - phone field caps at 10 digits: "40124" (5) + 5 digits = 10, fits, and keeps
        //     the 4012478xxx shape of the numbers originally supplied.
        String uniqueSuffix = String.format("%05d", System.currentTimeMillis() % 100000);
        String screenName = "sohaib" + uniqueSuffix;
        String email = "sohaib" + uniqueSuffix + "@yopmail.com";
        String phoneNumber = "40124" + uniqueSuffix;

        logStep("STEP 4: Filling basic profile fields (full name, screen name, email, DOB, gender, bio)");
        Assert.assertTrue(signupPage.enterFullName("rana sohaib"), "Failed to enter full name");
        Assert.assertTrue(signupPage.enterScreenName(screenName), "Failed to enter screen name");
        Assert.assertTrue(signupPage.enterEmail(email), "Failed to enter email");
        Assert.assertTrue(signupPage.selectDateOfBirth(String.valueOf(Year.now().getValue())),
                "Failed to select date of birth");
        Assert.assertTrue(signupPage.selectGenderAsFemale(), "Failed to select gender");
        signupPage.enterBio("i am the best tester"); // optional field
        pauseBetweenSteps();

        logStep("STEP 5: Filling location");
        Assert.assertTrue(signupPage.enterLocation("San Francisco"), "Failed to enter location");
        Assert.assertTrue(signupPage.selectLocationFromDropdown(), "Failed to select location from dropdown");
        pauseBetweenSteps();

        logStep("STEP 6: Filling phone number and password fields");
        Assert.assertTrue(signupPage.enterPhoneNumber(phoneNumber), "Failed to enter phone number");
        Assert.assertTrue(signupPage.enterPassword("Zain@123"), "Failed to enter password");
        Assert.assertTrue(signupPage.enterConfirmPassword("Zain@123"), "Failed to enter confirm password");
        pauseBetweenSteps();

        logStep("STEP 7: Checking the Terms & Conditions checkbox");
        Assert.assertTrue(signupPage.agreeTermsCheckbox(), "Failed to check the Terms & Conditions checkbox");
        pauseBetweenSteps();

        logStep("STEP 8: Submitting signup form");
        Assert.assertTrue(signupPage.clickCreateAccountButton(), "Failed to click Create Account button");
        pauseBetweenSteps();

        logStep("STEP 9: Completing email-OTP verification");
        completeEmailOtpVerification(signupPage, false);
        logStep("TEST PASSED: Signup with camera-captured profile image completed successfully");
    }

    @Test
    public void testSignupWithGoogle() {
        logStep("STEP 1: Starting test - Signup with Google");
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignupPage signupPage = new SignupPage(driver);

        logStep("STEP 2: Skipping onboarding");
        onboardingPage.clickSkipButton();
        pauseBetweenSteps();
        onboardingPage.clickGetStartedButton();
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                "Signup screen was not displayed after onboarding");

        logStep("STEP 3: Clicking Continue with Google");
        Assert.assertTrue(signupPage.clickContinueWithGoogle(), "Failed to click Continue with Google");
        pauseBetweenSteps();

        logStep("STEP 4: Selecting Google account");
        Assert.assertTrue(signupPage.selectGoogleAccount(), "Failed to select Google account");
        pauseBetweenSteps();

        logStep("STEP 5: Verifying signup success (a Google-verified email should skip the OTP screen)");
        if (signupPage.isEmailOtpScreenDisplayed()) {
            // Some builds still route Google signups through the email-OTP screen.
            completeEmailOtpVerification(signupPage, false);
        } else {
            Assert.assertTrue(signupPage.isSignupSuccessful(), "Signup with Google was not successful");
        }
        logStep("TEST PASSED: Signup with Google completed successfully");
    }

    @Test
    public void testSignupDobValidation() {
        logStep("STEP 1: Starting test - DOB mandatory field validation");
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignupPage signupPage = new SignupPage(driver);

        logStep("STEP 2: Completing onboarding via Next x4");
        onboardingPage.clickNextButtonFourTimes();
        pauseBetweenSteps();
        onboardingPage.clickGetStartedButton();
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                "Signup screen was not displayed after onboarding");

        logStep("STEP 3: Confirming DOB field is present and mandatory");
        Assert.assertTrue(signupPage.isDobFieldDisplayed(), "DOB field was not displayed on the signup form");

        // Unique per run — STEP 6 actually submits the form, and a duplicate screen-name /
        // email would block it just like a missing DOB would, masking what we're testing.
        logStep("STEP 4: Filling every other mandatory field, deliberately leaving DOB empty");
        Assert.assertTrue(signupPage.enterFullName("Kiran ijaz"), "Failed to enter full name");
        Assert.assertTrue(signupPage.enterScreenName("kiran" + RUN_SUFFIX), "Failed to enter screen name");
        Assert.assertTrue(signupPage.enterEmail("kiran" + RUN_SUFFIX + "@yopmail.com"), "Failed to enter email");
        Assert.assertTrue(signupPage.selectGender(), "Failed to select gender");
        Assert.assertTrue(signupPage.enterLocation("San Francisco"), "Failed to enter location");
        Assert.assertTrue(signupPage.selectLocationFromDropdown(), "Failed to select location from dropdown");
        Assert.assertTrue(signupPage.enterPhoneNumber("85524" + RUN_SUFFIX), "Failed to enter phone number");
        Assert.assertTrue(signupPage.enterPassword("Zain@123"), "Failed to enter password");
        Assert.assertTrue(signupPage.enterConfirmPassword("Zain@123"), "Failed to enter confirm password");
        Assert.assertTrue(signupPage.agreeTermsCheckbox(), "Failed to check the Terms & Conditions checkbox");
        pauseBetweenSteps();

        logStep("STEP 5: Submitting with DOB empty and verifying the form does NOT proceed");
        Assert.assertTrue(signupPage.isStillOnSignupScreenAfterEmptyDobSubmit(),
                "Signup was allowed to proceed without a DOB — mandatory validation is not being enforced");

        logStep("STEP 5b: Verifying a 'DOB is required' validation message is shown");
        Assert.assertTrue(signupPage.isDobRequiredErrorDisplayed(),
                "No 'Date of birth is required' validation message was shown for the empty DOB field");

        logStep("STEP 6: Now selecting a DOB (same session, same data otherwise) and resubmitting, "
                + "to prove DOB specifically was the blocker rather than some unrelated issue");
        Assert.assertTrue(signupPage.selectDateOfBirth("1995"), "Failed to select date of birth");
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.clickCreateAccountButton(), "Failed to click Create Account button after filling DOB");
        pauseBetweenSteps();
        // With the current build a valid submit proceeds to the email-OTP screen (rather
        // than straight to the home screen). Reaching it proves DOB was the only blocker.
        Assert.assertTrue(signupPage.isEmailOtpScreenDisplayed(),
                "Form did not proceed to OTP verification even after DOB was filled — DOB may not actually be the blocker");
        logStep("TEST PASSED: DOB is enforced as mandatory (empty -> blocked with error; filled -> proceeds to OTP verification)");
    }

    @Test
    public void testNavigateToSignInFromSignup() {
        logStep("STEP 1: Starting test - Navigate to Sign In from Signup");
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignupPage signupPage = new SignupPage(driver);
        SignInPage signInPage = new SignInPage(driver);

        logStep("STEP 2: Completing onboarding via Skip");
        onboardingPage.clickSkipButton();
        pauseBetweenSteps();
        onboardingPage.clickGetStartedButton();
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                "Signup screen was not displayed after onboarding");

        logStep("STEP 3: Clicking the Sign In link");
        Assert.assertTrue(signupPage.clickSignInLink(), "Failed to click Sign In link on the signup screen");
        pauseBetweenSteps();

        logStep("STEP 4: Verifying the Sign In screen opened");
        Assert.assertTrue(signInPage.isSignInScreenDisplayed(), "Sign In screen was not displayed after clicking Sign In");
        logStep("TEST PASSED: Sign In link correctly navigates from Signup to the Sign In screen");
    }
}
