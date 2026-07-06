package com.application.umkmshop.notification

import com.application.umkmshop.data.notification.PushTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UMKMFirebaseMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = PushTokenRepository()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            val registered = runCatching {
                repository.registerTokenForSignedInUser(token)
            }.getOrDefault(false)
            if (!registered) {
                PushTokenRegistrationWorker.enqueue(applicationContext, token)
            }
        }
    }
}
