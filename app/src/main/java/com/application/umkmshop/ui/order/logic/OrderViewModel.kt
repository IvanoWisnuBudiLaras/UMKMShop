package com.application.umkmshop.ui.order.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.order.OrderInput
import com.application.umkmshop.data.order.OrderRepository
import com.application.umkmshop.data.order.OrderTransaction
import com.application.umkmshop.data.shipping.ShippingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateOrderUiState(
    val itemNote: String = "",
    val weightGrams: String = "",
    val subtotal: String = "",
    val manualShippingCost: String = "",
    val estimatedShippingCost: Double? = null,
    val shippingServiceName: String? = null,
    val requiresManualShipping: Boolean = false,
    val isSaving: Boolean = false,
    val isEstimatingShipping: Boolean = false,
    val message: String? = null,
)

data class OrderHistoryUiState(
    val orders: List<OrderTransaction> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

open class OrderViewModel(
    private val repository: OrderRepository = OrderRepository(),
    private val shippingRepository: ShippingRepository = ShippingRepository(),
) : ViewModel() {
    protected val _createState = MutableStateFlow(CreateOrderUiState())
    val createState: StateFlow<CreateOrderUiState> = _createState.asStateFlow()

    protected val _historyState = MutableStateFlow(OrderHistoryUiState())
    val historyState: StateFlow<OrderHistoryUiState> = _historyState.asStateFlow()

    fun setItemNote(value: String) {
        _createState.update { it.copy(itemNote = value.take(300), message = null) }
    }

    fun setWeightGrams(value: String) {
        _createState.update {
            it.copy(
                weightGrams = value.digitsOnly().take(9),
                estimatedShippingCost = null,
                shippingServiceName = null,
                requiresManualShipping = false,
                manualShippingCost = "",
                message = null,
            )
        }
    }

    fun setSubtotal(value: String) {
        _createState.update { it.copy(subtotal = value.priceInput(), message = null) }
    }

    fun setManualShippingCost(value: String) {
        _createState.update {
            it.copy(
                manualShippingCost = value.priceInput(),
                estimatedShippingCost = null,
                shippingServiceName = null,
                message = null,
            )
        }
    }

    fun createOrder(
        input: OrderInput,
        onCreated: () -> Unit = {},
    ) {
        if (_createState.value.isSaving) return
        val state = _createState.value
        val subtotal = state.subtotal.toDoubleOrNull()
        val weight = state.weightGrams.toIntOrNull()
        val manualShippingCost = state.manualShippingCost.toDoubleOrNull()
        val validationMessage = when {
            subtotal == null -> "Subtotal harus diisi angka."
            subtotal < 0 -> "Subtotal tidak boleh negatif."
            state.weightGrams.isNotBlank() && weight == null -> "Berat barang harus berupa angka gram."
            weight != null && weight <= 0 -> "Berat barang harus lebih dari 0 gram."
            weight == null -> "Berat barang wajib diisi untuk hitung ongkir."
            state.requiresManualShipping && manualShippingCost == null -> "Isi ongkir manual untuk lanjut."
            manualShippingCost != null && manualShippingCost < 0 -> "Ongkir tidak boleh negatif."
            else -> null
        }
        if (validationMessage != null) {
            _createState.update { it.copy(message = validationMessage) }
            return
        }

        viewModelScope.launch {
            _createState.update {
                it.copy(
                    isSaving = true,
                    isEstimatingShipping = !it.requiresManualShipping && it.estimatedShippingCost == null,
                    message = null,
                )
            }
            runCatching {
                val shippingCost = resolveShippingCost(input, requireNotNull(weight), manualShippingCost)
                repository.createOrder(
                    input.copy(
                        itemNote = state.itemNote,
                        weightGrams = weight,
                        subtotal = requireNotNull(subtotal),
                        shippingCost = shippingCost,
                    ),
                )
            }.onSuccess { order ->
                _createState.update {
                    CreateOrderUiState(
                        message = if (order.xenditInvoiceUrl.isNullOrBlank()) {
                            "Invoice pending dibuat."
                        } else {
                            "Invoice Xendit dibuat."
                        },
                    )
                }
                refreshOrders()
                onCreated()
            }.onFailure { error ->
                _createState.update {
                    it.copy(
                        isSaving = false,
                        isEstimatingShipping = false,
                        message = error.userMessage("Gagal membuat invoice."),
                    )
                }
            }
        }
    }

    private suspend fun resolveShippingCost(
        input: OrderInput,
        weightGrams: Int,
        manualShippingCost: Double?,
    ): Double {
        if (_createState.value.requiresManualShipping) {
            return requireNotNull(manualShippingCost)
        }

        val existingEstimate = _createState.value.estimatedShippingCost
        if (existingEstimate != null) return existingEstimate

        val (sellerVillageCode, buyerVillageCode) = repository.getParticipantVillageCodes(
            buyerId = input.buyerId,
            sellerId = input.sellerId,
        )
        if (sellerVillageCode.isNullOrBlank() || buyerVillageCode.isNullOrBlank()) {
            _createState.update {
                it.copy(
                    isSaving = false,
                    isEstimatingShipping = false,
                    requiresManualShipping = true,
                    message = "Alamat penjual atau pembeli belum lengkap. Isi ongkir manual untuk lanjut.",
                )
            }
            error("Alamat penjual atau pembeli belum lengkap. Isi ongkir manual untuk lanjut.")
        }

        return runCatching {
            shippingRepository.estimateShipping(
                originVillageCode = sellerVillageCode,
                destinationVillageCode = buyerVillageCode,
                weightGrams = weightGrams,
            )
        }.fold(
            onSuccess = { estimate ->
                _createState.update {
                    it.copy(
                        estimatedShippingCost = estimate.cost,
                        shippingServiceName = estimate.serviceName,
                        isEstimatingShipping = false,
                    )
                }
                estimate.cost
            },
            onFailure = { error ->
                _createState.update {
                    it.copy(
                        isSaving = false,
                        isEstimatingShipping = false,
                        requiresManualShipping = true,
                        message = error.userMessage("Layanan ongkir belum tersedia. Isi ongkir manual untuk lanjut."),
                    )
                }
                throw error
            },
        )
    }

    fun refreshOrders() {
        viewModelScope.launch {
            _historyState.update { it.copy(isLoading = true, message = null) }
            runCatching { repository.listOrders() }
                .onSuccess { orders ->
                    _historyState.update {
                        OrderHistoryUiState(orders = orders)
                    }
                }
                .onFailure { error ->
                    _historyState.update {
                        it.copy(
                            isLoading = false,
                            message = error.userMessage("Gagal memuat riwayat transaksi."),
                        )
                    }
                }
        }
    }

    private fun String.digitsOnly(): String =
        filter { it.isDigit() }

    private fun String.priceInput(): String =
        filter { it.isDigit() || it == '.' }
            .let { cleaned ->
                val firstDot = cleaned.indexOf('.')
                if (firstDot == -1) cleaned else {
                    cleaned.take(firstDot + 1) + cleaned.drop(firstDot + 1).replace(".", "")
                }
            }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback
}
