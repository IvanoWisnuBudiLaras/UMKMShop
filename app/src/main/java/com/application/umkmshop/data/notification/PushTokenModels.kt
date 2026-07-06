package com.application.umkmshop.data.notification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushTokenUpsertDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("fcm_token")
    val fcmToken: String,
    @SerialName("device_info")
    val deviceInfo: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)
