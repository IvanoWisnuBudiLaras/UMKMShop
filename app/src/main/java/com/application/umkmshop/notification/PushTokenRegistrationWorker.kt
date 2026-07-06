package com.application.umkmshop.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.application.umkmshop.data.notification.PushTokenRepository
import java.util.concurrent.TimeUnit

class PushTokenRegistrationWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: PushTokenRepository = PushTokenRepository(),
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = inputData.getString(KEY_TOKEN)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return Result.failure()

        return runCatching {
            if (repository.registerTokenForSignedInUser(token)) {
                Result.success()
            } else {
                Result.retry()
            }
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_TOKEN = "fcm_token"

        fun enqueue(context: Context, token: String) {
            val cleanedToken = token.trim()
            if (cleanedToken.isEmpty()) return

            val request = OneTimeWorkRequestBuilder<PushTokenRegistrationWorker>()
                .setInputData(workDataOf(KEY_TOKEN to cleanedToken))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "push-token-registration-${cleanedToken.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
