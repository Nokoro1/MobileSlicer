package com.mobileslicer.modelsearch.thingiverse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class ThingiverseAuthSession(
    val accessToken: String,
    val displayName: String?,
    val expiresAtEpochMs: Long?
) {
    val isUsable: Boolean
        get() = accessToken.isNotBlank() &&
            (expiresAtEpochMs == null || expiresAtEpochMs > System.currentTimeMillis() + 60_000L)
}

data class ThingiverseOAuthConfig(
    val clientId: String,
    val backendBaseUrl: String,
    val redirectUri: String
) {
    val isConfigured: Boolean
        get() = clientId.isNotBlank() && backendBaseUrl.isTrustedBackendUrl() && redirectUri.isNotBlank()
}

data class ThingiverseOAuthStart(
    val url: String,
    val state: String
)

sealed interface ThingiverseOAuthRedirectResult {
    data class Success(val session: ThingiverseAuthSession) : ThingiverseOAuthRedirectResult
    data class Handoff(val code: String) : ThingiverseOAuthRedirectResult
    data class Failure(val message: String) : ThingiverseOAuthRedirectResult
    data object NotThingiverseRedirect : ThingiverseOAuthRedirectResult
}

class ThingiverseAuthStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun loadSession(): ThingiverseAuthSession? {
        migratePlainTextSessionIfPresent()
        val encryptedSession = preferences.getString(KEY_ENCRYPTED_SESSION, null) ?: return null
        val json = decrypt(encryptedSession)?.let(::JSONObject) ?: return null
        val token = json.optString("accessToken", "")
        if (token.isBlank()) return null
        val session = ThingiverseAuthSession(
            accessToken = token,
            displayName = json.optString("displayName").takeIf { it.isNotBlank() },
            expiresAtEpochMs = json.optLong("expiresAtEpochMs", 0L).takeIf { it > 0L }
        )
        return session.takeIf { it.isUsable }
    }

    fun saveSession(session: ThingiverseAuthSession) {
        val json = JSONObject()
            .put("accessToken", session.accessToken)
            .put("displayName", session.displayName.orEmpty())
            .put("expiresAtEpochMs", session.expiresAtEpochMs ?: 0L)
        val encrypted = encrypt(json.toString())
        preferences.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_EXPIRES_AT)
            if (encrypted != null) {
                putString(KEY_ENCRYPTED_SESSION, encrypted)
            } else {
                remove(KEY_ENCRYPTED_SESSION)
            }
        }.apply()
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_ENCRYPTED_SESSION)
            .remove(KEY_PENDING_STATE)
            .apply()
    }

    fun hasPendingOAuthState(): Boolean =
        preferences.getString(KEY_PENDING_STATE, "").orEmpty().isNotBlank()

    fun buildOAuthStart(config: ThingiverseOAuthConfig): ThingiverseOAuthStart? {
        if (!config.isConfigured) return null
        val state = UUID.randomUUID().toString()
        preferences.edit().putString(KEY_PENDING_STATE, state).apply()
        val startUrl = config.backendBaseUrl.trimEnd('/') +
            "/v1/thingiverse/oauth/start?" +
            mapOf(
                "client_id" to config.clientId,
                "redirect_uri" to config.redirectUri,
                "state" to state
            ).toQueryString()
        return ThingiverseOAuthStart(url = startUrl, state = state)
    }

    fun consumeOAuthRedirect(uri: Uri, expectedScheme: String, expectedHost: String): ThingiverseOAuthRedirectResult {
        if (!isThingiverseOAuthRedirect(uri, expectedScheme, expectedHost)) {
            return ThingiverseOAuthRedirectResult.NotThingiverseRedirect
        }
        val pendingState = preferences.getString(KEY_PENDING_STATE, "").orEmpty()
        val returnedState = uri.getQueryParameter("state").orEmpty()
        preferences.edit().remove(KEY_PENDING_STATE).apply()
        if (pendingState.isBlank() || returnedState.isBlank() || pendingState != returnedState) {
            return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in failed. OAuth state did not match.")
        }
        uri.getQueryParameter("error")?.takeIf { it.isNotBlank() }?.let { error ->
            val description = uri.getQueryParameter("error_description").orEmpty().ifBlank { error }
            return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in failed. $description")
        }
        val handoffCode = uri.getQueryParameter("handoff_code")
            ?: uri.getQueryParameter("handoff")
            ?: ""
        if (handoffCode.isNotBlank()) {
            return ThingiverseOAuthRedirectResult.Handoff(handoffCode)
        }

        return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in failed. No handoff code was returned.")
    }

    fun redeemOAuthHandoff(config: ThingiverseOAuthConfig, code: String): ThingiverseOAuthRedirectResult {
        if (!config.isConfigured) {
            return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in is not configured.")
        }
        if (!isSafeHandoffCode(code)) {
            return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in failed. Handoff code was invalid.")
        }
        val redeemUrl = config.backendBaseUrl.trimEnd('/') +
            "/v1/thingiverse/oauth/redeem?code=${code.urlEncode()}"
        val connection = (URL(redeemUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MobileSlicer Thingiverse OAuth")
        }
        val result = try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in failed. Try again.")
            }
            JSONObject(body).toAuthSession()
        } catch (error: Exception) {
            return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in failed. Try again.")
        } finally {
            connection.disconnect()
        }
        val session = result ?: return ThingiverseOAuthRedirectResult.Failure("Thingiverse sign-in failed. No access token was returned.")
        saveSession(session)
        return ThingiverseOAuthRedirectResult.Success(session)
    }

    private fun JSONObject.toAuthSession(): ThingiverseAuthSession? {
        val token = optString("access_token")
            .ifBlank { optString("session_token") }
            .ifBlank { optString("token") }
        if (token.isBlank()) return null
        val expiresInSeconds = optString("expires_in").toLongOrNull()
        return ThingiverseAuthSession(
            accessToken = token,
            displayName = optString("display_name")
                .ifBlank { optString("username") }
                .ifBlank { optString("user") }
                .takeIf { it.isNotBlank() },
            expiresAtEpochMs = expiresInSeconds?.let { System.currentTimeMillis() + it * 1000L }
        )
    }

    private fun migratePlainTextSessionIfPresent() {
        if (preferences.contains(KEY_ENCRYPTED_SESSION)) return
        val token = preferences.getString(KEY_ACCESS_TOKEN, "").orEmpty()
        if (token.isBlank()) return
        saveSession(
            ThingiverseAuthSession(
                accessToken = token,
                displayName = preferences.getString(KEY_DISPLAY_NAME, null),
                expiresAtEpochMs = preferences.getLong(KEY_EXPIRES_AT, 0L).takeIf { it > 0L }
            )
        )
    }

    private fun encrypt(plainText: String): String? =
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            JSONObject()
                .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .put("cipherText", Base64.encodeToString(cipherText, Base64.NO_WRAP))
                .toString()
        }.getOrNull()

    private fun decrypt(encrypted: String): String? =
        runCatching {
            val json = JSONObject(encrypted)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val cipherText = Base64.decode(json.getString("cipherText"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val PREFERENCES = "thingiverse_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EXPIRES_AT = "expires_at_epoch_ms"
        private const val KEY_ENCRYPTED_SESSION = "encrypted_session"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mobile_slicer_thingiverse_auth_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

private fun isSafeHandoffCode(code: String): Boolean =
    code.length in 32..128 && code.all { it.isLetterOrDigit() || it == '-' || it == '_' }

private fun String.isTrustedBackendUrl(): Boolean {
    if (isBlank()) return false
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    if (uri.scheme == "https" && !uri.host.isNullOrBlank()) return true
    return uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
}

fun isThingiverseOAuthRedirect(uri: Uri?, expectedScheme: String, expectedHost: String): Boolean =
    uri != null && isThingiverseOAuthRedirectParts(uri.scheme, uri.host, expectedScheme, expectedHost)

internal fun isThingiverseOAuthRedirectParts(
    actualScheme: String?,
    actualHost: String?,
    expectedScheme: String,
    expectedHost: String
): Boolean =
    actualScheme == expectedScheme && actualHost == expectedHost

fun thingiverseOAuthRedirectFrom(intent: Intent?, expectedScheme: String, expectedHost: String): Uri? =
    intent
        ?.takeIf { it.action == Intent.ACTION_VIEW }
        ?.data
        ?.takeIf { isThingiverseOAuthRedirect(it, expectedScheme, expectedHost) }

private fun Map<String, String>.toQueryString(): String =
    entries.joinToString("&") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

internal fun ThingiverseAuthSession.toDebugJson(): JSONObject =
    JSONObject()
        .put("hasAccessToken", accessToken.isNotBlank())
        .put("displayName", displayName.orEmpty())
        .put("expiresAtEpochMs", expiresAtEpochMs ?: 0L)
