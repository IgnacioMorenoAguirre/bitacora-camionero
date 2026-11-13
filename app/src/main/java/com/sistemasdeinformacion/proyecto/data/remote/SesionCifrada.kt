package com.sistemasdeinformacion.proyecto.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.almacenSesion by preferencesDataStore("sesion")

/**
 * Guarda la sesión de Supabase cifrada con AES-GCM. La llave vive en el Android Keystore
 * (no se puede extraer del teléfono), así que el token no queda legible en el almacenamiento.
 */
class SesionCifrada(private val context: Context) : SessionManager {

    private val clave = stringPreferencesKey("sesion_cifrada")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        val texto = json.encodeToString(UserSession.serializer(), session)
        val cifrado = cifrar(texto.toByteArray(Charsets.UTF_8))
        context.almacenSesion.edit { it[clave] = cifrado }
    }

    /** La librería espera una excepción cuando no hay sesión (usa loadSessionOrNull para la versión nula). */
    override suspend fun loadSession(): UserSession {
        val guardado = context.almacenSesion.data.first()[clave] ?: throw IllegalStateException("No hay sesión guardada")
        return json.decodeFromString(UserSession.serializer(), String(descifrar(guardado), Charsets.UTF_8))
    }

    override suspend fun deleteSession() {
        context.almacenSesion.edit { it.remove(clave) }
    }

    suspend fun haySesion(): Boolean = context.almacenSesion.data.first()[clave] != null

    // ---------- cifrado ----------
    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun cifrar(datos: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.ENCRYPT_MODE, llave()) }
        val iv = cipher.iv
        val salida = cipher.doFinal(datos)
        return Base64.encodeToString(iv + salida, Base64.NO_WRAP)
    }

    private fun descifrar(texto: String): ByteArray {
        val todo = Base64.decode(texto, Base64.NO_WRAP)
        val iv = todo.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMACION).apply { init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, iv)) }
        return cipher.doFinal(todo.copyOfRange(IV_BYTES, todo.size))
    }

    private companion object {
        const val ALIAS = "bitacora_sesion"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
