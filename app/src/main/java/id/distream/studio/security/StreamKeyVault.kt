package id.distream.studio.security

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.WindowManager
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Modul Keamanan Berlapis DiStream Studio (`AES-256-GCM` Android Hardware Keystore).
 *
 * Melindungi streamer dari risiko kebocoran data sensitif:
 * 1. Enkripsi Stream Key & Token Webhook menggunakan kunci hardware TEE/StrongBox Android Keystore
 *    (`allowBackup="false"` di Manifest + terenkripsi di disk).
 * 2. Redaksi otomatis Stream Key pada seluruh pesan log/error (`sanitizeEndpointForLog`).
 * 3. Kontrol `FLAG_SECURE` pada jendela input sensitif agar Stream Key tidak ikut terekam layar!
 * 4. Pemeriksaan status Do Not Disturb (`NotificationManager`) sebelum mulai live agar pop-up
 *    pesan WhatsApp / kode OTP bank tidak bocor ke siaran layar.
 */
object StreamKeyVault {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "distream_master_aes_key_v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun encryptSecret(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            Base64.getEncoder().encodeToString(combined)
        }.getOrDefault("")
    }

    fun decryptSecret(encodedPayload: String): String {
        if (encodedPayload.isEmpty()) return ""
        return runCatching {
            val combined = Base64.getDecoder().decode(encodedPayload)
            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /**
     * Mencegah jendela aplikasi DiStream (tempat ketik Stream Key & PIN) ikut terekam di MediaProjection.
     */
    fun applyWindowScreenCaptureGuard(activity: Activity, enabled: Boolean) {
        if (enabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Memeriksa apakah mode Jangan Ganggu (DND) HP aktif agar notifikasi WA / OTP tersembunyi.
     */
    fun isDoNotDisturbActive(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }

    /**
     * Menyembunyikan token rahasia Stream Key jika URL harus ditampilkan di status/log.
     */
    fun sanitizeEndpointForDisplay(fullUrl: String): String {
        val idx = fullUrl.lastIndexOf('/')
        if (idx <= 8 || idx >= fullUrl.length - 1) return fullUrl
        val prefix = fullUrl.substring(0, idx)
        val keyPart = fullUrl.substring(idx + 1)
        val masked = if (keyPart.length > 4) "${keyPart.take(4)}••••••••" else "••••••••"
        return "$prefix/$masked"
    }
}
