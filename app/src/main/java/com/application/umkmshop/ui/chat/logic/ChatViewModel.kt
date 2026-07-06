package com.application.umkmshop.ui.chat.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.chat.ChatMessage
import com.application.umkmshop.data.chat.ChatRepository
import com.application.umkmshop.data.chat.ChatRoomDetail
import com.application.umkmshop.data.chat.ChatRoomSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val rooms: List<ChatRoomSummary> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

data class ChatRoomUiState(
    val room: ChatRoomDetail? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draftMessage: String = "",
    val reviewRating: Int = 0,
    val reviewComment: String = "",
    val hasConfirmedTransaction: Boolean = false,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isSubmittingReview: Boolean = false,
    val message: String? = null,
)

open class ChatViewModel(
    private val repository: ChatRepository = ChatRepository(),
) : ViewModel() {
    protected val _listState = MutableStateFlow(ChatListUiState())
    val listState: StateFlow<ChatListUiState> = _listState.asStateFlow()

    protected val _roomState = MutableStateFlow(ChatRoomUiState())
    val roomState: StateFlow<ChatRoomUiState> = _roomState.asStateFlow()

    private var activeRoomId: String? = null
    private var messageSubscription: Job? = null

    fun refreshRooms() {
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true, message = null) }
            runCatching { repository.listRooms() }
                .onSuccess { rooms ->
                    _listState.update {
                        ChatListUiState(rooms = rooms)
                    }
                }
                .onFailure { error ->
                    _listState.update {
                        it.copy(
                            isLoading = false,
                            message = error.userMessage("Gagal memuat daftar chat."),
                        )
                    }
                }
        }
    }

    fun openRoom(productId: String?, roomId: String?) {
        if (productId.isNullOrBlank() && roomId.isNullOrBlank()) {
            _roomState.update {
                ChatRoomUiState(message = "Ruang chat harus dibuka dari produk atau daftar percakapan.")
            }
            return
        }
        if (activeRoomId == roomId && roomId != null && _roomState.value.room != null) return

        viewModelScope.launch {
            messageSubscription?.cancel()
            activeRoomId = null
            _roomState.update {
                ChatRoomUiState(isLoading = true)
            }
            runCatching {
                if (!roomId.isNullOrBlank()) {
                    repository.getRoom(roomId)
                } else {
                    repository.getOrCreateRoomForProduct(requireNotNull(productId))
                }
            }.onSuccess { room ->
                activeRoomId = room.id
                _roomState.update {
                    ChatRoomUiState(
                        room = room,
                        messages = repository.listMessages(room.id),
                    )
                }
                subscribe(room.id)
            }.onFailure { error ->
                _roomState.update {
                    ChatRoomUiState(
                        isLoading = false,
                        message = error.userMessage("Gagal membuka ruang chat."),
                    )
                }
            }
        }
    }

    fun setDraftMessage(value: String) {
        _roomState.update { it.copy(draftMessage = value, message = null) }
    }

    fun setReviewRating(value: Int) {
        _roomState.update { it.copy(reviewRating = value.coerceIn(0, 5), message = null) }
    }

    fun setReviewComment(value: String) {
        _roomState.update { it.copy(reviewComment = value.take(500), message = null) }
    }

    fun setHasConfirmedTransaction(value: Boolean) {
        _roomState.update { it.copy(hasConfirmedTransaction = value, message = null) }
    }

    fun sendMessage() {
        val roomId = _roomState.value.room?.id ?: return
        val text = _roomState.value.draftMessage.trim()
        if (text.isBlank()) {
            _roomState.update { it.copy(message = "Pesan tidak boleh kosong.") }
            return
        }

        viewModelScope.launch {
            _roomState.update { it.copy(isSending = true, message = null) }
            runCatching { repository.sendMessage(roomId = roomId, messageText = text) }
                .onSuccess { message ->
                    _roomState.update {
                        it.copy(
                            messages = (it.messages + message).distinctBy { item -> item.id },
                            draftMessage = "",
                            isSending = false,
                        )
                    }
                    refreshRooms()
                }
                .onFailure { error ->
                    _roomState.update {
                        it.copy(
                            isSending = false,
                            message = error.userMessage("Gagal mengirim pesan."),
                        )
                    }
                }
        }
    }

    fun submitReview() {
        val room = _roomState.value.room ?: return
        val state = _roomState.value

        if (room.currentUserId != room.buyerId) {
            _roomState.update { it.copy(message = "Hanya pembeli di chat ini yang bisa memberi rating.") }
            return
        }
        if (!state.hasConfirmedTransaction) {
            _roomState.update { it.copy(message = "Konfirmasi transaksi dulu sebelum memberi rating.") }
            return
        }
        if (state.reviewRating !in 1..5) {
            _roomState.update { it.copy(message = "Pilih rating 1 sampai 5.") }
            return
        }

        viewModelScope.launch {
            _roomState.update { it.copy(isSubmittingReview = true, message = null) }
            runCatching {
                repository.submitSellerReview(
                    roomId = room.id,
                    sellerId = room.sellerId,
                    rating = state.reviewRating,
                    comment = state.reviewComment,
                )
            }.onSuccess { review ->
                _roomState.update {
                    it.copy(
                        room = it.room?.copy(myReview = review),
                        reviewRating = 0,
                        reviewComment = "",
                        hasConfirmedTransaction = false,
                        isSubmittingReview = false,
                        message = "Rating terkirim.",
                    )
                }
            }.onFailure { error ->
                _roomState.update {
                    it.copy(
                        isSubmittingReview = false,
                        message = error.userMessage("Gagal mengirim rating."),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        messageSubscription?.cancel()
        super.onCleared()
    }

    private fun subscribe(roomId: String) {
        messageSubscription = repository.subscribeToRoomMessages(roomId)
            .let { flow ->
                viewModelScope.launch {
                    flow.collect { message ->
                        _roomState.update {
                            it.copy(
                                messages = (it.messages + message)
                                    .distinctBy { item -> item.id }
                                    .sortedBy { item -> item.createdAt },
                            )
                        }
                        refreshRooms()
                    }
                }
            }
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback
}
