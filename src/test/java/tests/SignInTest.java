package tests;

import base.BaseTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pages.OnboardingPage;
import pages.SignInPage;
import pages.SignupPage;

public class SignInTest extends BaseTest {

    private static final long STEP_DELAY_MS = 2000;
    private static final String VALID_EMAIL = "irfanrehan635+1@gmail.com";
    private static final String VALID_PASSWORD = "Test@905";

    private void pauseBetweenSteps() {
        try {
            Thread.sleep(STEP_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logStep(String message) {
        System.out.println("[SignInTest] " + message);
    }

    /**
     * The app has two separate buttons that both lead to the exact same Sign In screen:
     * the Welcome screen's own "Sign In" button, and the Signup screen's "Sign In" link.
     * Every session starts fresh on the onboarding carousel (BaseTest clears app data
     * before each test), so this one helper covers both routes rather than duplicating
     * the Sign In screen tests per entry point — the screen being tested is identical
     * either way, only how you arrive there differs.
     */
    private SignInPage navigateToSignInScreen() {
        return navigateToSignInScreen(false);
    }

    private SignInPage navigateToSignInScreen(boolean viaWelcomeScreen) {
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignInPage signInPage = new SignInPage(driver);

        if (viaWelcomeScreen) {
            logStep("Navigating: onboarding -> Sign In (direct from the Welcome screen)");
            onboardingPage.clickNextButtonFourTimes();
            pauseBetweenSteps();
            onboardingPage.clickSignInButton();
            pauseBetweenSteps();
        } else {
            SignupPage signupPage = new SignupPage(driver);
            logStep("Navigating: onboarding -> Get Started -> Signup -> Sign In link");
            onboardingPage.clickSkipButton();
            pauseBetweenSteps();
            onboardingPage.clickGetStartedButton();
            pauseBetweenSteps();
            Assert.assertTrue(signupPage.isSignupScreenDisplayed(), "Signup screen was not displayed after onboarding");
            Assert.assertTrue(signupPage.clickSignInLink(), "Failed to click Sign In link on the signup screen");
            pauseBetweenSteps();
        }

        Assert.assertTrue(signInPage.isSignInScreenDisplayed(), "Sign In screen was not displayed");
        return signInPage;
    }

    /**
     * The Sign In screen is reachable from two entry points that lead to the exact same
     * screen: the Welcome screen's own "Sign In" button, and the Signup screen's "Sign In"
     * link. Rather than duplicating the credentials test per entry point, this data
     * provider drives one shared @Test method across both — TestNG runs it twice (once
     * per row) and reports each as a distinct invocation of the same test case.
     */
    @DataProvider(name = "signInEntryPoints")
    public Object[][] signInEntryPoints() {
        return new Object[][]{
                {false, "via Signup screen's Sign In link"},
                {true, "via Welcome screen's Sign In button"}
        };
    }

    @Test(dataProvider = "signInEntryPoints")
    public void testSignInWithValidCredentials(boolean viaWelcomeScreen, String entryPointDescription) {
        logStep("STEP 1: Starting test - Sign in with valid credentials (" + entryPointDescription + ")");
        SignInPage signInPage = navigateToSignInScreen(viaWelcomeScreen);

        logStep("STEP 2: Entering email and password");
        Assert.assertTrue(signInPage.enterEmail(VALID_EMAIL), "Failed to enter email");
        Assert.assertTrue(signInPage.enterPassword(VALID_PASSWORD), "Failed to enter password");
        pauseBetweenSteps();

        logStep("STEP 3: Clicking Sign In");
        Assert.assertTrue(signInPage.clickSignInButton(), "Failed to click Sign In button");
        pauseBetweenSteps();

        logStep("STEP 4: Verifying successful login/navigation");
        Assert.assertTrue(signInPage.isSignInSuccessful(), "Sign in did not succeed");
        logStep("TEST PASSED: Signed in successfully");
    }

    @Test
    public void testPasswordShowHideToggle() {
        logStep("STEP 1: Starting test - Password show/hide toggle");
        SignInPage signInPage = navigateToSignInScreen();

        logStep("STEP 2: Entering password");
        Assert.assertTrue(signInPage.enterPassword(VALID_PASSWORD), "Failed to enter password");
        pauseBetweenSteps();

        logStep("STEP 3: Verifying password starts masked");
        Assert.assertTrue(signInPage.isPasswordMasked(), "Password field was not masked by default");

        logStep("STEP 4: Tapping the eye icon to reveal the password");
        Assert.assertTrue(signInPage.togglePasswordVisibility(), "Failed to click the eye icon");
        pauseBetweenSteps();
        Assert.assertFalse(signInPage.isPasswordMasked(), "Password was still masked after tapping the eye icon");

        logStep("STEP 5: Tapping the eye icon again to re-mask the password");
        Assert.assertTrue(signInPage.togglePasswordVisibility(), "Failed to click the eye icon a second time");
        pauseBetweenSteps();
        Assert.assertTrue(signInPage.isPasswordMasked(), "Password was not re-masked after tapping the eye icon again");

        logStep("TEST PASSED: Password show/hide toggle works correctly");
    }

    @Test
    public void testForgotPasswordNavigation() {
        logStep("STEP 1: Starting test - Forgot Password navigation");
        SignInPage signInPage = navigateToSignInScreen();

        logStep("STEP 2: Clicking Forgot Password?");
        Assert.assertTrue(signInPage.clickForgotPassword(), "Failed to click Forgot Password? link");
        pauseBetweenSteps();

        logStep("STEP 3: Verifying navigation occurred (left the Sign In screen)");
        Assert.assertFalse(signInPage.isStillOnSignInScreenAfterForgotPasswordClick(),
                "Forgot Password? did not navigate away from the Sign In screen");

        logStep("STEP 4: Clicking the back button on the Forgot Password screen");
        Assert.assertTrue(signInPage.clickForgotPasswordBackButton(), "Failed to click the Forgot Password back button");
        pauseBetweenSteps();

        logStep("STEP 5: Verifying return to the Sign In screen");
        Assert.assertTrue(signInPage.isSignInScreenDisplayed(), "Did not return to the Sign In screen after clicking back");

        logStep("STEP 6: Verifying the Sign In flow still works correctly after returning");
        Assert.assertTrue(signInPage.enterEmail(VALID_EMAIL), "Failed to enter email");
        Assert.assertTrue(signInPage.enterPassword(VALID_PASSWORD), "Failed to enter password");
        pauseBetweenSteps();
        Assert.assertTrue(signInPage.clickSignInButton(), "Failed to click Sign In button");
        pauseBetweenSteps();
        Assert.assertTrue(signInPage.isSignInSuccessful(), "Sign in did not succeed after returning from Forgot Password");
        logStep("TEST PASSED: Forgot Password back button returns to Sign In, and Sign In still works correctly");
    }

    @Test
    public void testSignInWithGoogleFromWelcomeScreen() {
        logStep("STEP 1: Starting test - Sign in with Google, reached directly from the Welcome screen");
        SignInPage signInPage = navigateToSignInScreen(true);

        logStep("STEP 2: Clicking Continue with Google");
        Assert.assertTrue(signInPage.clickContinueWithGoogle(), "Failed to click Continue with Google");
        pauseBetweenSteps();

        logStep("STEP 3: Selecting the first Google account");
        Assert.assertTrue(signInPage.selectFirstGoogleAccount(), "Failed to select a Google account");
        pauseBetweenSteps();

        logStep("STEP 4: Verifying successful sign-in");
        Assert.assertTrue(signInPage.isSignInSuccessful(), "Sign in with Google was not successful");
        logStep("TEST PASSED: Signed in with Google directly from the Welcome screen");
    }
}
