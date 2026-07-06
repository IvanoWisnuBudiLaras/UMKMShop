package com.application.umkmshop.data.shipping

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class VillageSearchResult(
    val villageCode: String,
    val postalCode: String?,
    val villageName: String,
    val districtName: String?,
    val cityName: String?,
    val provinceName: String?,
) {
    val label: String
        get() = listOf(villageName, districtName, cityName, provinceName)
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .joinToString(", ")
}

data class ShippingEstimate(
    val serviceName: String,
    val cost: Double,
    val etd: String?,
)

@Serializable
data class ShippingFunctionRequest(
    @SerialName("action")
    val action: String,
    @SerialName("query")
    val query: String? = null,
    @SerialName("originVillageCode")
    val originVillageCode: String? = null,
    @SerialName("destinationVillageCode")
    val destinationVillageCode: String? = null,
    @SerialName("weightGrams")
    val weightGrams: Int? = null,
)

@Serializable
data class VillageSearchResponse(
    @SerialName("villages")
    val villages: List<VillageSearchResultDto> = emptyList(),
)

@Serializable
data class VillageSearchResultDto(
    @SerialName("villageCode")
    val villageCode: String,
    @SerialName("postalCode")
    val postalCode: String? = null,
    @SerialName("villageName")
    val villageName: String,
    @SerialName("districtName")
    val districtName: String? = null,
    @SerialName("cityName")
    val cityName: String? = null,
    @SerialName("provinceName")
    val provinceName: String? = null,
)

@Serializable
data class ShippingEstimateResponse(
    @SerialName("estimate")
    val estimate: ShippingEstimateDto,
)

@Serializable
data class ShippingEstimateDto(
    @SerialName("serviceName")
    val serviceName: String,
    @SerialName("cost")
    val cost: Double,
    @SerialName("etd")
    val etd: String? = null,
)

fun VillageSearchResultDto.toVillageSearchResult(): VillageSearchResult =
    VillageSearchResult(
        villageCode = villageCode,
        postalCode = postalCode,
        villageName = villageName,
        districtName = districtName,
        cityName = cityName,
        provinceName = provinceName,
    )

fun ShippingEstimateDto.toShippingEstimate(): ShippingEstimate =
    ShippingEstimate(
        serviceName = serviceName,
        cost = cost,
        etd = etd,
    )
