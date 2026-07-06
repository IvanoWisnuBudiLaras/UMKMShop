package com.application.umkmshop.backend.worker

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import java.io.ByteArrayInputStream

interface PushNotifier {
    fun send(token: String, notification: PushNotification)
}

class InvalidPushTokenException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class FirebaseAdminPushNotifier(
    private val messaging: FirebaseMessaging,
) : PushNotifier {
    override fun send(token: String, notification: PushNotification) {
        val message = Message.builder()
            .setToken(token)
            .setNotification(
                Notification.builder()
                    .setTitle(notification.title)
                    .setBody(notification.body)
                    .build(),
            )
            .putAllData(notification.data)
            .build()

        try {
            messaging.send(message)
        } catch (exception: FirebaseMessagingException) {
            if (exception.isInvalidToken()) {
                throw InvalidPushTokenException("FCM token is invalid", exception)
            }
            throw exception
        }
    }
}

fun createFirebaseNotifier(config: WorkerConfig): FirebaseAdminPushNotifier {
    val credentials = config.firebaseServiceAccountJson?.let { rawJson ->
        GoogleCredentials.fromStream(ByteArrayInputStream(rawJson.toByteArray(Charsets.UTF_8)))
    } ?: GoogleCredentials.getApplicationDefault()

    val app = FirebaseApp.getApps().firstOrNull()
        ?: FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build())

    return FirebaseAdminPushNotifier(FirebaseMessaging.getInstance(app))
}

private fun FirebaseMessagingException.isInvalidToken(): Boolean =
    messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
        messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT ||
        messagingErrorCode == MessagingErrorCode.SENDER_ID_MISMATCH
