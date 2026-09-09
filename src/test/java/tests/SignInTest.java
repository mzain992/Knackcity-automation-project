package tests;

import base.BaseTest;
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import pages.OnboardingPage;
import pages.SignInPage;
import pages.SignupPage;

/**
 * End-to-end Sign In coverage — test plan cases TC-01 … TC-12, plus the Forgot Password
 * navigation check.
 *
 * <h2>Two entry points, one identical screen</h2>
 * The app reaches the SAME Sign In screen two different ways:
 * <ol>
 *   <li><b>Entry Point 1 — Welcome screen:</b> tap the Welcome screen's own "Sign In"
 *       button ({@code //android.view.ViewGroup[@content-desc="Sign In"]}).</li>
 *   <li><b>Entry Point 2 — Get Started screen:</b> tap "Get Started", then on the Signup
 *       screen tap its "Sign In" link ({@code //android.widget.TextView[@text="Sign In"]}).</li>
 * </ol>
 *
 * <h2>One reusable method, run per entry point</h2>
 * Rather than duplicating a dozen test cases per entry point, the whole plan lives in
 * {@link #executeSignInTestCases(SignInEntryPoint)}. A TestNG {@link DataProvider} invokes
 * it once per entry point, <b>sequentially</b>. Each invocation:
 * <ul>
 *   <li>navigates to Sign In through its entry point,</li>
 *   <li>runs the negative / validation / navigation cases (TC-02…TC-11 + Forgot Password) —
 *       these all leave the app ON the Sign In screen, so they chain with no app restart,</li>
 *   <li>then runs TC-01 and TC-12 last — the only two cases that actually authenticate and
 *       leave for the home screen, so exactly one in-session app restart is needed.</li>
 * </ul>
 *
 * <h2>Isolation &amp; reporting</h2>
 * Every sub-case runs inside {@link #runTestCase}, which records a pass/fail instead of
 * aborting the whole run, then best-effort recovers to a usable Sign In screen. All
 * failures are collected and raised together at the end, so one broken case never hides
 * the others.
 *
 * <h2>Why the negative assertions don't check the exact error text</h2>
 * For every "must be rejected" case the assertion is simply <i>"the app did not
 * authenticate and stayed on the Sign In screen"</i> ({@link SignInPage#isSignInUnsuccessful()}).
 * That keeps the tests stable across error-copy changes; any visible inline error is only
 * logged, for diagnostics.
 */
public class SignInTest extends BaseTest {

    /**
     * Fixed pause between visible UI steps. Kept short — the real synchronisation is done
     * by the explicit {@code WebDriverWait}s inside the page objects; this only gives the
     * UI a beat to settle between deliberate actions and keeps the run watchable.
     */
    private static final long STEP_DELAY_MS = 1500;

    // ------------------------------------------------------------------------------------
    // Test data — latest valid credentials per the test plan.
    // ------------------------------------------------------------------------------------
    /** TC-01 / TC-05 / TC-06 / TC-11 — the one registered account that must sign in. */
    private static final String VALID_EMAIL = "najam1@yopmail.com";
    private static final String VALID_PASSWORD = "Zain@123";
    /** TC-02 / TC-04 — well-formed address but NOT registered (real one is "najam1@…"). */
    private static final String INVALID_EMAIL = "najam@yopmail.com";
    /** TC-03 / TC-04 — wrong password for {@link #VALID_EMAIL}. */
    private static final String INVALID_PASSWORD = "zain321";
    /** TC-08 — not a valid e-mail format at all (double "@"). */
    private static final String MALFORMED_EMAIL = "abc@@gmail.com";
    /** TC-10 — the exact characters of {@link #VALID_PASSWORD} but wrong case → must fail. */
    private static final String WRONG_CASE_PASSWORD = "zain@123";
    /** TC-11 — well-formed, valid domain, but no account exists for it. */
    private static final String UNREGISTERED_EMAIL = "johndoe@yopmail.com";

    /** The two ways to reach the (identical) Sign In screen. */
    private enum SignInEntryPoint {
        /** Welcome screen's own "Sign In" button. */
        WELCOME_SCREEN,
        /** Welcome screen → "Get Started" → Signup screen's "Sign In" link. */
        GET_STARTED_SCREEN
    }

    /** Set at the start of each {@link #executeSignInTestCases} run; used by recovery/restart. */
    private SignInEntryPoint currentEntryPoint;

    /** A sub-case body that is allowed to throw (checked exceptions and assertion errors). */
    @FunctionalInterface
    private interface TestCaseBody {
        void run() throws Exception;
    }

    // ==================================================================================
    // Small helpers
    // ==================================================================================

    private void pauseBetweenSteps() {
        try {
            Thread.sleep(STEP_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(String message) {
        System.out.println("[SignInTest] " + message);
    }

    /** Human-readable rendering of a possibly-blank credential, for logs and messages. */
    private static String describe(String value) {
        return (value == null || value.isEmpty()) ? "<empty>" : value;
    }

    // ==================================================================================
    // The reusable Sign In test method
    // ==================================================================================

    /** One data-provider row per entry point — TestNG runs them sequentially. */
    @DataProvider(name = "signInEntryPoints")
    public Object[][] signInEntryPoints() {
        return new Object[][]{
                {SignInEntryPoint.WELCOME_SCREEN},
                {SignInEntryPoint.GET_STARTED_SCREEN}
        };
    }

    /**
     * THE reusable Sign In test method. Runs the full TC-01…TC-12 plan (plus Forgot
     * Password) against whichever entry point the data provider supplies. Called once per
     * entry point so both receive identical functional coverage with no duplicated code.
     */
    @Test(dataProvider = "signInEntryPoints")
    public void executeSignInTestCases(SignInEntryPoint entryPoint) {
        currentEntryPoint = entryPoint;
        log("############################################################");
        log("#  Sign In test plan  —  entry point: " + entryPoint);
        log("############################################################");

        // Navigate once up-front; every sub-case re-syncs itself to the Sign In screen from here.
        navigateToSignIn(entryPoint);

        List<String> failures = new ArrayList<>();

        // ---- Negative / validation / navigation cases: these stay on the Sign In screen ----
        runTestCase(failures, "TC-02", "Invalid email + valid password → rejected",
                () -> assertSignInRejected(INVALID_EMAIL, VALID_PASSWORD));
        runTestCase(failures, "TC-03", "Valid email + invalid password → rejected",
                () -> assertSignInRejected(VALID_EMAIL, INVALID_PASSWORD));
        runTestCase(failures, "TC-04", "Invalid email + invalid password → rejected",
                () -> assertSignInRejected(INVALID_EMAIL, INVALID_PASSWORD));
        runTestCase(failures, "TC-05", "Empty email + valid password → rejected",
                () -> assertSignInRejected("", VALID_PASSWORD));
        runTestCase(failures, "TC-06", "Valid email + empty password → rejected",
                () -> assertSignInRejected(VALID_EMAIL, ""));
        runTestCase(failures, "TC-07", "Both fields empty → rejected",
                () -> assertSignInRejected("", ""));
        runTestCase(failures, "TC-08", "Invalid email format (abc@@gmail.com) → rejected",
                () -> assertSignInRejected(MALFORMED_EMAIL, VALID_PASSWORD));
        runTestCase(failures, "TC-09", "Password show / hide toggle",
                this::assertPasswordShowHideWorks);
        runTestCase(failures, "TC-10", "Password is case-sensitive → wrong-case rejected",
                () -> assertSignInRejected(VALID_EMAIL, WRONG_CASE_PASSWORD));
        runTestCase(failures, "TC-11", "Unregistered email → rejected",
                () -> assertSignInRejected(UNREGISTERED_EMAIL, VALID_PASSWORD));
        runTestCase(failures, "TC-FP", "Forgot Password link navigates away and back",
                this::assertForgotPasswordNavigation);

        // ---- Positive cases last: they authenticate and leave the Sign In screen ----
        runTestCase(failures, "TC-01", "Valid email + valid password → sign-in succeeds",
                this::assertSignInSucceeds);
        runTestCase(failures, "TC-12", "Sign in with Google → sign-in succeeds",
                this::assertGoogleSignInSucceeds);

        // ---- Summary ----
        log("================  SUMMARY (" + entryPoint + ")  ================");
        if (failures.isEmpty()) {
            log("ALL Sign In test cases PASSED for entry point " + entryPoint);
        } else {
            log(failures.size() + " Sign In test case(s) FAILED for entry point " + entryPoint + ":");
            failures.forEach(f -> log("   - " + f));
            Assert.fail(failures.size() + " Sign In case(s) failed via " + entryPoint + ": " + failures);
        }
    }

    /**
     * Runs one sub-case in isolation: logs a banner, executes the body, records PASS/FAIL,
     * and — on failure — best-effort recovers to a usable Sign In screen so the remaining
     * cases still get a fair run. Catches {@link Throwable} so a failed TestNG assertion
     * inside a case is recorded, not propagated.
     */
    private void runTestCase(List<String> failures, String id, String title, TestCaseBody body) {
        log("------------------------------------------------------------");
        log(id + " : " + title);
        log("------------------------------------------------------------");
        try {
            body.run();
            log(id + " : PASSED");
        } catch (Throwable t) {
            log(id + " : FAILED — " + t.getMessage());
            failures.add(id + " (" + title + "): " + t.getMessage());
            recoverToSignInScreen();
        }
    }

    // ==================================================================================
    // Navigation
    // ==================================================================================

    /**
     * Drives the app from its freshly-cleared launch state (onboarding carousel) to the
     * Sign In screen through the given entry point, asserting the screen actually appears.
     */
    private SignInPage navigateToSignIn(SignInEntryPoint entryPoint) {
        OnboardingPage onboardingPage = new OnboardingPage(driver);
        SignInPage signInPage = new SignInPage(driver);

        log("Navigating to Sign In via " + entryPoint);

        // Both entry points first get past the onboarding carousel to the Welcome screen.
        onboardingPage.clickSkipButton();
        pauseBetweenSteps();

        if (entryPoint == SignInEntryPoint.WELCOME_SCREEN) {
            // Entry Point 1: tap the Welcome screen's own "Sign In" button.
            onboardingPage.clickSignInButton();
        } else {
            // Entry Point 2: Welcome → "Get Started" → Signup screen → its "Sign In" link.
            SignupPage signupPage = new SignupPage(driver);
            onboardingPage.clickGetStartedButton();
            pauseBetweenSteps();
            Assert.assertTrue(signupPage.isSignupScreenDisplayed(),
                    "Signup screen was not displayed after tapping Get Started");
            Assert.assertTrue(signupPage.clickSignInLink(),
                    "Failed to tap the 'Sign In' link on the Signup screen");
        }
        pauseBetweenSteps();

        Assert.assertTrue(signInPage.isSignInScreenDisplayed(),
                "Sign In screen was not displayed after navigating via " + entryPoint);
        return signInPage;
    }

    /**
     * Returns a Sign In page ready for the next sub-case:
     * <ul>
     *   <li>if we're still on the Sign In screen (the usual case — every negative test
     *       leaves us here), returns immediately; the next {@code enterEmail}/{@code
     *       enterPassword} clears the fields before typing;</li>
     *   <li>otherwise (a previous positive case authenticated and left for the home
     *       screen) restarts the app with cleared data and re-navigates via the current
     *       entry point.</li>
     * </ul>
     */
    private SignInPage freshSignInScreen() {
        SignInPage signInPage = new SignInPage(driver);
        if (signInPage.isOnSignInScreenNow()) {
            return signInPage;
        }
        log("No longer on the Sign In screen — restarting app and re-navigating via " + currentEntryPoint);
        restartAppWithClearedData();
        return navigateToSignIn(currentEntryPoint);
    }

    /** Best-effort recovery used after a sub-case throws. Never itself fails the run. */
    private void recoverToSignInScreen() {
        try {
            freshSignInScreen();
        } catch (Throwable t) {
            log("Recovery to the Sign In screen failed (continuing anyway): " + t.getMessage());
        }
    }

    // ==================================================================================
    // Sub-case bodies
    // ==================================================================================

    /**
     * Shared body for every "this must be refused" case (TC-02…TC-08, TC-10, TC-11).
     * Enters the given credentials (null / "" ⇒ leave that field blank), taps Sign In,
     * and asserts the app did NOT authenticate and stayed on the Sign In screen. A Sign In
     * button that isn't even clickable (the form blocking its own submit) is an accepted
     * outcome here, not a failure.
     */
    private void assertSignInRejected(String email, String password) throws Exception {
        SignInPage signInPage = freshSignInScreen();

        log("Entering credentials — email=[" + describe(email) + "], password=[" + describe(password) + "]");
        if (!signInPage.enterEmail(email)) {
            throw new AssertionError("could not type into the email field");
        }
        if (!signInPage.enterPassword(password)) {
            throw new AssertionError("could not type into the password field");
        }
        pauseBetweenSteps();

        boolean clicked = signInPage.clickSignInButton();
        log("Sign In button " + (clicked
                ? "tapped"
                : "was not clickable — form blocked its own submit (accepted for this case)"));
        pauseBetweenSteps();

        String inlineError = signInPage.getVisibleErrorText();
        if (inlineError != null) {
            log("Inline validation / auth error shown: \"" + inlineError + "\"");
        }

        Assert.assertTrue(signInPage.isSignInUnsuccessful(),
                "App appears to have signed in despite invalid input (email=" + describe(email)
                        + ", password=" + describe(password) + ")");
        log("Correctly rejected — still on the Sign In screen");
    }

    /** TC-01 — valid email + valid password must reach the post-auth screen. */
    private void assertSignInSucceeds() throws Exception {
        SignInPage signInPage = freshSignInScreen();

        Assert.assertTrue(signInPage.enterEmail(VALID_EMAIL), "Failed to enter email");
        Assert.assertTrue(signInPage.enterPassword(VALID_PASSWORD), "Failed to enter password");
        pauseBetweenSteps();

        Assert.assertTrue(signInPage.clickSignInButton(), "Failed to tap the Sign In button");
        pauseBetweenSteps();

        Assert.assertTrue(signInPage.isSignInSuccessful(),
                "Sign in with valid credentials did not reach the post-auth screen");
        log("Signed in successfully with valid credentials");
    }

    /** TC-09 — field starts masked, eye icon reveals it, tapping again re-masks it. */
    private void assertPasswordShowHideWorks() throws Exception {
        SignInPage signInPage = freshSignInScreen();

        Assert.assertTrue(signInPage.enterPassword(VALID_PASSWORD), "Failed to enter password");
        pauseBetweenSteps();

        Assert.assertTrue(signInPage.isPasswordMasked(), "Password was not masked by default");

        Assert.assertTrue(signInPage.togglePasswordVisibility(), "Failed to tap the eye icon (show)");
        pauseBetweenSteps();
        Assert.assertFalse(signInPage.isPasswordMasked(), "Password still masked after tapping 'show'");

        Assert.assertTrue(signInPage.togglePasswordVisibility(), "Failed to tap the eye icon (hide)");
        pauseBetweenSteps();
        Assert.assertTrue(signInPage.isPasswordMasked(), "Password not re-masked after tapping 'hide'");
        log("Password show/hide toggle works");
    }

    /**
     * TC-FP — the "Forgot Password?" link leaves the Sign In screen, and its back button
     * returns to it. (Detailed Forgot Password flow coverage is a separate concern; here
     * we only prove the navigation both ways so the link isn't broken.)
     */
    private void assertForgotPasswordNavigation() throws Exception {
        SignInPage signInPage = freshSignInScreen();

        Assert.assertTrue(signInPage.clickForgotPassword(), "Failed to tap the 'Forgot Password?' link");
        pauseBetweenSteps();
        Assert.assertFalse(signInPage.isStillOnSignInScreenAfterForgotPasswordClick(),
                "'Forgot Password?' did not navigate away from the Sign In screen");

        Assert.assertTrue(signInPage.clickForgotPasswordBackButton(),
                "Failed to tap the back button on the Forgot Password screen");
        pauseBetweenSteps();
        Assert.assertTrue(signInPage.isSignInScreenDisplayed(),
                "Did not return to the Sign In screen after tapping back");
        log("Forgot Password navigates away and back correctly");
    }

    /** TC-12 — Google sign-in from this entry point must reach the post-auth screen. */
    private void assertGoogleSignInSucceeds() throws Exception {
        SignInPage signInPage = freshSignInScreen();

        Assert.assertTrue(signInPage.signInWithGoogle(),
                "Failed to drive the 'Continue with Google' flow");
        pauseBetweenSteps();

        Assert.assertTrue(signInPage.isSignInSuccessful(),
                "Sign in with Google did not reach the post-auth screen");
        log("Signed in successfully with Google");
    }
}

