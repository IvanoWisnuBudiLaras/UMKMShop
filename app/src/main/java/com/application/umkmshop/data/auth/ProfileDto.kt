package com.application.umkmshop.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("phone")
    val phone: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("city")
    val city: String? = null,
    @SerialName("rating_avg")
    val ratingAvg: Double = 0.0,
    @SerialName("rating_count")
    val ratingCount: Int = 0,
)

fun ProfileDto.toBasicProfile(): BasicProfile =
    BasicProfile(
        id = id,
        name = name,
        phone = phone,
        avatarUrl = avatarUrl,
    )
