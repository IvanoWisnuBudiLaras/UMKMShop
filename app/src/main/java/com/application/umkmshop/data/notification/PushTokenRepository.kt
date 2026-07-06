package com.application.umkmshop.data.notification

import android.os.Build
import com.application.umkmshop.data.supabase.SupabaseClientProvider
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PushTokenRepository(
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client
) {
    private val client
        get() = supabaseClient

    suspend fun registerCurrentDeviceTokenForSignedInUser(): Boolean =
        withContext(Dispatchers.IO) {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }
                .getOrNull()
                ?: return@withContext false

            registerTokenForSignedInUser(token)
        }

    suspend fun registerTokenForSignedInUser(token: String): Boolean =
        withContext(Dispatchers.IO) {
            val userId = client.auth.currentUserOrNull()?.id ?: return@withContext false
            val cleanedToken = token.trim()
            if (cleanedToken.isEmpty()) return@withContext false

            client.from("push_tokens").upsert(
                PushTokenUpsertDto(
                    userId = userId,
                    fcmToken = cleanedToken,
                    deviceInfo = buildDeviceInfo(),
                    updatedAt = nowIsoUtc(),
                ),
            ) {
                onConflict = "user_id,fcm_token"
            }
            true
        }

    suspend fun deleteCurrentDeviceTokenForSignedInUser(): Boolean =
        withContext(Dispatchers.IO) {
            val userId = client.auth.currentUserOrNull()?.id ?: return@withContext false
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@withContext false

            client.from("push_tokens").delete {
                filter {
                    eq("user_id", userId)
                    eq("fcm_token", token)
                }
            }
            true
        }

    private fun buildDeviceInfo(): String =
        listOf(
            "android=${Build.VERSION.SDK_INT}",
            "manufacturer=${Build.MANUFACTURER}",
            "model=${Build.MODEL}",
        ).joinToString("; ")

    private fun nowIsoUtc(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase task failed."),
                )
            }
        }
    }
