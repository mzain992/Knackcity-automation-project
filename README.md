# Knackcity-automation-project

Appium + TestNG UI automation for the Knackcity Android app.

- **Language / build:** Java 11 (compiled), Maven
- **Automation:** Appium `java-client` 9.2.3, UiAutomator2, Selenium 4.21.0
- **Runner:** TestNG 7.10.2 via `maven-surefire-plugin`, driven by `testng.xml`

## Test suite

`testng.xml` defines the suite `KnackcityAppiumSuite` with two `<test>` blocks:

| `<test>` | Class | Tests |
|---|---|---|
| `OnboardingTests` | `tests.OnboardingTest` | `testNavigateToSignupViaNext`, `testNavigateToSignupViaSkip`, `testSignupWithEmailAndPassword`, `testSignupWithCameraImageUpload`, `testSignupWithGoogle`, `testSignupDobValidation`, `testNavigateToSignInFromSignup` |
| `ForgotPasswordTests` | `tests.ForgotPasswordTest` | `testForgotPasswordEndToEndFlow` — full reset journey: Forgot Password → Back → Forgot Password → enter email → Send Verification Code → OTP screen → 60s cooldown → Resend OTP → **manual OTP entry** → Verify Code → "Email Verified successfully" → New Password screen (confirm-mismatch validation) → show/hide → Reset Password → Sign In screen → login with the new password. **Requires a human to type the OTP on the device.** |
| `SignInTests` | `tests.SignInTest` | `executeSignInTestCases` — one reusable method, run **twice** (one data-provider row per Sign In entry point). Each run navigates to Sign In and executes the full plan: **TC-01** valid sign-in, **TC-02** invalid email + valid password, **TC-03** valid email + invalid password, **TC-04** invalid email + invalid password, **TC-05** empty email, **TC-06** empty password, **TC-07** both empty, **TC-08** invalid email format, **TC-09** password show/hide, **TC-10** password case-sensitivity, **TC-11** unregistered email, **TC-12** Sign in with Google, plus **Forgot Password** navigation. Sub-cases run sequentially and are isolated — all failures are collected and reported together at the end. |

### Sign In entry points

`executeSignInTestCases` covers both routes to the (identical) Sign In screen:

| Entry point | Path | Sign In control |
|---|---|---|
| Welcome screen | onboarding → Welcome → **Sign In** | `//android.view.ViewGroup[@content-desc="Sign In"]` |
| Get Started screen | onboarding → Welcome → **Get Started** → Signup → **Sign In** | `//android.widget.TextView[@text="Sign In"]` |

TC-01 and TC-12 (the two cases that actually authenticate) run last, so a single in-session
app restart — `BaseTest.restartAppWithClearedData()` — is enough to get back to Sign In for
the following case.

### Forgot Password test — notes

`ForgotPasswordTest` is a manual-assisted end-to-end test:

- **OTP is entered by hand on the device.** The test waits explicitly for the "Resend OTP"
  option (the 60s server cooldown), taps Resend, then polls the OTP field until a 4+ digit
  code appears (bounded by `-DotpEntryWaitSeconds`, default 180) before tapping Verify Code.
- **The new password must be one this account has not used before** — the backend silently
  rejects a previously-used value on "Reset Password". Set it with
  `-DforgotNewPassword=<value>` (default `Onyx@123`), and pick a fresh one per run.
- Run it on its own: `mvn test -Dtest=ForgotPasswordTest -DforgotNewPassword=Fresh@123`.
- Reuses `OnboardingPage` (reach Sign In), `SignInPage` (open "Forgot Password?", final
  login), and adds `pages.ForgotPasswordPage` for the reset-specific screens. No existing
  Sign-In / onboarding / Google code is modified.

## Prerequisites

| Tool | Version used | Check |
|---|---|---|
| JDK | 17 (source/target level 11) | `java -version` |
| Maven | 3.6.3 | `mvn -version` |
| Appium server | 3.x | `appium -v` |
| Android SDK platform-tools (`adb`) | — | `adb version` |

Also required:

- A physical Android device connected and authorized, with UDID **`R8VXA01R73Y`**
  (`adb devices` must list it as `device`).
- The app under test installed on that device — package **`com.knackcity`**,
  main activity **`com.knackcity.MainActivity`**.

These values are hardcoded in `src/test/java/base/BaseTest.java` (`APPIUM_URL`,
`DEVICE_NAME`, `APP_PACKAGE`, `APP_ACTIVITY`). Change them there if your setup differs.
`BaseTest` also runs `adb -s R8VXA01R73Y shell pm clear com.knackcity` before every
test to force a deterministic fresh-install starting state, so `adb` must be on `PATH`.

```bash
# verify prerequisites
java -version
mvn -version
appium -v
adb devices                                              # must show R8VXA01R73Y  device
adb -s R8VXA01R73Y shell pm list packages | grep com.knackcity
```

## Running the suite

### 1. Start the Appium server

In a separate terminal, leave it running:

```bash
appium --address 127.0.0.1 --port 4723
```

Verify it is up:

```bash
curl -s http://127.0.0.1:4723/status
```

### 2. Run the tests

From the project root (`/home/muhammadzain/Knackcity`):

```bash
mvn clean test
```

Surefire is configured with `<suiteXmlFile>testng.xml</suiteXmlFile>`, so `mvn test`
alone runs the whole suite (`OnboardingTests` then `SignInTests`).

For non-interactive / CI output:

```bash
mvn -B test
```

### Useful variations

```bash
mvn test -Dtest=SignInTest                            # a single test class
mvn test -Dtest=OnboardingTest#testSignupWithGoogle   # a single test method
mvn -Dsurefire.suiteXmlFiles=testng.xml test          # explicit suite file
mvn -e test                                           # add stack traces on failure
mvn -X test                                           # full debug logging
```

## Reports

After a run, results are written to `target/surefire-reports/`:

| File | Contents |
|---|---|
| `index.html` | HTML summary of the run |
| `emailable-report.html` | Single-file HTML report |
| `testng-results.xml` | Machine-readable TestNG results |
| `KnackcityAppiumSuite/` | Per-`<test>` HTML/XML output |
