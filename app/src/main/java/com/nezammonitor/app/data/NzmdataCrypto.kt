package com.nezammonitor.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** SEC-015 (Windows v12): the encrypted .nzmdata format, mirrored exactly
 *  from AndroidExportEngine / BackupEngine on Windows so a package written
 *  by either side opens on the other:
 *  - key = PBKDF2-SHA256(password, salt, 600_000 iterations, 32 bytes)
 *  - AES-256-GCM, 12-byte nonce, 16-byte tag
 *  - stored data.json bytes = ciphertext followed by the 16-byte tag
 *  - manifest.checksum = SHA-256 (uppercase hex) over the stored bytes
 *    (the ciphertext when encrypted, the plain JSON bytes otherwise)
 *  - manifest fields: encrypted (bool), encryption ("AES-256-GCM" or ""),
 *    salt / nonce (Base64, empty when not encrypted).
 *  A wrong password fails the GCM tag check (AEADBadTagException) — the
 *  caller turns that into "رمز اشتباه است". */
object NzmdataCrypto {
    const val PBKDF2_ITERATIONS = 600_000
    const val KEY_BYTES = 32
    const val SALT_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16
    const val ENCRYPTION_ID = "AES-256-GCM"

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { SecureRandom().nextBytes(it) }

    fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BYTES * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Encrypts [plain] and returns ciphertext + 16-byte tag. */
    fun encrypt(plain: ByteArray, password: String, salt: ByteArray, nonce: ByteArray): ByteArray {
        val key = SecretKeySpec(deriveKey(password, salt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
        // Java's GCM doFinal appends the tag after the ciphertext — the
        // same ciphertext||tag layout .NET's AesGcm writes.
        return cipher.doFinal(plain)
    }

    /** Decrypts ciphertext + 16-byte tag. Throws AEADBadTagException
     *  (a subclass of BadPaddingException) when the password is wrong or
     *  the data is corrupt. */
    fun decrypt(cipherWithTag: ByteArray, password: String, salt: ByteArray, nonce: ByteArray): ByteArray {
        require(cipherWithTag.size >= TAG_BYTES) { "داده رمزنگاری‌شده خراب است." }
        val key = SecretKeySpec(deriveKey(password, salt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
        return cipher.doFinal(cipherWithTag)
    }

    /** SHA-256 as uppercase hex, the same as .NET Convert.ToHexString. */
    fun sha256Hex(bytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = CharArray(hash.size * 2)
        val digits = "0123456789ABCDEF"
        for (i in hash.indices) {
            hex[i * 2] = digits[(hash[i].toInt() shr 4) and 0xF]
            hex[i * 2 + 1] = digits[hash[i].toInt() and 0xF]
        }
        return hex.concatToString()
    }
}
