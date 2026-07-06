package com.application.umkmshop.data.auth

import com.application.umkmshop.data.notification.PushTokenRepository
import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client,
    private val pushTokenRepository: PushTokenRepository = PushTokenRepository(supabaseClient),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val configValidator: () -> Boolean = { SupabaseClientProvider.hasValidConfig }
) {
    private val client get() = supabaseClient

    suspend fun restoreSession(): AuthSessionState =
        withContext(ioDispatcher) {
            if (!hasClientConfig()) {
                return@withContext AuthSessionState(
                    isRestoring = false,
                    message = "Konfigurasi Supabase belum tersedia.",
                )
            }

            val user = client.auth.currentUserOrNull()
                ?: return@withContext AuthSessionState(isRestoring = false)

            registerPushTokenIfPossible()

            AuthSessionState(
                isRestoring = false,
                userId = user.id,
                email = user.email,
                profile = fetchOwnProfile(user.id),
            )
        }

    suspend fun signUp(name: String, email: String, password: String): AuthSessionState =
        withContext(ioDispatcher) {
            require(hasClientConfig()) { "Konfigurasi Supabase belum tersedia." }
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
                data = buildJsonObject {
                    put("name", name.trim())
                }
            }

            val user = client.auth.currentUserOrNull()
            if (user != null) {
                registerPushTokenIfPossible()
            }
            AuthSessionState(
                isRestoring = false,
                userId = user?.id,
                email = user?.email ?: email.trim(),
                profile = user?.id?.let { fetchOwnProfileOrNull(it) },
                message = if (user == null) {
                    "Daftar berhasil. Cek email untuk verifikasi sebelum login."
                } else {
                    "Daftar berhasil."
                },
            )
        }

    suspend fun login(email: String, password: String): AuthSessionState =
        withContext(ioDispatcher) {
            require(hasClientConfig()) { "Konfigurasi Supabase belum tersedia." }
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }

            val user = requireNotNull(client.auth.currentUserOrNull()) {
                "Login berhasil tetapi session belum tersedia."
            }
            registerPushTokenIfPossible()
            AuthSessionState(
                isRestoring = false,
                userId = user.id,
                email = user.email,
                profile = fetchOwnProfile(user.id),
                message = "Login berhasil.",
            )
        }

    suspend fun logout(): AuthSessionState =
        withContext(ioDispatcher) {
            runCatching {
                pushTokenRepository.deleteCurrentDeviceTokenForSignedInUser()
            }
            if (hasClientConfig()) {
                client.auth.signOut()
            }
            AuthSessionState(isRestoring = false, message = "Logout berhasil.")
        }

    private suspend fun fetchOwnProfile(userId: String): BasicProfile? =
        fetchOwnProfileOrNull(userId)

    private suspend fun fetchOwnProfileOrNull(userId: String): BasicProfile? =
        runCatching {
            client.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<ProfileDto>()
                .toBasicProfile()
        }.getOrNull()

    private fun hasClientConfig(): Boolean = configValidator()

    private suspend fun registerPushTokenIfPossible() {
        runCatching {
            pushTokenRepository.registerCurrentDeviceTokenForSignedInUser()
        }
    }
}
