// TMS username+password at rest: AES-GCM key lives in AndroidKeyStore (no GMS), only the
// ciphertext (iv + bytes, base64) goes into DataStore.
package bd.sicip.qavisit.data.tms

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TmsCredentials(val username: String, val password: String)

interface TmsCredentialStore {
    suspend fun save(credentials: TmsCredentials)
    suspend fun load(): TmsCredentials?
    suspend fun clear()
}

private val Context.tmsDataStore by preferencesDataStore(name = "tms_vault")
private val CIPHERTEXT = stringPreferencesKey("credentials")
private const val KEY_ALIAS = "tms_vault_key"
private const val KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val SEPARATOR = '\n' // usernames never hold a newline; the password may, so split on the first one

class TmsVault(private val context: Context) : TmsCredentialStore {
    override suspend fun save(credentials: TmsCredentials) {
        val plain = "${credentials.username}$SEPARATOR${credentials.password}".toByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val packed = cipher.iv + cipher.doFinal(plain)
        context.tmsDataStore.edit { it[CIPHERTEXT] = Base64.encodeToString(packed, Base64.NO_WRAP) }
    }

    // null when nothing is stored or the key/ciphertext no longer match (e.g. restored backup).
    override suspend fun load(): TmsCredentials? {
        val stored = context.tmsDataStore.data.first()[CIPHERTEXT] ?: return null
        return runCatching {
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, 12)
            val cipher = Cipher.getInstance(TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_BITS, iv)) }
            val plain = String(cipher.doFinal(packed, 12, packed.size - 12))
            val split = plain.indexOf(SEPARATOR)
            TmsCredentials(plain.substring(0, split), plain.substring(split + 1))
        }.getOrNull()
    }

    override suspend fun clear() {
        context.tmsDataStore.edit { it.clear() }
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }.generateKey()
    }
}
