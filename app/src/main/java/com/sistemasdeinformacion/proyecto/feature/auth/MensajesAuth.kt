package com.sistemasdeinformacion.proyecto.feature.auth

import com.sistemasdeinformacion.proyecto.data.repo.ErrorAuth

internal const val MSJ_CORREO_INVALIDO = "Escribe un correo válido."
internal const val MSJ_CONTRASENA_INVALIDA = "Usa al menos 8 caracteres, con letras y números."
internal const val MSJ_NO_COINCIDEN = "Las contraseñas no coinciden."

/** Mensaje corto para el chofer; [sinRed] depende de lo que se estaba haciendo. */
internal fun mensajeAuth(e: Throwable, sinRed: String): String = when (e) {
    ErrorAuth.CredencialesInvalidas -> "Correo o contraseña incorrectos."
    ErrorAuth.SinConexion -> sinRed
    ErrorAuth.CorreoRegistrado -> "Ya existe una cuenta con ese correo. Inicia sesión."
    ErrorAuth.ContrasenaDebil -> "Esa contraseña es muy fácil de adivinar. Prueba con otra."
    ErrorAuth.MismaContrasena -> "La contraseña nueva debe ser distinta de la anterior."
    ErrorAuth.DemasiadosIntentos -> "Demasiados intentos. Espera unos minutos y vuelve a probar."
    ErrorAuth.CuentaInactiva -> "Tu cuenta está desactivada. Habla con el administrador."
    ErrorAuth.EnlaceVencido -> "El enlace venció o ya se usó. Pide uno nuevo."
    ErrorAuth.SinServidor -> "Falta configurar el servidor de la app."
    is ErrorAuth.DatosSinEnviar -> "En este teléfono hay registros de ${e.nombre} sin enviar. Que esa persona entre primero para enviarlos."
    else -> "No se pudo completar. Intenta de nuevo."
}
