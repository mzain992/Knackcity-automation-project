package base;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

public class BaseTest {

    protected AndroidDriver driver;

    private static final String APPIUM_URL = "http://127.0.0.1:4723";
    private static final String DEVICE_NAME = "R8VXA01R73Y";
    private static final String APP_PACKAGE = "com.knackcity";
    private static final String APP_ACTIVITY = "com.knackcity.MainActivity";
    private static final int NEW_COMMAND_TIMEOUT_SECONDS = 120;

    @BeforeMethod
    public void setUp() throws MalformedURLException {
        dismissAnyLingeringSystemDialogs();
        clearAppData();

        UiAutomator2Options options = new UiAutomator2Options();
        options.setDeviceName(DEVICE_NAME);
        options.setUdid(DEVICE_NAME);
        options.setAppPackage(APP_PACKAGE);
        options.setAppActivity(APP_ACTIVITY);
        options.setNoReset(true);
        options.setAutomationName("UiAutomator2");
        options.setPlatformName("Android");

        // Additional capabilities
        options.setNewCommandTimeout(Duration.ofSeconds(NEW_COMMAND_TIMEOUT_SECONDS));
        options.setAutoGrantPermissions(true);
        // The onboarding screen has a continuously animating SVG icon, which means the
        // screen never reaches UiAutomator2's default "idle" state. Its default
        // waitForIdleTimeout (10s) makes every single findElement call block for that
        // full 10s before returning, measured on-device. Disabling it drops each lookup
        // to ~0.2-0.5s and removes a major source of flaky/slow element waits app-wide.
        options.setCapability("appium:waitForIdleTimeout", 0);
        // This device is slow to spin up the UiAutomator2 instrumentation on a cold start
        // (seen intermittently: "The instrumentation process cannot be initialized within
        // 30000ms timeout"). Give it more headroom, and let Appium reinstall the helper
        // servers if a stale/broken copy is the cause.
        options.setCapability("appium:uiautomator2ServerLaunchTimeout", 90000);
        options.setCapability("appium:uiautomator2ServerInstallTimeout", 90000);
        options.setCapability("appium:uiautomator2ServerReadTimeout", 90000);
        options.setCapability("appium:enforceAppInstall", false);
        // autoWebview is intentionally NOT set here: it makes UiAutomator2 switch to a
        // WebView context immediately on session start, which fails with
        // "SessionNotCreatedException: No such context found" because this app launches
        // on a native onboarding screen with no WebView present yet. SignupPage switches
        // into the Terms & Conditions WebView context only once it's actually on screen.
        //
        // No implicit wait (server capability or driver.manage().timeouts()) is set here,
        // intentionally. Mixing an implicit wait with the explicit WebDriverWaits used
        // throughout OnboardingPage/SignupPage is a documented Selenium anti-pattern that
        // can produce spurious TimeoutExceptions on elements that are genuinely on screen.

        driver = new AndroidDriver(new URL(APPIUM_URL), options);
        handleInitialPermissionDialogIfPresent();
    }

    /**
     * The notification-permission dialog ("Allow Knackcity to send you notifications?")
     * can appear immediately on session creation, before any test method's first
     * onboarding action runs — confirmed on-device: autoGrantPermissions doesn't reliably
     * cover this specific runtime permission on every launch. Handling it here, right
     * after the driver is created, closes that timing gap rather than relying solely on
     * OnboardingPage's own check (which only runs once a test calls into it). Confirmed
     * on-device: resource-id="com.android.permissioncontroller:id/permission_allow_button"
     * is correct — clicking it flips POST_NOTIFICATIONS to granted=true.
     */
    private void handleInitialPermissionDialogIfPresent() {
        try {
            WebDriverWait permissionWait = new WebDriverWait(driver, Duration.ofSeconds(5));
            WebElement allowButton = permissionWait.until(ExpectedConditions.elementToBeClickable(
                    By.id("com.android.permissioncontroller:id/permission_allow_button")));
            allowButton.click();
            System.out.println("[BaseTest] Handled initial notification permission dialog (Allow)");
        } catch (Exception e) {
            // No permission dialog appeared — fine, nothing to dismiss.
        }
    }

    /**
     * If a previous test crashed mid-flow while a system dialog/picker (permission prompt,
     * photo picker, notification shade, etc.) was open, that foreign screen can still be in
     * the foreground when the next test starts, breaking it too even though its own
     * app-data was cleared. Pressing back a few times, then HOME, before every test clears
     * that regardless of what the previous test left behind — back alone doesn't dismiss an
     * open notification shade (confirmed on-device: it can be left open by an interrupted
     * scroll gesture), but HOME reliably does and returns to a neutral launcher state.
     */
    private void dismissAnyLingeringSystemDialogs() {
        try {
            for (int i = 0; i < 3; i++) {
                new ProcessBuilder("adb", "-s", DEVICE_NAME, "shell", "input", "keyevent", "4")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
            }
            new ProcessBuilder("adb", "-s", DEVICE_NAME, "shell", "input", "keyevent", "3")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (Exception e) {
            System.out.println("[BaseTest] Failed to dismiss lingering dialogs before test: " + e.getMessage());
        }
    }

    /**
     * noReset=true (kept as configured) preserves app data across sessions, but that means
     * the app's own navigation state (onboarding seen, last-used login email, draft signup
     * progress, etc.) bleeds from one test method into the next, making each test's starting
     * screen unpredictable. Clearing app data before every test guarantees a deterministic
     * fresh-install starting state (the onboarding carousel) without changing the noReset
     * driver capability itself.
     */
    private void clearAppData() {
        try {
            new ProcessBuilder("adb", "-s", DEVICE_NAME, "shell", "pm", "clear", APP_PACKAGE)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (Exception e) {
            System.out.println("[BaseTest] Failed to clear app data before test: " + e.getMessage());
        }
    }

    /**
     * Restarts the app to a deterministic freshly-installed state WITHOUT tearing down the
     * Appium session. Needed by tests that chain several sub-scenarios in a single session
     * (e.g. {@code SignInTest.executeSignInTestCases}) once one of those sub-scenarios has
     * actually authenticated and navigated away from the screen under test — clearing app
     * data and relaunching is the only reliable way back to the onboarding carousel from
     * the post-login home screen. Mirrors what {@link #setUp()} does per test, minus
     * creating a new driver.
     */
    protected void restartAppWithClearedData() {
        System.out.println("[BaseTest] Restarting app with cleared data (in-session reset)");
        try {
            driver.terminateApp(APP_PACKAGE);
        } catch (Exception e) {
            System.out.println("[BaseTest] terminateApp failed (continuing): " + e.getMessage());
        }
        dismissAnyLingeringSystemDialogs();
        clearAppData();
        try {
            driver.activateApp(APP_PACKAGE);
        } catch (Exception e) {
            System.out.println("[BaseTest] activateApp failed: " + e.getMessage());
        }
        handleInitialPermissionDialogIfPresent();
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
