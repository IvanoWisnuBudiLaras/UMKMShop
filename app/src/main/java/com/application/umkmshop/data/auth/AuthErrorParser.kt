package com.application.umkmshop.data.auth

import io.github.jan.supabase.exceptions.RestException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AuthErrorParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun mapThrowableToMessage(error: Throwable): String {
        val className = error.javaClass.simpleName
        if (className.contains("HttpRequestException") || className.contains("ConnectException") || className.contains("UnknownHostException")) {
            return "Gagal terhubung ke server. Periksa koneksi internet Anda."
        }

        return when (error) {
            is RestException -> {
                // Supabase API errors usually contain a description
                val description = error.description ?: ""
                when {
                    description.contains("Invalid login credentials", ignoreCase = true) -> "Email atau password salah."
                    description.contains("User not found", ignoreCase = true) -> "Akun tidak ditemukan."
                    description.contains("Email not confirmed", ignoreCase = true) -> "Email belum dikonfirmasi. Silakan cek inbox Anda."
                    description.contains("Password is too short", ignoreCase = true) -> "Password terlalu pendek."
                    else -> error.description ?: "Terjadi kesalahan pada server (API Error)."
                }
            }
            else -> {
                val message = error.message ?: return "Terjadi kesalahan internal."
                
                // Try to parse if it's a JSON error string
                if (message.trim().startsWith("{") && message.trim().endsWith("}")) {
                    try {
                        val element = json.parseToJsonElement(message).jsonObject
                        val code = element["error"]?.jsonPrimitive?.content ?: ""
                        val desc = element["error_description"]?.jsonPrimitive?.content 
                            ?: element["message"]?.jsonPrimitive?.content
                            ?: element["msg"]?.jsonPrimitive?.content

                        when {
                            code == "invalid_grant" && desc?.contains("credentials") == true -> "Email atau password salah."
                            code == "invalid_grant" && desc?.contains("PKCE") == true -> "Sesi login tidak valid (PKCE Error)."
                            code == "access_denied" -> "Akses ditolak oleh pengguna."
                            else -> desc ?: "Terjadi kesalahan internal (Server Error)."
                        }
                    } catch (e: Exception) {
                        "Terjadi kesalahan internal."
                    }
                } else if (message.contains("500") || message.contains("Internal Server Error")) {
                    "Terjadi kesalahan internal pada server."
                } else if (message.contains("401") || message.contains("Unauthorized")) {
                    "Sesi tidak valid atau kadaluarsa. Silakan login kembali."
                } else if (message.contains("404") || message.contains("Not Found")) {
                    when {
                        message.contains("product", ignoreCase = true) -> "Produk tidak ditemukan atau sudah dihapus."
                        message.contains("profile", ignoreCase = true) -> "Profil pengguna tidak ditemukan."
                        else -> "Data tidak ditemukan."
                    }
                } else {
                    message
                }
            }
        }
    }
}
