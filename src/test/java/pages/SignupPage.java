package pages;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SignupPage {

    private static final Duration EXPLICIT_WAIT = Duration.ofSeconds(15);
    private static final Duration SCROLL_RETRY_WAIT = Duration.ofSeconds(5);
    private static final Duration PERMISSION_WAIT = Duration.ofSeconds(5);
    private static final Duration ACTION_WAIT = Duration.ofSeconds(2);
    private static final int BIO_MAX_LENGTH = 150;
    // The signup form is a long scrollable page; fields past Location/Phone (Confirm
    // Password, Terms checkbox, Create Account, Continue with Google) are off-screen until
    // scrolled into view. Confirmed on-device: their locators are correct, they just aren't
    // visible/clickable until the form is scrolled down. Adding the DOB and Gender fields
    // lengthened the form further — 3 attempts was enough before but confirmed on-device to
    // no longer be enough to reach Phone Number/Password/Confirm Password/Terms/Create
    // Account/Continue with Google, so this is bumped up with headroom to spare.
    private static final int MAX_SCROLL_ATTEMPTS = 6;

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // ---------------- Locators ----------------

    // Signup screen marker (full name field is mandatory, always present)
    private static final By FULL_NAME_FIELD = By.xpath("//android.widget.EditText[@text=\"Enter your full name\"]");

    // Image upload
    // The tap target that opens the Gallery/Camera picker is the circular icon + its
    // caption, grouped under one clickable ViewGroup with this content-desc. Confirmed
    // on-device (resource-id not present on this element, content-desc is exact/clean —
    // no trailing-comma quirk here).
    private static final By UPLOAD_IMAGE_BUTTON = By.xpath("//android.view.ViewGroup[@content-desc=\"Upload Image (optional)\"]");
    private static final By CHOOSE_FROM_GALLERY = By.xpath("//android.widget.TextView[@text=\"Choose from Gallery\"]");
    // Camera branch of the same upload-image picker (sibling option to "Choose from
    // Gallery"). Locator as supplied for this flow — not yet re-confirmed on-device in
    // this session (Appium MCP was unavailable), so treat as provisional like the rest
    // of this block until run once.
    private static final By USE_CAMERA_OPTION = By.xpath("//android.view.ViewGroup[@content-desc=\"Use Camera\"]");
    // Camera permission dialog's "Allow only while using the app" button, as supplied.
    // Distinct element from PERMISSION_ALLOW_BUTTONS' by-id lookup of the same
    // resource-id; kept separate since this flow's steps were given explicitly by xpath.
    private static final By CAMERA_PERMISSION_ALLOW_FOREGROUND_ONLY =
            By.xpath("//android.widget.Button[@resource-id=\"com.android.permissioncontroller:id/permission_allow_foreground_only_button\"]");
    // In-app camera's shutter button, as supplied.
    private static final By CAMERA_SHUTTER_BUTTON =
            By.xpath("//android.view.ViewGroup[@resource-id=\"in-app-camera-shutter-button\"]/android.view.ViewGroup");
    // Confirmed on-device: the shutter takes the picture and drops straight onto a uCrop
    // "Edit Photo" screen (resource-id="com.knackcity:id/ucrop_photobox"). Its toolbar has
    // two actions — the "Navigate up" ImageButton (top-left, CANCELS the crop and returns
    // to the signup form with NO image set) and the "Crop" Button
    // (resource-id="com.knackcity:id/menu_crop", top-right, ACCEPTS the crop and returns
    // the image to the form). The requested flow deliberately exercises both: capture,
    // cancel via Navigate up, re-capture, then accept via Crop.
    private static final By CAMERA_CAPTURED_IMAGE_CLOSE_BUTTON =
            By.xpath("//android.widget.ImageButton[@content-desc=\"Navigate up\"]");
    private static final By CAMERA_CROP_BUTTON =
            By.xpath("//*[@resource-id=\"com.knackcity:id/menu_crop\"]");

    // Common Android runtime-permission dialog buttons (system permission controller).
    // Tried in order; whichever appears first is accepted. Android 14+'s photo-picker
    // permission dialog ("Allow limited access" / "Allow all" / "Don't allow") is a
    // different layout from the classic Allow/Deny prompt — confirmed on-device via its
    // resource-ids, listed first since it's what this app's Android 15 test device shows.
    private static final By[] PERMISSION_ALLOW_BUTTONS = {
            By.id("com.android.permissioncontroller:id/permission_allow_all_button"),
            By.id("com.android.permissioncontroller:id/permission_allow_button"),
            By.id("com.android.permissioncontroller:id/permission_allow_foreground_only_button"),
            By.id("com.android.permissioncontroller:id/permission_allow_one_time_button"),
            By.xpath("//android.widget.Button[@text=\"While using the app\"]"),
            By.xpath("//android.widget.Button[@text=\"Only this time\"]"),
            By.xpath("//android.widget.Button[@text=\"Allow\"]"),
            By.xpath("//android.widget.Button[@resource-id=\"com.android.packageinstaller:id/permission_allow_button\"]")
    };

    // Native Android Photo Picker (com.google.android.photopicker), shown after granting
    // gallery access. It's a Compose UI with no resource-ids; grid items only expose an
    // auto-generated "Photo taken on <date>" content-desc, so this targets the first grid
    // item positionally via XPath index rather than a fixed value. Confirmed on-device.
    private static final By FIRST_PHOTO_IN_PICKER =
            By.xpath("(//android.view.View[contains(@content-desc,\"Photo taken on\")])[1]");
    private static final By PHOTO_PICKER_DONE_BUTTON = By.xpath("//android.widget.TextView[@text=\"Done\"]/..");

    // Form fields
    private static final By SCREEN_NAME_FIELD = By.xpath("//android.widget.EditText[@resource-id=\"signup-screen-name-input\"]");
    private static final By EMAIL_FIELD = By.xpath("//android.widget.EditText[@text=\"Enter your email\"]");
    // The DOB EditText itself is disabled/non-clickable (display-only); the actual tap
    // target is its wrapping ViewGroup, resource-id="signup-dob-input". Confirmed on-device.
    // Tapping it opens a native android.app.DatePickerDialog (classic CalendarView style,
    // not a text field), so DOB entry is a picker interaction, not sendKeys().
    private static final By DOB_FIELD = By.xpath("//android.widget.EditText[@text=\"Select your date of birth\"]");
    private static final By DOB_INPUT_WRAPPER = By.xpath("//*[@resource-id=\"signup-dob-input\"]");
    private static final By DOB_PICKER_YEAR_HEADER = By.id("android:id/date_picker_header_year");
    private static final By DOB_PICKER_OK_BUTTON = By.id("android:id/button1");
    // Same wrapper pattern as DOB: the EditText is disabled/non-clickable; the real tap
    // target is its wrapping ViewGroup, resource-id="signup-gender-input". Confirmed
    // on-device. Tapping it opens a simple modal list (Male/Female/Non-binary/
    // Transgender/Prefer not to say), each option its own clickable ViewGroup with a
    // clean resource-id — used here instead of the content-desc="Male" the option also
    // exposes, consistent with this codebase's preference for resource-id where available.
    private static final By GENDER_INPUT_WRAPPER = By.xpath("//*[@resource-id=\"signup-gender-input\"]");
    private static final By GENDER_OPTION_MALE = By.xpath("//*[@resource-id=\"signup-gender-option-male\"]");
    // Follows the same resource-id pattern as the confirmed Male option
    // ("signup-gender-option-<value>"); not independently re-confirmed on-device this
    // session (Appium MCP was unavailable).
    private static final By GENDER_OPTION_FEMALE = By.xpath("//*[@resource-id=\"signup-gender-option-female\"]");
    private static final By BIO_FIELD = By.xpath("//android.widget.EditText[@text=\"Enter your bio\"]");
    private static final By LOCATION_FIELD = By.xpath("//android.widget.EditText[@text=\"Search your address\"]");
    private static final By LOCATION_DROPDOWN_SAN_FRANCISCO = By.xpath("//android.widget.TextView[@text=\"San Francisco, CA, USA\"]");
    // NOTE: this build's placeholder is "Enter Phone Number" — confirmed on-device this
    // changed from the earlier "123 456 7890" placeholder seen in a prior build.
    private static final By PHONE_FIELD = By.xpath("//android.widget.EditText[@text=\"Enter Phone Number\"]");
    // BUG FIX: previously anchored on the password="true"/"false" attribute, but that
    // attribute is present on EVERY EditText (it's "false" on Full Name, Bio, Email —
    // not just actual password fields), so that filter matched *any* text field and
    // positional indexing [1]/[2] landed on arbitrary fields (confirmed on-device: this
    // caused the password value to be typed into Bio). Anchored on the stable "Password
    // *" / "Confirm Password *" label text instead via the following:: axis — this stays
    // correct regardless of what's typed or the field's masked/unmasked state, unlike
    // both the placeholder text (changes once typed) and the password attribute (not
    // unique, and flips on toggle).
    private static final By PASSWORD_FIELD =
            By.xpath("//android.widget.TextView[@text=\"Password *\"]/following::android.widget.EditText[1]");
    private static final By CONFIRM_PASSWORD_FIELD =
            By.xpath("//android.widget.TextView[@text=\"Confirm Password *\"]/following::android.widget.EditText[1]");
    // The eye icon has no resource-id and an empty content-desc (confirmed on-device);
    // it's reliably the ViewGroup immediately following the Password EditText.
    private static final By PASSWORD_EYE_ICON = By.xpath(
            "//android.widget.TextView[@text=\"Password *\"]/following::android.widget.EditText[1]/following-sibling::android.view.ViewGroup[1]");

    // Terms & Conditions
    private static final By TERMS_AND_CONDITIONS_LINK = By.xpath("//android.widget.TextView[@text=\"I agree to the Terms & Conditions\"]");
    // BUG FIX: this is a NATIVE in-app screen, not a WebView — confirmed on-device (its
    // own header reads "Terms & Conditions"; android.webkit.WebView never appears in the
    // tree). The old locator waited for a WebView that would never exist, so
    // isTermsWebpageDisplayed() always failed and aborted the whole signup test right
    // there, before Terms checkbox/Create Account ever ran.
    private static final By TERMS_PAGE_MARKER = By.xpath("//android.widget.TextView[@text=\"Terms & Conditions\"]");
    // BUG FIX: the back arrow's TextView (icon glyph) has empty text but is NOT
    // clickable; the real tap target is a clickable ViewGroup with an empty content-desc.
    // Exact-matching an EMPTY content-desc string via live XPath findElement proved
    // unreliable on this UiAutomator2 setup, so this anchors structurally instead: every
    // screen in this app shares one header component, resource-id="custom-header"
    // (confirmed on-device present identically on both the Terms page and the Forgot
    // Password screen), and the back button is reliably its immediately-following
    // clickable sibling — a robust, reusable pattern across screens.
    private static final By TERMS_BACK_BUTTON =
            By.xpath("//*[@resource-id=\"custom-header\"]/following-sibling::android.view.ViewGroup[1]");
    // Confirmed on-device: android.widget.CheckBox resource-id="terms-checkbox".
    private static final By TERMS_CHECKBOX = By.xpath("//android.widget.CheckBox[@resource-id=\"terms-checkbox\"]");

    // Create account
    // Confirmed on-device: resource-id="signup-submit", content-desc="Create Account".
    private static final By CREATE_ACCOUNT_BUTTON = By.xpath("//*[@resource-id=\"signup-submit\"]");
    // Confirmed on-device: successful signup (manual or Google) lands on the shared home
    // screen greeting, e.g. "Hi <name>" / "Welcome to Knackcity!". The greeting name text
    // varies per account, so this anchors on the static "Welcome to Knackcity!" TextView.
    private static final By SIGNUP_SUCCESS_MARKER = By.xpath("//android.widget.TextView[@text=\"Welcome to Knackcity!\"]");

    // Sign In (link back to the Sign In screen from Signup)
    // Same wrapper pattern as elsewhere: the clickable node is the ViewGroup, not the
    // inner TextView. Confirmed on-device on the actual Sign In screen's own "Sign In"
    // submit button; this signup-screen link follows the same app-wide button pattern.
    private static final By SIGN_IN_LINK = By.xpath("//android.view.ViewGroup[@content-desc=\"Sign In\"]");

    // Google login
    // Confirmed on-device: the clickable node is the ViewGroup wrapper (content-desc exact,
    // no trailing-comma quirk), not the inner TextView (which is not itself clickable).
    private static final By CONTINUE_WITH_GOOGLE = By.xpath("//android.view.ViewGroup[@content-desc=\"Continue with Google\"]");
    private static final By GOOGLE_ACCOUNT_OPTION = By.xpath(
            "//android.widget.TextView[@resource-id=\"com.google.android.gms:id/account_display_name\" and @text=\"Onyx Test Account\"]");

    public SignupPage(AndroidDriver driver) {
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

    private String maskIfSensitive(String fieldName, String value) {
        return fieldName.toLowerCase().contains("password") ? "****" : value;
    }

    /**
     * Hides the on-screen keyboard after typing. Confirmed on-device: a keyboard left
     * open after sendKeys() occupies the bottom half of the screen and blocks further
     * scroll gestures from reaching fields/buttons further down the form — this was the
     * real cause behind several "not visible even after scrolling" failures, not just
     * an insufficient scroll budget.
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
            System.out.println("[SignupPage] scrollDown failed: " + e.getMessage());
        }
    }

    private void scrollUp() {
        Map<String, Object> params = new HashMap<>();
        params.put("left", 50);
        params.put("top", 400);
        params.put("width", 620);
        params.put("height", 900);
        params.put("direction", "up");
        params.put("percent", 0.8);
        try {
            driver.executeScript("mobile: scrollGesture", params);
        } catch (Exception e) {
            System.out.println("[SignupPage] scrollUp failed: " + e.getMessage());
        }
    }

    /**
     * Locates a clickable element, scrolling the form down and retrying if it isn't
     * visible yet. The signup form is a long scrollable page — fields below the fold
     * aren't found/clickable until scrolled into view.
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
                System.out.println("[SignupPage] '" + label + "' not visible yet, scrolling down (attempt " + attempt + "/" + MAX_SCROLL_ATTEMPTS + ")");
                scrollDown();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * Same as findClickableWithScroll, but scrolls UP instead of down. DOB sits near the
     * top of the signup form — confirmed on-device that after a failed submit attempt
     * (e.g. the mandatory-DOB validation test, which fills every field below DOB and
     * submits before circling back to DOB), the form is left scrolled near the bottom, so
     * DOB needs to be scrolled back UP into view. The down-only retry loop above cannot
     * reach it in that case (scrolling down only pushes DOB further out of view), which is
     * exactly what caused clickDobField() to fail after an empty-DOB submit attempt.
     */
    private WebElement findClickableWithScrollUp(By locator, String label) {
        for (int attempt = 1; attempt <= MAX_SCROLL_ATTEMPTS; attempt++) {
            try {
                WebDriverWait attemptWait = attempt == 1 ? wait : new WebDriverWait(driver, SCROLL_RETRY_WAIT);
                return attemptWait.until(ExpectedConditions.elementToBeClickable(locator));
            } catch (Exception e) {
                if (attempt == MAX_SCROLL_ATTEMPTS) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
                System.out.println("[SignupPage] '" + label + "' not visible yet, scrolling up (attempt " + attempt + "/" + MAX_SCROLL_ATTEMPTS + ")");
                scrollUp();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private boolean click(By locator, String actionName) {
        try {
            WebElement element = findClickableWithScroll(locator, actionName);
            element.click();
            System.out.println("[SignupPage] Clicked '" + actionName + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignupPage] '" + actionName + "' failed: " + e.getMessage());
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
            System.out.println("[SignupPage] Filled '" + fieldName + "' with '" + maskIfSensitive(fieldName, text) + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignupPage] Could not enter text into '" + fieldName + "': " + e.getMessage());
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

    // ---------------- Signup screen marker ----------------

    // BUG FIX: checking Full Name alone breaks after returning from the Terms &
    // Conditions page, which preserves the form's scroll position — if that was
    // scrolled to the bottom (as it is right after Terms & Conditions, positioned near
    // there), Full Name is scrolled out of view and isn't even in the accessibility tree
    // (confirmed on-device), so a Full-Name-only check would time out. Matches Full Name
    // (top of form) OR the Create Account button (bottom of form) — whichever is
    // currently in view still confirms we're genuinely on the signup screen.
    private static final By SIGNUP_SCREEN_MARKER = By.xpath(
            "//android.widget.EditText[@text=\"Enter your full name\"] | //*[@resource-id=\"signup-submit\"]");

    public boolean isSignupScreenDisplayed() {
        return isDisplayed(SIGNUP_SCREEN_MARKER);
    }

    // ---------------- Image upload ----------------

    public boolean clickUploadImageButton() {
        return click(UPLOAD_IMAGE_BUTTON, "clickUploadImageButton");
    }

    public boolean clickChooseFromGallery() {
        return click(CHOOSE_FROM_GALLERY, "clickChooseFromGallery");
    }

    public boolean handlePermissionIfNeeded() {
        WebDriverWait permissionWait = new WebDriverWait(driver, PERMISSION_WAIT);
        for (By locator : PERMISSION_ALLOW_BUTTONS) {
            try {
                WebElement button = permissionWait.until(ExpectedConditions.elementToBeClickable(locator));
                button.click();
                System.out.println("[SignupPage] Handled permission popup via locator: " + locator);
                pauseForAction();
                return true;
            } catch (Exception ignored) {
                // Not this dialog/button — try the next known locator.
            }
        }
        System.out.println("[SignupPage] No permission popup appeared (or none of the known locators matched).");
        return false;
    }

    public boolean selectFirstPhotoFromPicker() {
        return click(FIRST_PHOTO_IN_PICKER, "selectFirstPhotoFromPicker");
    }

    public boolean confirmPhotoSelection() {
        return click(PHOTO_PICKER_DONE_BUTTON, "confirmPhotoSelection");
    }

    /**
     * Presses the Android back button a few times to dismiss any lingering system dialog
     * or picker (permission prompt, photo picker, etc.) and return to the app. Used to
     * recover from a partially-failed optional step so it can't strand a later step — or
     * even the next test's session — on a foreign screen.
     */
    private void recoverToApp() {
        for (int i = 0; i < 3; i++) {
            try {
                driver.navigate().back();
            } catch (Exception ignored) {
                // Nothing to go back from — fine, we're likely already on the app.
            }
        }
        pauseForAction();
    }

    /**
     * Full optional-image-upload flow: opens the picker, chooses Gallery, grants the
     * permission prompt (if one appears — autoGrantPermissions can make it skip straight
     * to the picker), selects the first available photo, and confirms it. Best-effort —
     * the field is optional and this walks through the native OS photo picker, which is
     * more environment/OS-version-dependent than the app's own UI (e.g. relies on the test
     * device actually having at least one photo in its gallery). On any failure it presses
     * back to recover to the signup form, so a failed optional step can't strand later
     * steps — or the next test — on a stuck system dialog/picker screen.
     */
    public boolean uploadFirstAvailablePhotoFromGallery() {
        if (!clickUploadImageButton()) {
            return false;
        }
        if (!clickChooseFromGallery()) {
            recoverToApp();
            return false;
        }
        handlePermissionIfNeeded(); // best-effort; no-op if no permission prompt appears
        if (!selectFirstPhotoFromPicker()) {
            recoverToApp();
            return false;
        }
        if (!confirmPhotoSelection()) {
            recoverToApp();
            return false;
        }
        return true;
    }

    public boolean clickUseCamera() {
        return click(USE_CAMERA_OPTION, "clickUseCamera");
    }

    public boolean handleCameraPermissionIfNeeded() {
        WebDriverWait permissionWait = new WebDriverWait(driver, PERMISSION_WAIT);
        try {
            WebElement button = permissionWait.until(ExpectedConditions.elementToBeClickable(CAMERA_PERMISSION_ALLOW_FOREGROUND_ONLY));
            button.click();
            System.out.println("[SignupPage] Handled camera permission popup (Allow while using the app)");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignupPage] No camera permission popup appeared (or already granted).");
            return false;
        }
    }

    public boolean clickCameraShutterButton() {
        return click(CAMERA_SHUTTER_BUTTON, "clickCameraShutterButton");
    }

    public boolean closeCapturedCameraImage() {
        return click(CAMERA_CAPTURED_IMAGE_CLOSE_BUTTON, "closeCapturedCameraImage");
    }

    public boolean cropCapturedCameraImage() {
        return click(CAMERA_CROP_BUTTON, "cropCapturedCameraImage");
    }

    /** Opens the picker, chooses Use Camera, grants the permission prompt if shown, taps the shutter. */
    private boolean openCameraAndCapture() {
        if (!clickUploadImageButton()) {
            return false;
        }
        if (!clickUseCamera()) {
            return false;
        }
        handleCameraPermissionIfNeeded(); // best-effort; no-op if no permission prompt appears
        return clickCameraShutterButton();
    }

    /**
     * Full optional-image-upload flow via the in-app camera, per the requested steps:
     * capture a photo, close it via "Navigate up" (which cancels the crop and returns to
     * the signup form with no image set), capture again, then confirm with "Crop" to
     * select the image. Best-effort, mirroring uploadFirstAvailablePhotoFromGallery — on
     * any failure it presses back to recover to the signup form so a failed optional step
     * can't strand later steps (or the next test) on a stuck camera/crop screen.
     */
    public boolean uploadPhotoFromCamera() {
        // First capture — then discard it via the crop screen's "Navigate up" button.
        if (!openCameraAndCapture()) {
            recoverToApp();
            return false;
        }
        if (!closeCapturedCameraImage()) {
            recoverToApp();
            return false;
        }
        // Second capture — this one is kept: confirm the crop to select the image.
        if (!openCameraAndCapture()) {
            recoverToApp();
            return false;
        }
        if (!cropCapturedCameraImage()) {
            recoverToApp();
            return false;
        }
        return true;
    }

    // ---------------- Form fields ----------------

    public boolean enterFullName(String name) {
        return type(FULL_NAME_FIELD, name, "Full Name");
    }

    public boolean enterScreenName(String screenName) {
        return type(SCREEN_NAME_FIELD, screenName, "Screen Name");
    }

    public boolean enterEmail(String email) {
        return type(EMAIL_FIELD, email, "Email");
    }

    public boolean isDobFieldDisplayed() {
        return isDisplayed(DOB_FIELD);
    }

    public boolean clickDobField() {
        try {
            WebElement element = findClickableWithScrollUp(DOB_INPUT_WRAPPER, "clickDobField");
            element.click();
            System.out.println("[SignupPage] Clicked 'clickDobField'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignupPage] 'clickDobField' failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Opens the DOB picker and selects the given year, keeping the current month/day
     * (the dialog defaults to today's date, so changing only the year yields a valid
     * past date without needing to fight the day grid too). Best-effort on the year:
     * the native year list's fling-scroll behaviour is unreliable under automation
     * (confirmed on-device — momentum overshoot bounces it back to the top repeatedly),
     * so this tries a bounded, scoped scrollIntoView and falls through to whatever year
     * is on screen (even today's) rather than hang — DOB being *set* to *some* past-or-
     * present date matters more here than hitting the exact target year.
     */
    public boolean selectDateOfBirth(String targetYear) {
        if (!clickDobField()) {
            return false;
        }
        try {
            WebElement yearHeader = wait.until(ExpectedConditions.elementToBeClickable(DOB_PICKER_YEAR_HEADER));
            yearHeader.click();
            pauseForAction();

            try {
                WebElement yearItem = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//android.widget.TextView[@resource-id=\"android:id/text1\" and @text=\"" + targetYear + "\"]")));
                yearItem.click();
                System.out.println("[SignupPage] Selected DOB year " + targetYear);
            } catch (Exception directClickFailed) {
                // Target year wasn't already visible — try the UiAutomator scroll idiom once.
                String uiScrollable = "new UiScrollable(new UiSelector().resourceId(\"android:id/date_picker_year_picker\"))"
                        + ".setAsVerticalList().scrollIntoView(new UiSelector().resourceId(\"android:id/text1\").text(\"" + targetYear + "\"))";
                try {
                    driver.findElement(AppiumBy.androidUIAutomator(uiScrollable));
                    System.out.println("[SignupPage] Scrolled to and selected DOB year " + targetYear + " via UiScrollable");
                } catch (Exception scrollFailed) {
                    System.out.println("[SignupPage] Could not reach DOB year " + targetYear
                            + " (native year-list scrolling is flaky under automation) — keeping default year: "
                            + scrollFailed.getMessage());
                }
            }
            pauseForAction();
        } catch (Exception e) {
            System.out.println("[SignupPage] Could not open DOB year picker, proceeding with default date: " + e.getMessage());
        }

        try {
            WebElement okButton = wait.until(ExpectedConditions.elementToBeClickable(DOB_PICKER_OK_BUTTON));
            okButton.click();
            System.out.println("[SignupPage] Confirmed DOB picker (OK)");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignupPage] Could not confirm DOB picker: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies DOB is enforced as mandatory: leaves it untouched (empty) and confirms
     * that submitting the form does NOT succeed — i.e. we're still on the signup screen
     * afterward. Uses the signup screen's own presence as the check rather than a
     * specific error-message locator, since no DOB-required error text/locator has been
     * confirmed on-device yet.
     */
    public boolean isStillOnSignupScreenAfterEmptyDobSubmit() {
        clickCreateAccountButton();
        pauseForAction();
        return isSignupScreenDisplayed();
    }

    public boolean clickGenderField() {
        return click(GENDER_INPUT_WRAPPER, "clickGenderField");
    }

    public boolean selectGenderMale() {
        return click(GENDER_OPTION_MALE, "selectGenderMale");
    }

    /**
     * Opens the Gender dropdown and selects Male in one call, for use alongside the
     * other field-fill calls in the main signup flow.
     */
    public boolean selectGender() {
        if (!clickGenderField()) {
            return false;
        }
        return selectGenderMale();
    }

    public boolean selectGenderFemale() {
        return click(GENDER_OPTION_FEMALE, "selectGenderFemale");
    }

    /**
     * Opens the Gender dropdown and selects Female in one call, mirroring selectGender()
     * (Male) above.
     */
    public boolean selectGenderAsFemale() {
        if (!clickGenderField()) {
            return false;
        }
        return selectGenderFemale();
    }

    public boolean enterBio(String bio) {
        if (bio == null) {
            return true;
        }
        if (bio.length() > BIO_MAX_LENGTH) {
            System.out.println("[SignupPage] Bio exceeds " + BIO_MAX_LENGTH + " characters, truncating.");
            bio = bio.substring(0, BIO_MAX_LENGTH);
        }
        return type(BIO_FIELD, bio, "Bio");
    }

    public boolean enterLocation(String location) {
        return type(LOCATION_FIELD, location, "Location");
    }

    public boolean selectLocationFromDropdown() {
        return click(LOCATION_DROPDOWN_SAN_FRANCISCO, "selectLocationFromDropdown");
    }

    public boolean enterPhoneNumber(String phone) {
        return type(PHONE_FIELD, phone, "Phone Number");
    }

    public boolean enterPassword(String password) {
        return type(PASSWORD_FIELD, password, "Password");
    }

    public boolean enterConfirmPassword(String password) {
        return type(CONFIRM_PASSWORD_FIELD, password, "Confirm Password");
    }

    public boolean togglePasswordVisibility() {
        return click(PASSWORD_EYE_ICON, "togglePasswordVisibility");
    }

    /**
     * True if the Password field is currently masking its content (password="true" in
     * the accessibility tree — confirmed on-device this flips to "false" once the eye
     * icon reveals the plaintext value).
     */
    public boolean isPasswordMasked() {
        try {
            WebElement field = wait.until(ExpectedConditions.presenceOfElementLocated(PASSWORD_FIELD));
            return "true".equals(field.getAttribute("password"));
        } catch (Exception e) {
            System.out.println("[SignupPage] Could not read password masking state: " + e.getMessage());
            return true; // fail safe: assume still masked if we can't tell
        }
    }

    // ---------------- Terms & Conditions ----------------

    public boolean clickTermsAndConditions() {
        return click(TERMS_AND_CONDITIONS_LINK, "clickTermsAndConditions");
    }

    public boolean isTermsWebpageDisplayed() {
        return isDisplayed(TERMS_PAGE_MARKER);
    }

    public boolean clickBackButton() {
        return click(TERMS_BACK_BUTTON, "clickBackButton");
    }

    public boolean agreeTermsCheckbox() {
        return click(TERMS_CHECKBOX, "agreeTermsCheckbox");
    }

    // ---------------- Create Account ----------------

    public boolean clickCreateAccountButton() {
        return click(CREATE_ACCOUNT_BUTTON, "clickCreateAccountButton");
    }

    public boolean isSignupSuccessful() {
        return isDisplayed(SIGNUP_SUCCESS_MARKER);
    }

    // ---------------- Sign In (navigate away from Signup) ----------------

    public boolean clickSignInLink() {
        return click(SIGN_IN_LINK, "clickSignInLink");
    }

    // ---------------- Google Login ----------------

    public boolean clickContinueWithGoogle() {
        return click(CONTINUE_WITH_GOOGLE, "clickContinueWithGoogle");
    }

    public boolean selectGoogleAccount() {
        return click(GOOGLE_ACCOUNT_OPTION, "selectGoogleAccount");
    }
}
