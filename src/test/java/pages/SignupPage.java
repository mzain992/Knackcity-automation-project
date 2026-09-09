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
import java.util.function.BooleanSupplier;

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

    // ============= Profile image: gallery + camera (current build) =============
    // "Upload Media" = tap the circular avatar / profile-image control to open the action
    // sheet. Sheet contents:
    //   empty avatar : "Upload Photo" (gallery) / "Take Photo" (in-app camera)
    //   image set    : the above + ", View Photo" / ", Delete"
    // Locators below are exactly as captured on-device for this build.

    // The avatar control that opens the action sheet — first ImageView on the form (the
    // avatar sits above every other image).
    private static final By IMAGE_UPLOAD_TRIGGER = By.xpath("(//android.widget.ImageView)[1]");

    // Action-sheet items — empty avatar. Plain TextViews with the option label.
    private static final By MENU_UPLOAD_PHOTO = By.xpath("//android.widget.TextView[@text=\"Upload Photo\"]");
    private static final By MENU_TAKE_PHOTO = By.xpath("//android.widget.TextView[@text=\"Take Photo\"]");
    // Action-sheet items — image already set. Confirmed on-device: the clickable node is a
    // ViewGroup whose content-desc is "<icon-glyph>, <label>" (React Native joins the icon's
    // empty label with the text), so an exact "@content-desc=', View Photo'" match misses —
    // anchor with contains() on ", <label>" (the leading comma-space keeps ", Delete" from
    // matching the delete-dialog's exact "Delete" button). TextView fallback for safety.
    private static final By MENU_VIEW_PHOTO = By.xpath(
            "//android.view.ViewGroup[contains(@content-desc,\", View Photo\")]"
                    + " | //android.widget.TextView[@text=\"View Photo\"]");
    private static final By MENU_DELETE_PHOTO = By.xpath(
            "//android.view.ViewGroup[contains(@content-desc,\", Delete\")]"
                    + " | //android.widget.TextView[@text=\"Delete\" and not(ancestor::*[contains(@content-desc,\"Delete Photo\")])]");

    // Runtime-permission dialog buttons (com.android.permissioncontroller):
    //   gallery : "Allow Selected"  (limited photo access)
    //   camera  : "Allow only while using the app"  (foreground only)
    private static final By PERMISSION_ALLOW_SELECTED = By.xpath(
            "//android.widget.Button[@resource-id=\"com.android.permissioncontroller:id/permission_allow_selected_button\"]");
    private static final By PERMISSION_ALLOW_FOREGROUND_ONLY = By.xpath(
            "//android.widget.Button[@resource-id=\"com.android.permissioncontroller:id/permission_allow_foreground_only_button\"]");

    // Android photo picker (com.google.android.photopicker), multi-select "Media grid".
    // Confirmed on-device: the grid container ("Media grid") renders BEFORE its tiles do
    // (Compose lazy grid), so waits must anchor on an actual tile, not the container.
    // First selectable tile. The spec's fixed positional path
    // (Media grid/View/View[2]/View[2]/View) does not resolve consistently against the
    // lazy grid, so prefer "first clickable View inside Media grid", then a "Photo taken"
    // tile's clickable parent, then the spec path as a last resort.
    private static final By GALLERY_FIRST_IMAGE = By.xpath(
            "(//android.view.View[@content-desc=\"Media grid\"]//android.view.View[@clickable=\"true\"])[1]"
                    + " | (//android.view.View[contains(@content-desc,\"Photo taken\")]/parent::android.view.View)[1]"
                    + " | //android.view.View[@content-desc=\"Media grid\"]/android.view.View/android.view.View[2]/android.view.View[2]/android.view.View");
    // Any tile — used to know the grid has actually populated.
    private static final By GALLERY_ANY_TILE = By.xpath(
            "//android.view.View[@content-desc=\"Media grid\"]//android.view.View[@clickable=\"true\"]"
                    + " | //android.view.View[contains(@content-desc,\"Photo taken\")]");
    // "Done" bar button — appears only AFTER at least one tile is selected. The visible
    // node is a non-clickable TextView; tapping it works via its clickable ancestor.
    private static final By GALLERY_DONE_BUTTON = By.xpath(
            "//android.widget.TextView[@text=\"Done\"]/ancestor-or-self::*[@clickable=\"true\"][1]"
                    + " | //android.widget.TextView[@text=\"Done\"]"
                    + " | //android.widget.Button[@text=\"Done\"]"
                    + " | //*[@content-desc=\"Done\"]");

    // In-app camera shutter.
    private static final By CAMERA_SHUTTER_BUTTON = By.xpath(
            "//android.view.ViewGroup[@resource-id=\"in-app-camera-shutter-button\"]/android.view.ViewGroup");
    // "Edit Photo" screen after a capture: "Navigate up" CANCELS (no image is applied),
    // "Crop" ACCEPTS (the cropped image is set on the form).
    private static final By EDIT_PHOTO_CLOSE_BUTTON = By.xpath("//android.widget.ImageButton[@content-desc=\"Navigate up\"]");
    private static final By EDIT_PHOTO_CROP_BUTTON = By.xpath("//android.widget.Button[@content-desc=\"Crop\"]");

    // Full-screen photo viewer (opened via ", View Photo") — closed by the top-right glyph.
    // The spec's bare //android.widget.TextView[@text=""] glyph is not itself clickable, so
    // this also targets its clickable ancestor and the labelled close Button.
    private static final By PHOTO_VIEWER_CLOSE_BUTTON = By.xpath(
            "//android.widget.Button[@content-desc=\"Close image preview\"]"
                    + " | //android.widget.TextView[@text=\"\"]/ancestor-or-self::*[@clickable=\"true\"][1]"
                    + " | //android.widget.TextView[@text=\"\"]");

    // Delete-confirmation dialog ("Delete Photo?"): "Cancel" keeps the image, "Delete"
    // removes it. Both are clickable ViewGroups with an exact content-desc.
    private static final By DELETE_DIALOG_CANCEL = By.xpath("//android.view.ViewGroup[@content-desc=\"Cancel\"]");
    private static final By DELETE_DIALOG_CONFIRM = By.xpath("//android.view.ViewGroup[@content-desc=\"Delete\"]");

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
    // BUG FIX: the current build reworded this from "I agree to the Terms & Conditions" to
    // "I have read and agree to the Terms & Conditions". The node is a single non-clickable
    // TextView (enabled=true, so elementToBeClickable still resolves it and click() does a
    // coordinate tap on it). Match on the "Terms & Conditions" tail so a future minor
    // reword of the leading copy doesn't break it again; keep the old exact text as a
    // fallback.
    private static final By TERMS_AND_CONDITIONS_LINK = By.xpath(
            "//android.widget.TextView[contains(@text,\"agree to the Terms & Conditions\")]"
                    + " | //android.widget.TextView[@text=\"I agree to the Terms & Conditions\"]");
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

    /**
     * Runs one numbered step of an image sub-flow and logs
     * {@code [<FLOW> STEP n] <description> -> PASS|FAIL} (with the error text on failure).
     * Never throws: the profile image is an optional signup field, so a failed sub-step is
     * recorded and the flow continues — the caller derives the overall result from the
     * final on-form state.
     */
    private boolean runStep(String flow, int number, String description, BooleanSupplier action) {
        System.out.println("[SignupPage] [" + flow + " STEP " + number + "] " + description);
        boolean ok;
        try {
            ok = action.getAsBoolean();
        } catch (Exception e) {
            System.out.println("[SignupPage] [" + flow + " STEP " + number + "] -> FAIL (" + e.getMessage() + ")");
            return false;
        }
        System.out.println("[SignupPage] [" + flow + " STEP " + number + "] -> " + (ok ? "PASS" : "FAIL"));
        return ok;
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

    /** Taps a system dialog button within a short window; {@code false} if it isn't showing. */
    private boolean tapSystemButton(By locator, String name) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(3))
                    .until(ExpectedConditions.elementToBeClickable(locator))
                    .click();
            System.out.println("[SignupPage] Tapped '" + name + "'");
            pauseForAction();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Grants the gallery photo-permission dialog ("Allow Selected") if it is showing. */
    private boolean grantGalleryPermissionIfPrompted() {
        return tapSystemButton(PERMISSION_ALLOW_SELECTED, "permission — Allow Selected");
    }

    /** Grants the camera permission dialog ("Allow only while using the app") if it is showing. */
    private boolean grantCameraPermissionIfPrompted() {
        return tapSystemButton(PERMISSION_ALLOW_FOREGROUND_ONLY, "permission — Allow only while using the app");
    }

    /**
     * Waits for the photo picker to be usable — i.e. an actual TILE has rendered, not just
     * the "Media grid" container (which appears first, empty). Grants the "Allow Selected"
     * permission dialog if it surfaces. The first gallery open of a run is slow (RN
     * cold-loads the picker module), so this polls for up to ~40s.
     */
    private void waitForGalleryGrid() {
        long deadline = System.currentTimeMillis() + 40_000L;
        while (System.currentTimeMillis() < deadline) {
            if (isPresentShort(GALLERY_ANY_TILE)) {
                return;
            }
            if (grantGalleryPermissionIfPrompted()) {
                continue;
            }
            pauseForAction();
        }
        System.out.println("[SignupPage] gallery picker tiles did not render within 40s");
    }

    /** Waits until the signup form is back in the foreground; recovers the app if not. */
    private boolean backOnForm() {
        if (isPresent(SIGNUP_SCREEN_MARKER, 8)) {
            return true;
        }
        recoverToApp();
        return isSignupScreenDisplayedQuick();
    }

    /**
     * Waits for the grid, taps the first tile, and confirms "Done" has appeared (it is
     * drawn only once a selection registers — a stray earlier tap can toggle it back off,
     * so retry the tile once). Returns whether "Done" is now showing.
     */
    private boolean selectFirstGalleryImage() {
        waitForGalleryGrid();
        if (!tap(GALLERY_FIRST_IMAGE, "gallery — first image")) {
            return false;
        }
        if (isPresent(GALLERY_DONE_BUTTON, 4)) {
            return true;
        }
        tap(GALLERY_FIRST_IMAGE, "gallery — first image (retry)");
        return isPresent(GALLERY_DONE_BUTTON, 4);
    }

    /** Taps the picker's "Done" bar button and waits for the signup form to come back. */
    private boolean tapGalleryDone() {
        boolean ok = tap(GALLERY_DONE_BUTTON, "gallery — Done");
        if (!isPresent(SIGNUP_SCREEN_MARKER, 12)) {
            recoverToApp();
        }
        return ok;
    }

    /** Full pick: wait for grid -> select first tile -> "Done" -> back on the signup form. */
    private boolean selectFirstGalleryImageAndConfirm() {
        if (!selectFirstGalleryImage()) {
            recoverToApp();
            return false;
        }
        return tapGalleryDone();
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

    /**
     * True if NO profile image is set — opens the action sheet and confirms "Upload Photo"
     * is present while ", View Photo" is not, then leaves the sheet.
     */
    public boolean isProfileImageUnset() {
        if (!tapImageUploadTrigger()) {
            return false;
        }
        boolean unset = isPresentShort(MENU_UPLOAD_PHOTO) && !isPresentShort(MENU_VIEW_PHOTO);
        dismissActionSheet();
        return unset;
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
     * One full gallery pick: action sheet -> "Upload Photo" -> grant "Allow Selected" ->
     * wait for the "Media grid" -> first image -> "Done" -> back on the signup form.
     * Returns whether a profile image is set afterwards.
     */
    private boolean uploadImageFromGallery() {
        if (!tapImageUploadTrigger() || !tapUploadPhotoOption()) {
            recoverToApp();
            return false;
        }
        grantGalleryPermissionIfPrompted();
        selectFirstGalleryImageAndConfirm();
        return isProfileImageSet();
    }

    /**
     * One full camera capture: action sheet -> "Take Photo" -> grant "Allow only while
     * using the app" -> shutter -> "Edit Photo" ({@code accept} ? "Crop" : "Navigate up")
     * -> back on the signup form. Returns whether the sub-flow's taps all succeeded.
     */
    private boolean captureImageFromCamera(boolean accept) {
        if (!tapImageUploadTrigger() || !tapTakePhotoOption()) {
            recoverToApp();
            return false;
        }
        grantCameraPermissionIfPrompted();
        if (!tap(CAMERA_SHUTTER_BUTTON, "camera shutter")) {
            recoverToApp();
            return false;
        }
        boolean finished = accept ? acceptPhotoEdit() : cancelPhotoEdit();
        backOnForm();
        return finished;
    }

    /** Camera re-capture used by the final step: capture -> "Crop" -> verify an image is set. */
    private boolean recaptureImageFromCamera() {
        return captureImageFromCamera(true) && isProfileImageSet();
    }

    /**
     * TEST CASE 1 — Upload Image from Gallery.
     *
     * <p>Runs the numbered scenario end to end, logging {@code [GALLERY STEP n] … -> PASS/FAIL}
     * for every step. Best-effort throughout (the profile image is an optional field and the
     * flow crosses the OS photo picker); returns whether a profile image is set at the end.
     */
    public boolean completeGalleryImageUploadFlow() {
        final String flow = "GALLERY";

        boolean uploaded = runStep(flow, 1, "Open the action sheet and tap 'Upload Photo'",
                () -> tapImageUploadTrigger() && tapUploadPhotoOption());
        runStep(flow, 2, "Handle the permission dialog if shown ('Allow Selected')",
                () -> {
                    grantGalleryPermissionIfPrompted(); // no-op when the permission is pre-granted
                    return true;
                });
        runStep(flow, 3, "Select the first image from the gallery 'Media grid'",
                this::selectFirstGalleryImage);
        runStep(flow, 4, "Tap 'Done' to confirm the selection",
                this::tapGalleryDone);
        boolean imageSet = runStep(flow, 5, "Verify the image is set (', View Photo' shown in the sheet)",
                this::isProfileImageSet);

        runStep(flow, 6, "Open the sheet and tap ', Delete'",
                () -> tapImageUploadTrigger() && tapDeletePhotoOption());
        boolean deleteDialog = runStep(flow, 7, "Verify the delete-confirmation dialog appears",
                () -> isPresent(DELETE_DIALOG_CANCEL, 6));
        runStep(flow, 8, "Tap 'Cancel' — the image is kept",
                () -> {
                    boolean ok = cancelDeleteDialog();
                    backOnForm();
                    return ok;
                });

        runStep(flow, 9, "Open the sheet, tap ', Delete', then confirm with 'Delete'",
                () -> tapImageUploadTrigger()
                        && tapDeletePhotoOption()
                        && isPresent(DELETE_DIALOG_CONFIRM, 6)
                        && confirmDeleteDialog());
        backOnForm();
        boolean removed = runStep(flow, 10, "Verify the image was removed",
                this::isProfileImageUnset);

        boolean reUploaded = runStep(flow, 11, "Upload a new image from the gallery and verify success",
                this::uploadImageFromGallery);

        settleBackOnSignupForm();

        boolean pass = uploaded && imageSet && deleteDialog && removed && reUploaded;
        System.out.println("[SignupPage] [GALLERY] flow result: " + (pass ? "PASS" : "PARTIAL")
                + " — step1Upload=" + uploaded + ", step5ImageSet=" + imageSet
                + ", step7DeleteDialog=" + deleteDialog + ", step10Removed=" + removed
                + ", step11ReUploaded=" + reUploaded);
        return reUploaded;
    }

    /**
     * TEST CASE 2 — Capture Image from Camera.
     *
     * <p>Runs the numbered scenario end to end, logging {@code [CAMERA STEP n] … -> PASS/FAIL}
     * for every step. Best-effort throughout; returns whether a profile image is set at the end.
     */
    public boolean completeCameraImageUploadFlow() {
        final String flow = "CAMERA";

        runStep(flow, 1, "Open the sheet and tap 'Take Photo'",
                () -> tapImageUploadTrigger() && tapTakePhotoOption());
        runStep(flow, 2, "Handle the permission dialog if shown ('Allow only while using the app')",
                () -> {
                    grantCameraPermissionIfPrompted(); // no-op when the permission is pre-granted
                    return true;
                });
        runStep(flow, 3, "Capture an image with the in-app camera shutter",
                () -> tap(CAMERA_SHUTTER_BUTTON, "camera shutter"));
        runStep(flow, 4, "On 'Edit Photo', tap 'Navigate up' to close without saving",
                () -> {
                    boolean ok = cancelPhotoEdit();
                    backOnForm();
                    return ok;
                });

        runStep(flow, 5, "Re-open the sheet, tap 'Take Photo' and re-capture",
                () -> {
                    if (!tapImageUploadTrigger() || !tapTakePhotoOption()) {
                        return false;
                    }
                    grantCameraPermissionIfPrompted();
                    return tap(CAMERA_SHUTTER_BUTTON, "camera shutter");
                });
        runStep(flow, 6, "Select the 'Crop' option to accept the captured photo",
                this::acceptPhotoEdit);
        runStep(flow, 7, "Return to the signup form with the cropped photo applied",
                this::backOnForm);

        boolean imageSet = runStep(flow, 8, "Verify the photo is set (', View Photo' shown) and open the viewer",
                () -> tapImageUploadTrigger() && tapViewPhotoOption());
        runStep(flow, 9, "Close the full-screen photo viewer",
                this::closePhotoViewer);
        backOnForm();

        runStep(flow, 10, "Open the sheet and tap ', Delete'",
                () -> tapImageUploadTrigger() && tapDeletePhotoOption());
        boolean deleteDialog = runStep(flow, 11, "Confirm the delete dialog appears, then tap 'Cancel'",
                () -> isPresent(DELETE_DIALOG_CANCEL, 6) && cancelDeleteDialog());
        backOnForm();

        boolean deleted = runStep(flow, 12, "Open the sheet, tap ', Delete', confirm with 'Delete', and verify removal",
                () -> {
                    boolean ok = tapImageUploadTrigger()
                            && tapDeletePhotoOption()
                            && isPresent(DELETE_DIALOG_CONFIRM, 6)
                            && confirmDeleteDialog();
                    backOnForm();
                    return ok && isProfileImageUnset();
                });

        boolean recaptured = runStep(flow, 13, "Re-capture from the camera, accept with 'Crop', and verify success",
                this::recaptureImageFromCamera);

        settleBackOnSignupForm();

        boolean pass = imageSet && deleteDialog && deleted && recaptured;
        System.out.println("[SignupPage] [CAMERA] flow result: " + (pass ? "PASS" : "PARTIAL")
                + " — step8ImageSet=" + imageSet + ", step11DeleteDialog=" + deleteDialog
                + ", step12Deleted=" + deleted + ", step13Recaptured=" + recaptured);
        return recaptured;
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

    /**
     * Opens the Terms &amp; Conditions page from the signup form.
     *
     * <p>The row is one non-clickable TextView, "I have read and agree to the Terms &amp;
     * Conditions", which wraps to two lines — only the trailing "Terms &amp; Conditions"
     * words (start of line 2, lower-LEFT of the element) are the tappable link; a centre
     * tap lands on plain copy and does nothing. Confirmed on-device: tapping at
     * (~15% width, ~80% height) of the element opens the page. This tries that point, then
     * a couple of nearby fallbacks along line 2, verifying the Terms page each time.
     */
    public boolean clickTermsAndConditions() {
        WebElement link;
        try {
            link = findClickableWithScroll(TERMS_AND_CONDITIONS_LINK, "clickTermsAndConditions");
        } catch (Exception e) {
            System.out.println("[SignupPage] 'clickTermsAndConditions' — link text not found: " + e.getMessage());
            return false;
        }
        org.openqa.selenium.Rectangle r = link.getRect();
        double[][] fractions = {{0.15, 0.80}, {0.25, 0.80}, {0.10, 0.72}, {0.15, 0.90}};
        for (double[] f : fractions) {
            int x = r.getX() + (int) (r.getWidth() * f[0]);
            int y = r.getY() + (int) (r.getHeight() * f[1]);
            try {
                Map<String, Object> args = new HashMap<>();
                args.put("x", x);
                args.put("y", y);
                driver.executeScript("mobile: clickGesture", args);
                System.out.println("[SignupPage] Tapped 'Terms & Conditions' link @" + x + "," + y);
                pauseForAction();
            } catch (Exception e) {
                System.out.println("[SignupPage] Terms link tap @" + x + "," + y + " failed: " + e.getMessage());
                continue;
            }
            if (isPresent(TERMS_PAGE_MARKER, 4)) {
                return true;
            }
            System.out.println("[SignupPage] Terms page not shown after tap @" + x + "," + y + " — trying next point");
        }
        return false;
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
