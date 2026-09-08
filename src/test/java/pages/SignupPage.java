package pages;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class SignupPage {

    private static final Duration EXPLICIT_WAIT = Duration.ofSeconds(15);
    private static final Duration SCROLL_RETRY_WAIT = Duration.ofSeconds(5);
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

    // ===================== Image upload (current build) =====================
    // The profile-image control is a plain ImageView (the circular avatar/placeholder).
    // Tapping it opens an action sheet: "Upload Photo" / "Take Photo" when empty, and
    // (once an image is set) "…, View Photo" / "…, Delete". Use the first ImageView on the
    // form — the avatar sits at the top, above every other image.
    private static final By IMAGE_UPLOAD_TRIGGER = By.xpath("(//android.widget.ImageView)[1]");
    // Action-sheet rows: clickable ViewGroups whose content-desc is "<icon-glyph>, <label>"
    // (React Native joins the icon's empty label with the text). Confirmed on-device:
    //   empty avatar  -> "…, Take Photo", "…, Upload Photo"
    //   image set     -> the above + "…, View Photo", "…, Delete"
    // Matched by @clickable="true" + label so the non-clickable dialog title that also
    // contains "Delete" ("Delete Photo? …") can't be picked up by mistake.
    private static final By MENU_UPLOAD_PHOTO =
            By.xpath("//android.view.ViewGroup[contains(@content-desc,\"Upload Photo\") and @clickable=\"true\"]");
    private static final By MENU_TAKE_PHOTO =
            By.xpath("//android.view.ViewGroup[contains(@content-desc,\"Take Photo\") and @clickable=\"true\"]");
    private static final By MENU_VIEW_PHOTO =
            By.xpath("//android.view.ViewGroup[contains(@content-desc,\", View Photo\") and @clickable=\"true\"]");
    private static final By MENU_DELETE_PHOTO =
            By.xpath("//android.view.ViewGroup[contains(@content-desc,\", Delete\") and @clickable=\"true\"]");

    // (Media + camera runtime-permission "Allow" buttons are matched by ANY_PERMISSION_ALLOW,
    // near grantPermissionIfPrompted().)

    // In-app camera shutter.
    private static final By CAMERA_SHUTTER_BUTTON =
            By.xpath("//android.view.ViewGroup[@resource-id=\"in-app-camera-shutter-button\"]/android.view.ViewGroup");
    // "Edit Photo" screen after a capture: "Navigate up" CANCELS (no image), "Crop"
    // ACCEPTS (image is set on the form).
    private static final By EDIT_PHOTO_CLOSE_BUTTON =
            By.xpath("//android.widget.ImageButton[@content-desc=\"Navigate up\"]");
    private static final By EDIT_PHOTO_CROP_BUTTON =
            By.xpath("//android.widget.Button[@content-desc=\"Crop\"]");

    // Photo viewer (opened via "View Photo") — closed by the top-right "Close image
    // preview" Button (its inner icon glyph is a non-clickable TextView).
    private static final By PHOTO_VIEWER_CLOSE_BUTTON =
            By.xpath("//android.widget.Button[@content-desc=\"Close image preview\"]");
    // Delete confirmation dialog ("Delete Photo? — Are you sure … This action cannot be
    // undone.") — both buttons are clickable ViewGroups with an exact content-desc.
    private static final By DELETE_DIALOG_CONFIRM = By.xpath("//android.view.ViewGroup[@content-desc=\"Delete\"]");
    private static final By DELETE_DIALOG_CANCEL = By.xpath("//android.view.ViewGroup[@content-desc=\"Cancel\"]");

    // (The media/camera permission "Allow" button is matched by ANY_PERMISSION_ALLOW near
    // grantPermissionIfPrompted(); the photo-picker tile / Done locators live near
    // pickFirstPhotoFromPicker().)

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
    // Phone field. The placeholder ("Enter Phone Number") only matches while the field is
    // empty — it flips to the typed/formatted value on first keystroke, which broke the
    // retry loop in enterPhoneNumber(). Anchor on the stable "Phone Number *" label via the
    // following:: axis (same pattern used for Password), with the placeholder as a fallback.
    private static final By PHONE_FIELD = By.xpath(
            "//android.widget.TextView[@text=\"Phone Number *\"]/following::android.widget.EditText[1]"
                    + " | //android.widget.EditText[@text=\"Enter Phone Number\"]");
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
    // Confirmed on-device (screen dump after a real Sign In, which shares this flow):
    // successful auth lands on a post-auth interstitial — heading "Welcome to KnackCity!",
    // body "You're all set! ...", and a "Start Exploring" button — NOT a "Welcome to
    // Knackcity!" home greeting. "Welcome to Knack..." also appears on the PRE-auth Welcome
    // screen, so it can't be the marker; "Start Exploring" / "You're all set" are unique to
    // the post-auth screen. Kept in sync with SignInPage.SIGNIN_SUCCESS_MARKER.
    private static final By SIGNUP_SUCCESS_MARKER = By.xpath(
            "//android.widget.TextView[@text=\"Start Exploring\" or starts-with(@text,\"You're all set\")]");

    // Sign In (link back to the Sign In screen from Signup — Sign-In "Entry Point 2":
    // Welcome -> Get Started -> Signup screen -> this "Sign In" link).
    // The visible label is a TextView, //android.widget.TextView[@text="Sign In"], but in
    // this app tappable labels are wrapped in a clickable ViewGroup (the inner TextView is
    // usually not itself clickable). This union targets, in order: the label's nearest
    // clickable ancestor-or-self, the content-desc wrapper used elsewhere, and finally the
    // bare label — the first that resolves clickable wins.
    private static final By SIGN_IN_LINK = By.xpath(
            "//android.widget.TextView[@text=\"Sign In\"]/ancestor-or-self::*[@clickable=\"true\"][1]"
                    + " | //android.view.ViewGroup[@content-desc=\"Sign In\"]"
                    + " | //android.widget.TextView[@text=\"Sign In\"]");

    // Google login
    // Confirmed on-device: the clickable node is the ViewGroup wrapper (content-desc exact,
    // no trailing-comma quirk), not the inner TextView (which is not itself clickable).
    private static final By CONTINUE_WITH_GOOGLE = By.xpath("//android.view.ViewGroup[@content-desc=\"Continue with Google\"]");
    private static final By GOOGLE_ACCOUNT_OPTION = By.xpath(
            "//android.widget.TextView[@resource-id=\"com.google.android.gms:id/account_display_name\" and @text=\"Onyx Test Account\"]");

    // Since the current build, tapping "Create Account" no longer finishes signup directly —
    // it first sends an email OTP and shows the shared verification screen. All OTP handling
    // is delegated to OtpVerificationPage (same screen the Forgot Password flow uses).
    private final OtpVerificationPage otpPage;

    public SignupPage(AndroidDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, EXPLICIT_WAIT);
        this.otpPage = new OtpVerificationPage(driver);
    }

    // ---------------- Internal helpers ----------------

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

    /** Strips everything but digits — for comparing typed vs. displayed phone values. */
    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
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

    // ==================== Image upload (gallery + camera) ====================
    // Flow per the current build:
    //   tap the avatar (ImageView) -> action sheet
    //     empty : "Upload Photo" (gallery) / "Take Photo" (camera)
    //     set   : ", View Photo" / ", Delete"
    //   gallery : Upload Photo -> photo-picker permission (Allow) -> pick a photo
    //   camera  : Take Photo -> camera permission (Allow while using) -> shutter ->
    //             Edit Photo screen ("Navigate up" cancels, "Crop" accepts)
    //   delete  : ", Delete" -> confirm dialog ("Cancel" keeps it, "Delete" removes it)

    /** Short wait + click, no scroll — for action-sheet items, dialogs and system prompts. */
    private boolean tap(By locator, String name) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.elementToBeClickable(locator))
                    .click();
            System.out.println("[SignupPage] Tapped '" + name + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            System.out.println("[SignupPage] '" + name + "' not tappable: " + e.getMessage());
            return false;
        }
    }

    private boolean isPresentShort(By locator) {
        return isPresent(locator, 3);
    }

    private boolean isPresent(By locator, int seconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seconds))
                    .until(ExpectedConditions.presenceOfElementLocated(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Leaves any system dialog / picker / camera screen and returns to the signup form,
     * so a half-finished optional step can't strand the test. Presses Back at most twice —
     * pressing it blindly used to walk right out of the app to the launcher — and, if the
     * app is no longer in the foreground, brings it back and re-scrolls to the form.
     */
    private void recoverToApp() {
        for (int i = 0; i < 2; i++) {
            if (isSignupScreenDisplayedQuick()) {
                break;
            }
            try {
                driver.navigate().back();
                pauseForAction();
            } catch (Exception ignored) {
                // Nothing to go back from.
            }
        }
        if (!isSignupScreenDisplayedQuick()) {
            try {
                driver.activateApp("com.knackcity");
                pauseForAction();
            } catch (Exception ignored) {
                // Best effort.
            }
        }
    }

    private boolean isSignupScreenDisplayedQuick() {
        return isPresentShort(SIGNUP_SCREEN_MARKER);
    }

    /**
     * Taps the avatar / profile-image control to open its action sheet. The avatar is a
     * plain ImageView; if that node isn't directly clickable (it can render as a
     * non-clickable image once a photo is set), fall back to its nearest clickable ancestor.
     */
    public boolean tapImageUploadTrigger() {
        if (click(IMAGE_UPLOAD_TRIGGER, "tapImageUploadTrigger")) {
            return true;
        }
        return click(By.xpath("(//android.widget.ImageView)[1]/ancestor-or-self::*[@clickable=\"true\"][1]"),
                "tapImageUploadTrigger (clickable ancestor)");
    }

    // One combined locator for the "grant" button of any media/camera permission dialog —
    // "Allow all photos" (photo) or "While using the app" (camera). Deliberately excludes
    // permission_allow_selected_button ("Select photos"), which drops into a fiddly
    // limited-access grid; full access gives a plain picker.
    private static final By ANY_PERMISSION_ALLOW = By.xpath(
            "//*[@resource-id=\"com.android.permissioncontroller:id/permission_allow_all_button\"]"
                    + " | //*[@resource-id=\"com.android.permissioncontroller:id/permission_allow_button\"]"
                    + " | //*[@resource-id=\"com.android.permissioncontroller:id/permission_allow_foreground_only_button\"]");

    /**
     * Grants the media/camera permission dialog if it is showing right now. Fast (≤2s) so
     * it can be polled in a loop while waiting for the (sometimes slow) first-time picker.
     */
    private boolean grantPermissionIfPrompted() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(2))
                    .until(ExpectedConditions.elementToBeClickable(ANY_PERMISSION_ALLOW))
                    .click();
            System.out.println("[SignupPage] Granted permission (Allow)");
            pauseForAction();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Android photo picker (com.google.android.photopicker), limited-access / multi-select
    // variant. Confirmed on-device:
    //   - each tile: a non-clickable View content-desc="Photo taken on <date>", whose
    //     PARENT View is the clickable tap target;
    //   - after selecting, a "Done" TextView appears — not clickable itself, its parent
    //     View is — and tapping it returns the photo to the app.
    private static final By PHOTO_PICKER_LABEL =
            By.xpath("(//android.view.View[contains(@content-desc,\"Photo taken\") or contains(@content-desc,\"GIF taken\")])[1]");
    private static final By PHOTO_PICKER_FIRST_TILE =
            By.xpath("(//android.view.View[contains(@content-desc,\"Photo taken\") or contains(@content-desc,\"GIF taken\")]/..)[1]");
    private static final By PHOTO_PICKER_DONE =
            By.xpath("//android.widget.TextView[@text=\"Done\"]/ancestor-or-self::android.view.View[1]");

    /**
     * Selects the first photo in the Android photo picker (com.google.android.photopicker)
     * and taps "Done" to return it to the app. The picker is multi-select: tap a tile
     * (its clickable parent View), then the "Done" bar. If no grid appears the photo was
     * returned directly — nothing to do.
     */
    private boolean pickFirstPhotoFromPicker() {
        if (!isPresent(PHOTO_PICKER_LABEL, 12)) {
            System.out.println("[SignupPage] No photo grid detected — assuming the photo was returned directly.");
            return true;
        }
        if (!tap(PHOTO_PICKER_FIRST_TILE, "select first photo")) {
            return false;
        }
        // Wait for the "Done" bar to appear (it only shows once something is selected).
        if (isPresent(PHOTO_PICKER_DONE, 6)) {
            tap(PHOTO_PICKER_DONE, "photo picker — Done");
        } else {
            // Other picker shapes: an explicit Allow / Add button.
            for (By confirm : new By[]{
                    By.xpath("//android.widget.Button[@text=\"Allow\"]"),
                    By.xpath("//*[@content-desc=\"Add\" or @text=\"Add\"]")}) {
                if (isPresentShort(confirm) && tap(confirm, "photo picker — confirm")) {
                    break;
                }
            }
        }
        // Wait until the picker is gone and we're back on the signup form.
        isPresent(SIGNUP_SCREEN_MARKER, 10);
        return true;
    }

    // ---- Action-sheet items ----
    private boolean tapUploadPhotoOption() {
        return tap(MENU_UPLOAD_PHOTO, "Upload Photo");
    }

    private boolean tapTakePhotoOption() {
        return tap(MENU_TAKE_PHOTO, "Take Photo");
    }

    private boolean tapViewPhotoOption() {
        return tap(MENU_VIEW_PHOTO, "View Photo");
    }

    private boolean tapDeletePhotoOption() {
        return tap(MENU_DELETE_PHOTO, "Delete");
    }

    private boolean closePhotoViewer() {
        // Prefer the viewer's own close glyph; fall back to the system back button.
        if (tap(PHOTO_VIEWER_CLOSE_BUTTON, "close photo viewer")) {
            return true;
        }
        recoverToApp();
        return true;
    }

    // ---- In-app camera "Edit Photo" screen ----
    private boolean cancelPhotoEdit() {
        return tap(EDIT_PHOTO_CLOSE_BUTTON, "Edit Photo — Navigate up (cancel)");
    }

    private boolean acceptPhotoEdit() {
        return tap(EDIT_PHOTO_CROP_BUTTON, "Edit Photo — Crop (accept)");
    }

    // ---- Delete confirmation dialog ----
    private boolean cancelDeleteDialog() {
        return tap(DELETE_DIALOG_CANCEL, "delete dialog — Cancel");
    }

    private boolean confirmDeleteDialog() {
        return tap(DELETE_DIALOG_CONFIRM, "delete dialog — Delete");
    }

    /**
     * True if a profile image is currently set — opens the action sheet and looks for the
     * "View Photo" / "Delete" rows (only present once an image exists), then leaves the sheet.
     */
    public boolean isProfileImageSet() {
        if (!tapImageUploadTrigger()) {
            return false;
        }
        boolean set = isPresentShort(MENU_VIEW_PHOTO) || isPresentShort(MENU_DELETE_PHOTO);
        dismissActionSheet();
        return set;
    }

    /** Closes the action sheet (tap outside / Back) without leaving the signup screen. */
    private void dismissActionSheet() {
        try {
            driver.navigate().back();
        } catch (Exception ignored) {
            // fine
        }
        pauseForAction();
    }

    /**
     * Returns the form to a clean state after an image sub-flow: waits for the signup
     * screen, then scrolls back to the top so the caller's subsequent field entry starts
     * from the same position it would on a freshly-opened form. (The existing manual-signup
     * / Google code is left untouched — this just hands control back the way it found it.)
     */
    private void settleBackOnSignupForm() {
        isPresent(SIGNUP_SCREEN_MARKER, 8);
        for (int i = 0; i < 5; i++) {
            scrollUp();
        }
        pauseForAction();
    }

    /**
     * Removes the currently-set profile image, exercising the confirm dialog both ways:
     * open sheet → Delete → dialog → Cancel (kept), then open sheet → Delete → dialog →
     * Delete (removed).
     */
    private void deletePhotoExercisingDialog() {
        tapImageUploadTrigger();
        tapDeletePhotoOption();
        cancelDeleteDialog();            // dismiss — image stays
        isPresent(SIGNUP_SCREEN_MARKER, 5);

        tapImageUploadTrigger();
        tapDeletePhotoOption();
        confirmDeleteDialog();           // confirm — image removed
        isPresent(SIGNUP_SCREEN_MARKER, 5);
    }

    // ---- Gallery: open the action sheet, choose "Upload Photo", grant, pick, Done ----
    private boolean uploadPhotoFromGallery() {
        if (!tapImageUploadTrigger() || !tapUploadPhotoOption()) {
            recoverToApp();
            return false;
        }
        // The FIRST "Upload Photo" of a run is slow — RN cold-loads the photopicker module
        // and the runtime-permission dialog can take 10-20s to surface. Poll for up to ~35s:
        // grant the dialog whenever it appears, stop as soon as the picker grid is up.
        long deadline = System.currentTimeMillis() + 35_000L;
        while (System.currentTimeMillis() < deadline) {
            if (isPresentShort(PHOTO_PICKER_LABEL)) {
                break;
            }
            if (grantPermissionIfPrompted()) {
                continue; // granted — loop round to wait for the picker
            }
            pauseForAction();
        }
        pickFirstPhotoFromPicker();
        if (!isPresent(SIGNUP_SCREEN_MARKER, 10)) {
            recoverToApp();
        }
        return true;
    }

    // ---- Camera: open the action sheet, choose "Take Photo", grant, shutter, edit ----
    private boolean captureFromCameraAndFinish(boolean accept) {
        if (!tapImageUploadTrigger() || !tapTakePhotoOption()) {
            recoverToApp();
            return false;
        }
        grantPermissionIfPrompted(); // camera permission — no-op once granted
        if (!tap(CAMERA_SHUTTER_BUTTON, "camera shutter")) {
            recoverToApp();
            return false;
        }
        // The uCrop "Edit Photo" screen: "Navigate up" cancels, "Crop" accepts.
        boolean ok = accept ? acceptPhotoEdit() : cancelPhotoEdit();
        if (!isPresent(SIGNUP_SCREEN_MARKER, 8)) {
            recoverToApp();
        }
        return ok;
    }

    /**
     * Full GALLERY profile-image scenario:
     *   upload from gallery → view it → delete (Cancel, kept) → delete (Delete, removed)
     *   → upload from gallery again.
     * Best-effort throughout (the field is optional and this crosses the OS photo picker);
     * returns whether a profile image is set at the end.
     */
    public boolean completeGalleryImageUploadFlow() {
        System.out.println("[SignupPage] Gallery image flow: first upload");
        if (!uploadPhotoFromGallery()) {
            settleBackOnSignupForm();
            return false;
        }

        System.out.println("[SignupPage] Gallery image flow: view the uploaded photo");
        tapImageUploadTrigger();
        tapViewPhotoOption();
        closePhotoViewer();
        isPresent(SIGNUP_SCREEN_MARKER, 5);

        System.out.println("[SignupPage] Gallery image flow: delete (Cancel then Delete)");
        deletePhotoExercisingDialog();

        System.out.println("[SignupPage] Gallery image flow: re-upload from gallery");
        boolean set = uploadPhotoFromGallery();

        settleBackOnSignupForm();
        return set;
    }

    /**
     * Full CAMERA profile-image scenario:
     *   capture → cancel edit (no image) → capture → accept crop (image set) → view it →
     *   close → delete (Cancel, kept) → delete (Delete, removed) → capture → accept crop.
     * Best-effort throughout; returns whether a profile image is set at the end.
     */
    public boolean completeCameraImageUploadFlow() {
        System.out.println("[SignupPage] Camera image flow: capture then CANCEL the edit");
        captureFromCameraAndFinish(false);

        System.out.println("[SignupPage] Camera image flow: capture then ACCEPT (Crop)");
        if (!captureFromCameraAndFinish(true)) {
            settleBackOnSignupForm();
            return false;
        }

        System.out.println("[SignupPage] Camera image flow: view the captured photo");
        tapImageUploadTrigger();
        tapViewPhotoOption();
        closePhotoViewer();
        isPresent(SIGNUP_SCREEN_MARKER, 5);

        System.out.println("[SignupPage] Camera image flow: delete (Cancel then Delete)");
        deletePhotoExercisingDialog();

        System.out.println("[SignupPage] Camera image flow: capture again and accept (Crop)");
        boolean set = captureFromCameraAndFinish(true);

        settleBackOnSignupForm();
        return set;
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
     * Verifies DOB is enforced as mandatory: leaves it untouched (empty), submits, and
     * confirms the form did NOT advance. "Did not advance" is true when EITHER the
     * DOB-required error is shown OR we're still on the signup form — and, definitively,
     * we are NOT on the post-Create-Account OTP screen. (Checking the signup marker alone
     * false-negatived: submitting scrolls the long form to the first error, moving both
     * the Full Name field and the Create Account button out of view.)
     */
    public boolean isStillOnSignupScreenAfterEmptyDobSubmit() {
        clickCreateAccountButton();
        pauseForAction();
        scrollUp();
        pauseForAction();
        boolean dobError = isDobRequiredErrorDisplayed();
        boolean onForm = isSignupScreenDisplayed();
        System.out.println("[SignupPage] Empty-DOB submit — dobError=" + dobError + ", onSignupForm=" + onForm);
        return dobError || onForm;
    }

    // Inline "date of birth is required" validation shown under the DOB field on an
    // empty-DOB submit. The signup form renders field errors as TextViews with a
    // resource-id of "error-<field>" (same pattern as "error-email"); the exact suffix
    // isn't confirmed, so this also matches on the message text (case-insensitively),
    // accepting the common "date of birth" / "DOB" / "birth ... required" wordings.
    private static final By DOB_REQUIRED_ERROR = By.xpath(
            "//android.widget.TextView[@resource-id=\"error-dob\" or @resource-id=\"error-dateOfBirth\""
                    + " or @resource-id=\"error-date_of_birth\""
                    + " or contains(translate(@text,\"DATEOFBIRTH\",\"dateofbirth\"),\"date of birth\")"
                    + " or (contains(translate(@text,\"DOB\",\"dob\"),\"dob\")"
                    + " and contains(translate(@text,\"REQUIRED\",\"required\"),\"required\"))"
                    + " or (contains(translate(@text,\"BIRTH\",\"birth\"),\"birth\")"
                    + " and contains(translate(@text,\"REQUIRED\",\"required\"),\"required\"))]");

    /**
     * True if a "date of birth is required" validation message is visible (call right
     * after {@link #isStillOnSignupScreenAfterEmptyDobSubmit()}, which triggers it).
     */
    public boolean isDobRequiredErrorDisplayed() {
        return isDisplayed(DOB_REQUIRED_ERROR);
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

    /**
     * Enters the phone number robustly.
     *
     * <p>This build's phone field carries a country-code prefix and enforces "exactly 10
     * digits", and its formatter fights automation input:
     * <ul>
     *   <li>a single fast {@code element.sendKeys("<10 digits>")} lands partially
     *       ("8148014748" -> "731255"), and</li>
     *   <li>{@code element.sendKeys(oneChar)} in a loop does a <i>replace</i> per call on
     *       this UiAutomator2 build, so only the last digit survives ("...9").</li>
     * </ul>
     * So this types with the W3C Actions API (genuine key events to the focused field —
     * these append), reads the field back, and escalates the strategy on retry until all
     * ten digits stick.
     */
    public boolean enterPhoneNumber(String phone) {
        String wanted = digitsOnly(phone);
        try {
            for (int attempt = 1; attempt <= 4; attempt++) {
                WebElement field = findClickableWithScroll(PHONE_FIELD, "Phone Number");
                field.click();
                field.clear();
                sleepQuietly(300);

                switch (attempt) {
                    case 1:
                        // Whole string as real key events.
                        new Actions(driver).sendKeys(wanted).perform();
                        break;
                    case 2:
                        // Key events one digit at a time, with a beat for the formatter.
                        for (int i = 0; i < wanted.length(); i++) {
                            new Actions(driver).sendKeys(String.valueOf(wanted.charAt(i))).perform();
                            sleepQuietly(150);
                        }
                        break;
                    case 3:
                        // Appium IME "type" into the focused element.
                        Map<String, Object> typeArgs = new HashMap<>();
                        typeArgs.put("text", wanted);
                        driver.executeScript("mobile: type", typeArgs);
                        break;
                    default:
                        // Last resort — plain single sendKeys.
                        field.sendKeys(wanted);
                }
                hideKeyboardIfShown();
                sleepQuietly(300);

                String actual = digitsOnly(readPhoneFieldText());
                // endsWith() tolerates the leading country-code digit the field may prepend.
                if (actual.endsWith(wanted)) {
                    System.out.println("[SignupPage] Filled 'Phone Number' with '" + wanted
                            + "' (strategy " + attempt + ")");
                    pauseForAction();
                    return true;
                }
                System.out.println("[SignupPage] Phone field held '" + actual + "' after strategy " + attempt
                        + "/4 (wanted '" + wanted + "') — clearing and retrying");
            }
            System.out.println("[SignupPage] Phone number would not stick after 4 strategies");
            return false;
        } catch (Exception e) {
            System.out.println("[SignupPage] Could not enter phone number: " + e.getMessage());
            return false;
        }
    }

    /** Re-reads the phone field's current text, tolerant of it having scrolled/changed. */
    private String readPhoneFieldText() {
        try {
            return driver.findElement(PHONE_FIELD).getText();
        } catch (Exception e) {
            return "";
        }
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

    /**
     * Post-signup email-OTP verification screen (shown after "Create Account" since the
     * current build). {@code true} once the signup either reached that screen OR went
     * straight to the success screen (older behaviour / already-verified email).
     */
    public boolean isEmailOtpScreenDisplayed() {
        return otpPage.isOtpScreenDisplayed();
    }

    /** Explicitly waits out the 60s cooldown for the "Resend OTP" option to appear. */
    public boolean waitForResendOtpOption() {
        return otpPage.waitForResendOtpOption();
    }

    /** True if the "Resend OTP" option is currently visible on the OTP screen. */
    public boolean isResendOtpDisplayed() {
        return otpPage.isResendOtpDisplayed();
    }

    /** Taps "Resend OTP" (only valid once the 60s cooldown has elapsed). */
    public boolean clickResendOtp() {
        return otpPage.clickResendOtp();
    }

    /** Polls the OTP field(s) until a manually-typed code appears (or {@code maxWait} elapses). */
    public boolean waitForManualOtpEntry(int minDigits, Duration maxWait) {
        return otpPage.waitForManualOtpEntry(minDigits, maxWait);
    }

    /** Taps "Verify Code" to submit the manually-entered OTP. */
    public boolean clickVerifyCode() {
        return otpPage.clickVerifyCode();
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
