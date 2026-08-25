package tests;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Test;
import pages.OnboardingPage;
import pages.SignInPage;
import pages.SignupPage;

public class OnboardingTest extends BaseTest {

    private static final long STEP_DELAY_MS = 2000;

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

        logStep("STEP 4: Filling basic profile fields (full name, screen name, email, DOB, gender, bio)");
        Assert.assertTrue(signupPage.enterFullName("Uzair Iqbal"), "Failed to enter full name");
        Assert.assertTrue(signupPage.enterScreenName("uzair2"), "Failed to enter screen name");
        Assert.assertTrue(signupPage.enterEmail("uzair2@yopmail.com"), "Failed to enter email");
        Assert.assertTrue(signupPage.selectDateOfBirth("1995"), "Failed to select date of birth");
        Assert.assertTrue(signupPage.selectGender(), "Failed to select gender");
        signupPage.enterBio("QA Automation Engineer"); // optional field
        pauseBetweenSteps();

        logStep("STEP 5: Filling location");
        Assert.assertTrue(signupPage.enterLocation("San Francisco"), "Failed to enter location");
        Assert.assertTrue(signupPage.selectLocationFromDropdown(), "Failed to select location from dropdown");
        pauseBetweenSteps();

        logStep("STEP 6: Filling phone number and password fields");
        Assert.assertTrue(signupPage.enterPhoneNumber("4084427455"), "Failed to enter phone number");
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

        logStep("STEP 9: Verifying signup success");
        Assert.assertTrue(signupPage.isSignupSuccessful(), "Signup was not successful");
        logStep("TEST PASSED: Signup with email and password completed successfully");
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

        logStep("STEP 5: Verifying signup success");
        Assert.assertTrue(signupPage.isSignupSuccessful(), "Signup with Google was not successful");
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

        logStep("STEP 4: Filling every other mandatory field, deliberately leaving DOB empty");
        Assert.assertTrue(signupPage.enterFullName("Muhammad Zain"), "Failed to enter full name");
        Assert.assertTrue(signupPage.enterScreenName("xani2dob"), "Failed to enter screen name");
        Assert.assertTrue(signupPage.enterEmail("zain1dob@yopmail.com"), "Failed to enter email");
        Assert.assertTrue(signupPage.selectGender(), "Failed to select gender");
        Assert.assertTrue(signupPage.enterLocation("San Francisco"), "Failed to enter location");
        Assert.assertTrue(signupPage.selectLocationFromDropdown(), "Failed to select location from dropdown");
        Assert.assertTrue(signupPage.enterPhoneNumber("5169298899"), "Failed to enter phone number");
        Assert.assertTrue(signupPage.enterPassword("Zain@123"), "Failed to enter password");
        Assert.assertTrue(signupPage.enterConfirmPassword("Zain@123"), "Failed to enter confirm password");
        Assert.assertTrue(signupPage.agreeTermsCheckbox(), "Failed to check the Terms & Conditions checkbox");
        pauseBetweenSteps();

        logStep("STEP 5: Submitting with DOB empty and verifying the form does NOT proceed");
        Assert.assertTrue(signupPage.isStillOnSignupScreenAfterEmptyDobSubmit(),
                "Signup was allowed to proceed without a DOB — mandatory validation is not being enforced");

        logStep("STEP 6: Now selecting a DOB (same session, same data otherwise) and resubmitting, "
                + "to prove DOB specifically was the blocker rather than some unrelated issue");
        Assert.assertTrue(signupPage.selectDateOfBirth("1995"), "Failed to select date of birth");
        pauseBetweenSteps();
        Assert.assertTrue(signupPage.clickCreateAccountButton(), "Failed to click Create Account button after filling DOB");
        pauseBetweenSteps();
        Assert.assertFalse(signupPage.isSignupScreenDisplayed(),
                "Form still did not proceed even after DOB was filled — DOB may not actually be the blocker");
        logStep("TEST PASSED: DOB is correctly enforced as a mandatory field (confirmed via empty-vs-filled comparison)");
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
