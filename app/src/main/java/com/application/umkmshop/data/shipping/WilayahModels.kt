package com.application.umkmshop.data.shipping

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WilayahResponse<T>(
    val data: List<T>,
    val meta: WilayahMeta
)

@Serializable
data class WilayahMeta(
    @SerialName("administrative_area_level") val level: Int,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class WilayahItem(
    val code: String,
    val name: String
)
