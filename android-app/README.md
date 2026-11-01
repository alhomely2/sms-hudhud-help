# Hudhud Assistant Android App

This is the Android application project. The project is being implemented in small, separate operations:

- Ready messages
- Templates (the only place a template is created, edited, duplicated or deleted; a template may mix per-row columns with fixed values such as the billing month)
- Raw data that needs a template (the campaign entry only matches and selects an existing template; when none matches it offers to open the Templates tab)
- Connection settings (per-site HTTPS profiles, encrypted API keys, timeout/rate policies, and a read-only connectivity probe)

## Local build on Windows

Keep Gradle cache and temporary files on drive D:

```powershell
$env:GRADLE_USER_HOME = 'D:\sms-hudhud-help\.gradle-home'
$env:TEMP = 'D:\sms-hudhud-help\.tmp'
$env:TMP = $env:TEMP
$env:JAVA_HOME = 'D:\Android\jdk17\jdk-17.0.20.1+1'
$env:ANDROID_HOME = 'D:\Android\sdk'
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

The current build provides the application shell, file selection, local template storage, tested message-preparation logic, per-site connection profiles, and a read-only connection probe. API keys are encrypted with Android Keystore; request timeouts, proposed batch size, and rate settings are stored per site. The proposed network batch size and retry policy are not used to send campaigns: file parsing still needs device-level validation, and delivery requires the platform's API contract. The platform's authenticated messages endpoint currently returns HTTP 500, so only the read-only probe is implemented; see `../docs/API-CONNECTION-NOTES.md` and `../docs/SETTINGS-MANAGEMENT-PLAN.md`.

Android Lint currently reports advisory dependency-version notices, the intentionally configured target SDK 34, and trust-manager code present in parser dependency jars. The app does not call those trust-manager utilities; review or replace the parser dependencies before production, and keep app networking on OkHttp's default certificate validation.
