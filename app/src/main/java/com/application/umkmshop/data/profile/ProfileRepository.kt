package com.application.umkmshop.data.profile

import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository(
    private val supabaseClient: SupabaseClient = SupabaseClientProvider.client,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val client get() = supabaseClient

    suspend fun loadOwnAddress(): UserAddress =
        withContext(ioDispatcher) {
            client.from("profiles")
                .select {
                    filter {
                        eq("id", currentUserId())
                    }
                }
                .decodeSingle<ProfileAddressDto>()
                .toUserAddress()
        }

    suspend fun updateOwnAddress(address: UserAddress): UserAddress =
        withContext(ioDispatcher) {
            val cleanedVillageCode = address.villageCode.cleanedOrNull()
            require(cleanedVillageCode == null || cleanedVillageCode.matches(Regex("\\d{10}"))) {
                "Pilih kelurahan dari hasil pencarian."
            }
            val cleanedPostalCode = address.postalCode.cleanedOrNull()
            require(cleanedPostalCode == null || cleanedPostalCode.matches(Regex("\\d{5}"))) {
                "Kode pos harus 5 digit."
            }

            val updated = client.from("profiles")
                .update(
                    ProfileAddressUpdateDto(
                        city = address.city.cleanedOrNull()?.toDisplayText(),
                        postalCode = cleanedPostalCode,
                        villageCode = cleanedVillageCode,
                    ),
                ) {
                    filter {
                        eq("id", currentUserId())
                    }
                    select()
                }
                .decodeSingle<ProfileAddressDto>()

            updated.toUserAddress()
        }

    internal suspend fun currentUserId(): String =
        client.auth.currentUserOrNull()?.id
            ?: error("Session login tidak tersedia.")

    internal fun String?.cleanedOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    internal fun String.toDisplayText(): String =
        trim()
            .replace(Regex("\\s+"), " ")
            .replaceFirstChar { it.uppercase() }
}
