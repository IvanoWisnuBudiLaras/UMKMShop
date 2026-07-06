package com.application.umkmshop.ui.profile.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.profile.ProfileRepository
import com.application.umkmshop.data.profile.UserAddress
import com.application.umkmshop.data.shipping.ShippingRepository
import com.application.umkmshop.data.shipping.WilayahItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val city: String = "",
    val postalCode: String = "",
    val villageCode: String = "",
    
    // Hierarchical Wilayah
    val provinces: List<WilayahItem> = emptyList(),
    val regencies: List<WilayahItem> = emptyList(),
    val districts: List<WilayahItem> = emptyList(),
    val villages: List<WilayahItem> = emptyList(),
    
    val selectedProvince: WilayahItem? = null,
    val selectedRegency: WilayahItem? = null,
    val selectedDistrict: WilayahItem? = null,
    val selectedVillage: WilayahItem? = null,

    val isLoading: Boolean = false,
    val isSavingAddress: Boolean = false,
    val message: String? = null,
)

open class ProfileViewModel(
    private val profileRepository: ProfileRepository = ProfileRepository(),
    private val shippingRepository: ShippingRepository = ShippingRepository(),
) : ViewModel() {
    protected val _state = MutableStateFlow(ProfileUiState())
    open val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadProvinces()
    }

    private fun loadProvinces() {
        viewModelScope.launch {
            val provinces = shippingRepository.getProvinces()
            _state.update { it.copy(provinces = provinces) }
        }
    }

    fun selectProvince(province: WilayahItem) {
        _state.update { 
            it.copy(
                selectedProvince = province,
                selectedRegency = null,
                selectedDistrict = null,
                selectedVillage = null,
                regencies = emptyList(),
                districts = emptyList(),
                villages = emptyList()
            )
        }
        viewModelScope.launch {
            val regencies = shippingRepository.getRegencies(province.code)
            _state.update { it.copy(regencies = regencies) }
        }
    }

    fun selectRegency(regency: WilayahItem) {
        _state.update { 
            it.copy(
                selectedRegency = regency,
                selectedDistrict = null,
                selectedVillage = null,
                districts = emptyList(),
                villages = emptyList()
            )
        }
        viewModelScope.launch {
            val districts = shippingRepository.getDistricts(regency.code)
            _state.update { it.copy(districts = districts) }
        }
    }

    fun selectDistrict(district: WilayahItem) {
        _state.update { 
            it.copy(
                selectedDistrict = district,
                selectedVillage = null,
                villages = emptyList()
            )
        }
        viewModelScope.launch {
            val villages = shippingRepository.getVillages(district.code)
            _state.update { it.copy(villages = villages) }
        }
    }

    fun selectVillageItem(village: WilayahItem) {
        _state.update { 
            it.copy(
                selectedVillage = village,
                villageCode = village.code,
                city = it.selectedRegency?.name.orEmpty()
            )
        }
    }

    fun setPostalCode(value: String) {
        _state.update { it.copy(postalCode = value.take(5).filter { it.isDigit() }) }
    }

    fun loadAddress() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            runCatching { profileRepository.loadOwnAddress() }
                .onSuccess { address ->
                    _state.update {
                        it.copy(
                            city = address.city.orEmpty(),
                            postalCode = address.postalCode.orEmpty(),
                            villageCode = address.villageCode.orEmpty(),
                            isLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = error.userMessage("Gagal memuat alamat."),
                        )
                    }
                }
        }
    }

    fun saveAddress() {
        val current = _state.value
        val validationMessage = when {
            current.villageCode.isBlank() -> "Pilih kelurahan dari hasil pencarian."
            current.postalCode.length != 5 -> "Kode pos harus 5 digit."
            else -> null
        }
        if (validationMessage != null) {
            _state.update { it.copy(message = validationMessage) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSavingAddress = true, message = null) }
            runCatching {
                profileRepository.updateOwnAddress(
                    UserAddress(
                        city = current.city,
                        postalCode = current.postalCode,
                        villageCode = current.villageCode,
                    ),
                )
            }.onSuccess { address ->
                _state.update {
                    it.copy(
                        isSavingAddress = false,
                        message = "Alamat ongkir tersimpan.",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSavingAddress = false,
                        message = error.userMessage("Gagal menyimpan alamat."),
                    )
                }
            }
        }
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback
}
