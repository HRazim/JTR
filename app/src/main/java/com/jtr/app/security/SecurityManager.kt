package com.jtr.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Sécurité locale (v6.2.0) — verrou par schéma, 100 % sur l'appareil.
 *
 * Tout est stocké dans des [EncryptedSharedPreferences] (clé maîtresse AES-256-GCM
 * de l'Android Keystore) : on n'y conserve que des **empreintes salées** (PBKDF2-
 * HMAC-SHA256, sel aléatoire de 16 octets par secret), JAMAIS le schéma ni le code
 * de secours en clair. Aucune donnée ne quitte l'appareil ; aucune permission réseau.
 *
 * Le schéma est sérialisé en suite d'indices de nœuds (« 0-1-2-4 ») avant hachage.
 */
object SecurityManager {

    private const val FILE = "jtr_secure_prefs"
    private const val K_PATTERN_HASH = "pattern_hash"
    private const val K_PATTERN_SALT = "pattern_salt"
    private const val K_RECOVERY_HASH = "recovery_hash"
    private const val K_RECOVERY_SALT = "recovery_salt"
    private const val K_LOCK_ENABLED = "lock_enabled"
    private const val K_BIOMETRIC = "biometric_enabled"
    private const val K_FAILED = "failed_attempts"

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    /** Nombre minimal de nœuds exigé pour un schéma valide. */
    const val MIN_PATTERN_SIZE = 4

    // Alphabet sans caractères ambigus (pas de 0/O, 1/I/L) pour le code de secours.
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val appCtx = context.applicationContext
            val masterKey = MasterKey.Builder(appCtx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val p = EncryptedSharedPreferences.create(
                appCtx,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            cached = p
            return p
        }
    }

    // ── État ────────────────────────────────────────────────────────────────────

    fun isLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_LOCK_ENABLED, false)

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_BIOMETRIC, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_BIOMETRIC, enabled).apply()
    }

    // ── Schéma ───────────────────────────────────────────────────────────────────

    /** Définit (ou remplace) le schéma : empreinte salée + activation du verrou. */
    fun setPattern(context: Context, pattern: List<Int>) {
        val salt = randomSalt()
        prefs(context).edit()
            .putString(K_PATTERN_HASH, hash(serialize(pattern), salt))
            .putString(K_PATTERN_SALT, encode(salt))
            .putBoolean(K_LOCK_ENABLED, true)
            .apply()
        resetFailedAttempts(context)
    }

    fun verifyPattern(context: Context, pattern: List<Int>): Boolean {
        val p = prefs(context)
        val salt = p.getString(K_PATTERN_SALT, null)?.let { decode(it) } ?: return false
        val stored = p.getString(K_PATTERN_HASH, null) ?: return false
        return constantTimeEquals(stored, hash(serialize(pattern), salt))
    }

    // ── Code de secours (récupération locale) ─────────────────────────────────────

    /** Génère un code lisible « XXXX-XXXX-XXXX » (alphabet sans ambiguïté). */
    fun generateRecoveryCode(): String {
        val rnd = SecureRandom()
        val sb = StringBuilder()
        repeat(12) { i ->
            if (i > 0 && i % 4 == 0) sb.append('-')
            sb.append(RECOVERY_ALPHABET[rnd.nextInt(RECOVERY_ALPHABET.length)])
        }
        return sb.toString()
    }

    fun storeRecoveryCode(context: Context, code: String) {
        val salt = randomSalt()
        prefs(context).edit()
            .putString(K_RECOVERY_HASH, hash(normalizeRecovery(code), salt))
            .putString(K_RECOVERY_SALT, encode(salt))
            .apply()
    }

    fun verifyRecoveryCode(context: Context, code: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(K_RECOVERY_SALT, null)?.let { decode(it) } ?: return false
        val stored = p.getString(K_RECOVERY_HASH, null) ?: return false
        return constantTimeEquals(stored, hash(normalizeRecovery(code), salt))
    }

    // ── Désactivation ─────────────────────────────────────────────────────────────

    /** Efface TOUTES les données de sécurité (schéma, code de secours, drapeaux). */
    fun disableLock(context: Context) {
        prefs(context).edit().clear().apply()
    }

    // ── Anti-force-brute ───────────────────────────────────────────────────────────

    fun failedAttempts(context: Context): Int = prefs(context).getInt(K_FAILED, 0)

    fun incrementFailedAttempts(context: Context): Int {
        val n = failedAttempts(context) + 1
        prefs(context).edit().putInt(K_FAILED, n).apply()
        return n
    }

    fun resetFailedAttempts(context: Context) {
        prefs(context).edit().putInt(K_FAILED, 0).apply()
    }

    // ── Internes ───────────────────────────────────────────────────────────────────

    private fun serialize(pattern: List<Int>): String = pattern.joinToString("-")

    private fun normalizeRecovery(code: String): String =
        code.uppercase().filter { it.isLetterOrDigit() }

    private fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(spec).encoded
        return encode(bytes)
    }

    /** Comparaison à temps constant (évite les attaques temporelles sur l'empreinte). */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val x = a.toByteArray()
        val y = b.toByteArray()
        if (x.size != y.size) return false
        var result = 0
        for (i in x.indices) result = result or (x[i].toInt() xor y[i].toInt())
        return result == 0
    }
}
