package com.application.umkmshop.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class UserAddress(
    val city: String?,
    val postalCode: String?,
    val villageCode: String?,
) {
    val hasShippingAddress: Boolean
        get() = !villageCode.isNullOrBlank() && !postalCode.isNullOrBlank()
}

@Serializable
data class ProfileAddressDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("city")
    val city: String? = null,
    @SerialName("postal_code")
    val postalCode: String? = null,
    @SerialName("village_code")
    val villageCode: String? = null,
)

@Serializable
data class ProfileAddressUpdateDto(
    @SerialName("city")
    val city: String?,
    @SerialName("postal_code")
    val postalCode: String?,
    @SerialName("village_code")
    val villageCode: String?,
)

fun ProfileAddressDto.toUserAddress(): UserAddress =
    UserAddress(
        city = city,
        postalCode = postalCode,
        villageCode = villageCode,
    )
