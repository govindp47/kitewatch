# 07 — Security Model

**Version:** 1.0
**Product:** KiteWatch — Android Local-First Portfolio Management
**Last Updated:** 2026-03-10

---

## 1. Threat Model

### 1.1 Asset Inventory

| Asset | Classification | Risk if Compromised |
|---|---|---|
| Kite Connect API key | **SECRET** | Unauthorized API access to user's Zerodha account |
| Kite Connect access token | **SECRET** | Active session hijack — order placement, fund withdrawal |
| SQLCipher encryption key | **SECRET** | Full database decryption — all financial history exposed |
| Database content (orders, holdings, transactions) | **CONFIDENTIAL** | Complete financial profile exposure |
| Google OAuth tokens (Gmail, Drive) | **SECRET** | Email reading, Drive file access |
| Biometric enrollment state | **SENSITIVE** | Bypass of app lock |
| Backup files (.kwbackup) | **CONFIDENTIAL** | Contains full database snapshot if intercepted |
| Charge rates, sync logs | **INTERNAL** | Low sensitivity — operational metadata |

### 1.2 Threat Actors and Attack Vectors

| # | Actor | Vector | Target Asset | Mitigation |
|---|---|---|---|---|
| T-01 | Physical — lost/stolen device | Direct device access | All local data | Biometric lock, SQLCipher, EncryptedSharedPreferences |
| T-02 | Physical — shoulder surfing | Screen observation | Financial data on screen | App lock timeout, no balance in notifications |
| T-03 | Malware — screen overlay | Accessibility exploit | Auth credentials | FLAG_SECURE on auth screens, overlay detection |
| T-04 | Malware — root access | File system read | Database file, SharedPrefs | SQLCipher (DB), EncryptedSharedPreferences (tokens) |
| T-05 | Network — MITM | Intercepted API calls | Access token, financial data | Certificate pinning, TLS 1.2+ only |
| T-06 | Network — DNS spoofing | Redirected API calls | Credentials sent to attacker | Certificate pinning on Kite API domain |
| T-07 | Backup interception | Man-in-the-middle on Drive upload | Full DB contents | TLS for Drive API, backup file integrity hash |
| T-08 | Side-channel — task switcher | App screenshot in recents | Screen content | FLAG_SECURE on all financial screens |
| T-09 | Debug — ADB extraction | `adb backup` command | App data | `android:allowBackup="false"` in manifest |
| T-10 | Supply chain — malicious dependency | Compromised library | All data | No third-party analytics/tracking SDKs; minimal deps |

### 1.3 Risk Matrix

```
            ┌────────────┬────────────┬────────────┐
            │   LOW      │   MEDIUM   │   HIGH     │
            │ LIKELIHOOD │ LIKELIHOOD │ LIKELIHOOD │
┌───────────┼────────────┼────────────┼────────────┤
│ HIGH      │ T-05, T-06 │ T-01       │            │
│ IMPACT    │ T-07       │ T-04       │            │
├───────────┼────────────┼────────────┼────────────┤
│ MEDIUM    │ T-03       │ T-02       │ T-08       │
│ IMPACT    │ T-09       │            │            │
├───────────┼────────────┼────────────┼────────────┤
│ LOW       │ T-10       │            │            │
│ IMPACT    │            │            │            │
└───────────┴────────────┴────────────┴────────────┘
```

---

## 2. Data Encryption Architecture

### 2.1 Encryption at Rest

```
┌────────────────────────────────────────────────────────┐
│                    LOCAL STORAGE                        │
│                                                        │
│  ┌─────────────────────┐   ┌────────────────────────┐ │
│  │   SQLCipher DB       │   │  EncryptedSharedPrefs  │ │
│  │   (kitewatch.db)     │   │  (secrets.xml)         │ │
│  │                      │   │                        │ │
│  │  AES-256-CBC         │   │  AES-256-SIV           │ │
│  │  Key: Android        │   │  Key: Android          │ │
│  │  Keystore-backed     │   │  Keystore MasterKey    │ │
│  │                      │   │                        │ │
│  │  Contains:           │   │  Contains:             │ │
│  │  • All 15 tables     │   │  • API key             │ │
│  │  • Financial data    │   │  • Access token        │ │
│  │  • Order history     │   │  • Google OAuth tokens │ │
│  │  • Transaction log   │   │  • SQLCipher passphrase│ │
│  └─────────────────────┘   └────────────────────────┘ │
│                                                        │
│  ┌─────────────────────┐   ┌────────────────────────┐ │
│  │   Jetpack DataStore  │   │   Backup Files         │ │
│  │   (settings.pb)      │   │   (.kwbackup)          │ │
│  │                      │   │                        │ │
│  │  NOT encrypted       │   │  Protobuf + GZIP       │ │
│  │  (non-sensitive only)│   │  SHA-256 integrity     │ │
│  │                      │   │  check                 │ │
│  │  Contains:           │   │                        │ │
│  │  • Theme preference  │   │  Transport security:   │ │
│  │  • Schedule times    │   │  TLS to Google Drive   │ │
│  │  • Feature flags     │   │                        │ │
│  └─────────────────────┘   └────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

### 2.2 SQLCipher Configuration

```kotlin
class DatabaseProvider @Inject constructor(
    private val context: Context,
    private val masterKeyProvider: MasterKeyProvider,
) {
    fun buildDatabase(): KiteWatchDatabase {
        val passphrase = masterKeyProvider.getDatabasePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)

        return Room.databaseBuilder(
            context,
            KiteWatchDatabase::class.java,
            "kitewatch.db",
        )
            .openHelperFactory(factory)
            .addMigrations(/* ... */)
            .build()
    }
}

class MasterKeyProvider @Inject constructor(
    private val encryptedPrefs: SharedPreferences, // EncryptedSharedPreferences
) {
    companion object {
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val PASSPHRASE_LENGTH = 32
    }

    fun getDatabasePassphrase(): ByteArray {
        val stored = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        return if (stored != null) {
            Base64.decode(stored, Base64.NO_WRAP)
        } else {
            generateAndStorePassphrase()
        }
    }

    private fun generateAndStorePassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_LENGTH).also {
            SecureRandom().nextBytes(it)
        }
        encryptedPrefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }
}
```

**Key Storage Chain:**

1. SQLCipher passphrase (32 random bytes) is generated once on first launch.
2. Passphrase is stored in `EncryptedSharedPreferences`.
3. `EncryptedSharedPreferences` uses Android Keystore `MasterKey` (AES-256-SIV) for key encryption.
4. Android Keystore key is hardware-backed on devices with TEE/StrongBox.
5. **Result:** Database passphrase is protected by the hardware security module.

### 2.3 EncryptedSharedPreferences Setup

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(true) // Use StrongBox if available
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "kitewatch_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
```

---

## 3. Authentication System

### 3.1 Biometric Authentication Flow

```
App Launch / Resume from Background
         │
         ▼
┌────────────────────┐
│ Check lock status  │
│ (lockTimeoutExpired│
│  OR first launch)  │
└────────┬───────────┘
         │
    ┌────┤
    │ NOT│ LOCKED
    │ LOCKED
    ▼    ▼
 [Main] ┌────────────────────┐
 Screen │ BiometricPrompt    │
        │ (fingerprint/face  │
        │  /device credential│
        └────────┬───────────┘
                 │
    ┌────────────┤
    │ SUCCESS    │ FAILURE         │ ERROR
    ▼            ▼                 ▼
 [Main]  ┌──────────────┐  ┌──────────────┐
 Screen  │ Retry (up to │  │ Show error   │
         │ 3 attempts)  │  │ message.     │
         └──────────────┘  │ Fallback to  │
                           │ device cred  │
                           └──────────────┘
```

### 3.2 BiometricManager Implementation

```kotlin
class BiometricManager @Inject constructor(
    private val context: Context,
    private val preferencesRepo: PreferencesRepository,
) {
    private val biometricPromptManager = androidx.biometric.BiometricManager.from(context)

    private val _lockState = MutableStateFlow<LockState>(LockState.Locked)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    private var lastAuthTime: Instant? = null

    fun canAuthenticate(): BiometricCapability {
        return when (biometricPromptManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricCapability.HardwareUnavailable
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NoneEnrolled
            else -> BiometricCapability.Unknown
        }
    }

    fun createPromptInfo(): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock KiteWatch")
            .setSubtitle("Authenticate to access your portfolio")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
    }

    fun onAuthenticationSuccess() {
        lastAuthTime = Instant.now()
        _lockState.value = LockState.Unlocked
    }

    suspend fun checkLockOnResume() {
        val timeout = preferencesRepo.getLockTimeoutMinutes()
        val lastAuth = lastAuthTime

        if (lastAuth == null) {
            _lockState.value = LockState.Locked
            return
        }

        val elapsed = Duration.between(lastAuth, Instant.now())
        if (elapsed.toMinutes() >= timeout) {
            _lockState.value = LockState.Locked
        }
    }

    fun lockNow() {
        lastAuthTime = null
        _lockState.value = LockState.Locked
    }
}

sealed interface LockState {
    data object Locked : LockState
    data object Unlocked : LockState
}

sealed interface BiometricCapability {
    data object Available : BiometricCapability
    data object NoHardware : BiometricCapability
    data object HardwareUnavailable : BiometricCapability
    data object NoneEnrolled : BiometricCapability
    data object Unknown : BiometricCapability
}
```

### 3.3 Lock Lifecycle Integration

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var biometricManager: BiometricManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots on sensitive screens
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        setContent {
            val lockState by biometricManager.lockState.collectAsStateWithLifecycle()

            KiteWatchTheme {
                when (lockState) {
                    LockState.Locked -> AuthScreen(biometricManager = biometricManager)
                    LockState.Unlocked -> KiteWatchApp()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Record the time when app goes to background
        // Lock check happens in onResume
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            biometricManager.checkLockOnResume()
        }
    }
}
```

---

## 4. Kite Connect Session Security

### 4.1 Token Lifecycle

```
┌───────────────┐
│ User initiates│
│ Kite Login    │
│ (WebView)     │
└───────┬───────┘
        │
        ▼
┌───────────────────────────┐
│ Kite Connect OAuth2 Flow  │
│ 1. User enters credentials│
│    on kite.zerodha.com    │
│ 2. Redirect with          │
│    request_token          │
└───────────┬───────────────┘
            │
            ▼
┌───────────────────────────┐
│ Exchange request_token    │
│ for access_token via      │
│ POST /session/token       │
│ (server-side equivalent)  │
└───────────┬───────────────┘
            │
            ▼
┌───────────────────────────┐
│ Store securely:           │
│ • access_token →          │
│   EncryptedSharedPrefs    │
│ • api_key →               │
│   EncryptedSharedPrefs    │
│ • user_id →               │
│   EncryptedSharedPrefs    │
│ Also update SQLCipher DB: │
│ • account_binding table   │
└───────────┬───────────────┘
            │
            ▼ Token valid for ~6-8 hours (market day)
┌───────────────────────────┐
│ Token used for all API    │
│ calls via AuthInterceptor │
│                           │
│ On 403 response:          │
│ → Token invalidated       │
│ → SessionState.Expired    │
│ → persistent_alerts entry │
│ → User prompted to re-    │
│   authenticate            │
└───────────────────────────┘
```

### 4.2 Token Invalidation Rules

| Trigger | Action | User Impact |
|---|---|---|
| API returns 403 | Remove `access_token` from EncryptedPrefs, emit `SessionExpired` event | Banner: "Session expired. Tap to re-login." |
| API returns 401 | Same as 403 | Same banner |
| User manually logs out (not in v1, but designed for) | Remove all auth tokens, clear `account_binding.access_token` | Full re-authentication required |
| Token age > 24 hours (defensive) | Proactive invalidation check on app foreground | Soft prompt to re-authenticate if token is stale |

### 4.3 WebView Security for Kite Login

```kotlin
class KiteLoginWebViewClient(
    private val onRequestToken: (String) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // Intercept the redirect URL containing the request_token
        if (url.contains("kite_callback") || url.contains("request_token=")) {
            val requestToken = Uri.parse(url).getQueryParameter("request_token")
            if (requestToken != null) {
                onRequestToken(requestToken)
                return true // Prevent WebView from loading the redirect
            }
        }
        return false
    }
}

// WebView configuration
fun configureSecureWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true  // Required for Kite login
        domStorageEnabled = false
        setSupportMultipleWindows(false)
        allowFileAccess = false
        allowContentAccess = false
        // No JavaScript interfaces exposed
    }

    // Clear cookies after login completes
    // Clear WebView data to prevent credential caching
}

fun clearWebViewData(context: Context) {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
}
```

---

## 5. Network Security

### 5.1 Certificate Pinning

```kotlin
object CertificatePinning {
    val kiteConnectPins = CertificatePinner.Builder()
        .add("api.kite.trade", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // Primary
        .add("api.kite.trade", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup
        .add("kite.zerodha.com", "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkSecurityModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: KiteConnectAuthInterceptor,
        rateLimitInterceptor: RateLimitInterceptor,
        transmissionAuditInterceptor: DataTransmissionAuditInterceptor,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .certificatePinner(CertificatePinning.kiteConnectPins)
            .addInterceptor(authInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(transmissionAuditInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // TLS 1.2+ enforced by default on API 26+
            .build()
    }
}
```

> **Note:** Certificate pin SHA-256 hashes are placeholders. Actual pins must be extracted from Zerodha's current certificates before release. Pins should include at least one backup pin to handle certificate rotation.

### 5.2 Network Security Config

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <!-- Forbid cleartext (HTTP) traffic entirely -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Pin Kite Connect API domain -->
    <domain-config>
        <domain includeSubdomains="true">api.kite.trade</domain>
        <domain includeSubdomains="true">kite.zerodha.com</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </domain-config>

    <!-- Allow debug-only cleartext for local testing -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

### 5.3 Host Allowlist Enforcement

```kotlin
class DataTransmissionAuditInterceptor : Interceptor {
    private val allowedHosts = setOf(
        "api.kite.trade",
        "kite.zerodha.com",
        "www.googleapis.com",
        "oauth2.googleapis.com",
        "accounts.google.com",
        "gmail.googleapis.com",
        "content.googleapis.com",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        if (host !in allowedHosts) {
            Timber.e("SECURITY VIOLATION: Attempted request to unauthorized host: $host")
            throw SecurityException("Network request to unauthorized host: $host")
        }
        return chain.proceed(chain.request())
    }
}
```

---

## 6. Application Hardening

### 6.1 AndroidManifest Security

```xml
<application
    android:name=".KiteWatchApplication"
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="false"
    android:supportsRtl="true"
    android:theme="@style/Theme.KiteWatch">

    <!-- No exported activities except the launcher -->
    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:windowSoftInputMode="adjustResize">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <!-- All other components are NOT exported -->
    <!-- No content providers, no broadcast receivers for external intents -->
</application>
```

### 6.2 Data Extraction Rules (Android 12+)

```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <!-- Exclude everything from Google's cloud backup -->
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </cloud-backup>
    <device-transfer>
        <!-- Also exclude from device-to-device transfer -->
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
    </device-transfer>
</data-extraction-rules>
```

### 6.3 FLAG_SECURE Strategy

| Screen | FLAG_SECURE | Rationale |
|---|---|---|
| Auth (Biometric) screen | ✅ Yes | Prevent screenshot of auth prompt |
| Portfolio screen | ✅ Yes | Financial data visible |
| Holdings screen | ✅ Yes | Stock positions visible |
| Orders screen | ✅ Yes | Trade history visible |
| Transactions screen | ✅ Yes | Fund flow visible |
| GTT screen | ✅ Yes | Price targets visible |
| Settings screens | ❌ No | No sensitive data (except charge rates, which are public) |
| Onboarding (T&C) | ❌ No | Public content |
| About / Guidebook | ❌ No | Public content |

**Implementation:** `FLAG_SECURE` is set at the `Activity` level in `onCreate()`. Since KiteWatch uses a single-Activity architecture with Compose Navigation, the flag applies to all screens. The trade-off (Settings screens are also secured) is acceptable for the security benefit.

### 6.4 ProGuard/R8 Configuration

```proguard
# R8 full mode enabled in gradle.properties
# android.enableR8.fullMode=true

# Keep Kotlin serialization
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Room entities and DAOs
-keep class com.kitewatch.database.entity.** { *; }
-keep class com.kitewatch.database.dao.** { *; }

# Keep Retrofit interfaces
-keep interface com.kitewatch.network.kiteconnect.KiteConnectApiService { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }

# Keep Protobuf generated classes (backup format)
-keep class com.kitewatch.infra.backup.proto.** { *; }

# Obfuscate everything else
# Class names, method names, field names are renamed
# This makes reverse engineering significantly harder
```

---

## 7. Secrets Management

### 7.1 Secret Lifecycle

| Secret | Generation | Storage | Rotation | Invalidation |
|---|---|---|---|---|
| SQLCipher passphrase | Generated once on first launch (SecureRandom) | EncryptedSharedPreferences | Never (tied to DB) | App uninstall / data clear |
| Kite API key | User provides during onboarding | EncryptedSharedPreferences + DB (encrypted) | User action (re-bind) | Account re-binding |
| Kite access token | OAuth flow result | EncryptedSharedPreferences + DB (encrypted) | Every login (daily) | 403/401 response, manual re-auth |
| Google OAuth refresh token | Google Sign-In flow | Managed by Google Play Services | Google manages | User revokes in Google settings |
| Master Key (Android Keystore) | Auto-generated by EncryptedSharedPreferences | Hardware-backed Keystore | Never | Factory reset |

### 7.2 No Hardcoded Secrets

**Enforcement:**

1. **CI check:** `grep -rn "api_key\|secret\|password\|token" --include="*.kt" --include="*.java"` in GitHub Actions. Any match in non-test files fails the build.
2. **Lint check:** Custom Lint rule `NoHardcodedCredentials` scans for string literals matching credential patterns.
3. **Build-time:** API base URLs are in `BuildConfig` (not secret, but configurable per build type). API keys are never in `BuildConfig` (entered at runtime by user).

---

## 8. Vulnerability Management

### 8.1 Dependency Scanning

```yaml
# .github/workflows/security.yml
name: Security Scan
on:
  schedule:
    - cron: '0 6 * * 1'  # Weekly on Monday
  push:
    branches: [main]

jobs:
  dependency-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Dependency Check
        run: ./gradlew dependencyCheckAnalyze
        # Uses OWASP Dependency-Check Gradle plugin
      - name: Upload Report
        uses: actions/upload-artifact@v4
        with:
          name: dependency-check-report
          path: build/reports/dependency-check-report.html
```

### 8.2 Update Policy

| Component | Update Frequency | Process |
|---|---|---|
| AndroidX libraries | Monthly | Dependabot PR → CI → merge |
| OkHttp / Retrofit | Monthly | Same |
| SQLCipher | Per release (security patches) | Immediate patch release |
| Room | Per AndroidX release cycle | Standard PR |
| Kotlin | Per stable release | Staged rollout (feature branch first) |
| Gradle | Per stable release | Build verification before merge |

### 8.3 Security-Critical Dependencies

| Dependency | Purpose | Security Relevance |
|---|---|---|
| `net.zetetic:sqlcipher-android` | Database encryption | Core data protection — must stay current |
| `androidx.security:security-crypto` | EncryptedSharedPreferences | Token storage — must stay current |
| `androidx.biometric:biometric` | App lock | Auth bypass risk if outdated |
| `com.squareup.okhttp3:okhttp` | Network communication | TLS implementation — must stay current |

---

## 9. Audit Logging for Security Events

### 9.1 Security Events Tracked

| Event | Logged To | Details Captured |
|---|---|---|
| Biometric auth success | `sync_event_log` | Timestamp, auth method (fingerprint/face/device) |
| Biometric auth failure | `sync_event_log` | Timestamp, failure reason, attempt count |
| Kite session created | `sync_event_log` | Timestamp, user_id (no tokens logged) |
| Kite session expired | `sync_event_log` + `persistent_alerts` | Timestamp, trigger (403/timeout/manual) |
| Unauthorized host attempt | Timber log (local file) | Host, endpoint, timestamp |
| Database integrity check failed | `persistent_alerts` | Check type, failure details |
| Backup created | `backup_history` | Destination, file size, schema version |
| Backup restored | `sync_event_log` | Source, schema version, restore result |
| Certificate pin mismatch | Timber log (local file) | Domain, expected pins |

### 9.2 Logging Security Rules

1. **Never log tokens, passwords, or API keys.** Timber `Tree` implementation strips any string matching `token=`, `password=`, `api_key=` patterns.
2. **Never log full email bodies.** Gmail scan logs only message ID and detected amount.
3. **Never log financial amounts in plain text in Timber.** Amounts are logged as hashed or masked values for debugging. Full amounts appear only in the encrypted database.
4. **Timber logs are local-only.** No remote logging service. Log files are stored in app-private storage with 7-day retention.

```kotlin
class SecureFileLoggingTree(
    private val logDir: File,
) : Timber.Tree() {

    private val sensitivePatterns = listOf(
        Regex("(?:token|password|api_key|secret|access_token)\\s*[=:]\\s*\\S+", RegexOption.IGNORE_CASE),
    )

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val sanitized = sensitivePatterns.fold(message) { msg, pattern ->
            pattern.replace(msg, "[REDACTED]")
        }
        writeToFile(priority, tag, sanitized, t)
    }

    private fun writeToFile(priority: Int, tag: String?, message: String, t: Throwable?) {
        val logFile = File(logDir, "kitewatch_${LocalDate.now()}.log")
        val timestamp = Instant.now().toString()
        val level = when (priority) {
            Log.VERBOSE -> "V"; Log.DEBUG -> "D"; Log.INFO -> "I"
            Log.WARN -> "W"; Log.ERROR -> "E"; else -> "?"
        }
        logFile.appendText("$timestamp $level/$tag: $message\n")
        t?.let { logFile.appendText(it.stackTraceToString() + "\n") }
    }
}
```

---

## 10. Privacy Compliance Checklist

| # | Requirement | Implementation | Status |
|---|---|---|---|
| 1 | No third-party analytics SDKs | Dependency audit; no Firebase Analytics, no Mixpanel, etc. | ✅ Design |
| 2 | No third-party crash reporting | Timber local-only logging; no Crashlytics | ✅ Design |
| 3 | No remote telemetry | No outbound network calls except to Kite/Google APIs | ✅ Design |
| 4 | All data stored locally | Room + SQLCipher; backup to user's own Google Drive | ✅ Design |
| 5 | Gmail read-only, scoped access | Gmail API with user-defined filters only; no write access | ✅ Design |
| 6 | No email body persistence | Parsed in-memory; only metadata + amount stored | ✅ Design |
| 7 | Biometric auth mandatory | `BiometricManager` gates all app access | ✅ Design |
| 8 | No data shared with third parties | Host allowlist enforced; no tracking pixels | ✅ Design |
| 9 | User can delete all data | App data clear via Android Settings deletes everything | ✅ Design |
| 10 | Backup data belongs to user | Stored in user's Google Drive app folder or local storage | ✅ Design |
