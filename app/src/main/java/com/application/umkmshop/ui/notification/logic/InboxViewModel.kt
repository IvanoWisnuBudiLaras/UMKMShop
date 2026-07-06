package com.application.umkmshop.ui.notification.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.notification.InboxNotification
import com.application.umkmshop.data.notification.InboxRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InboxUiState(
    val notifications: List<InboxNotification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val message: String? = null,
)

open class InboxViewModel(
    private val repository: InboxRepository = InboxRepository(),
) : ViewModel() {
    protected val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private var subscription: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            runCatching {
                val notifications = repository.listNotifications()
                val unreadCount = repository.unreadCount()
                notifications to unreadCount
            }.onSuccess { (notifications, unreadCount) ->
                _state.update {
                    InboxUiState(
                        notifications = notifications,
                        unreadCount = unreadCount,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = error.userMessage("Gagal memuat inbox."),
                    )
                }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching { repository.markAllRead() }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            notifications = current.notifications.map { it.copy(isRead = true) },
                            unreadCount = 0,
                            message = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(message = error.userMessage("Gagal menandai inbox dibaca."))
                    }
                }
        }
    }

    fun startRealtime() {
        if (subscription?.isActive == true) return
        subscription = repository.subscribeToInbox()
            .let { flow ->
                viewModelScope.launch {
                    flow.collect { notification ->
                        _state.update { current ->
                            current.copy(
                                notifications = (listOf(notification) + current.notifications)
                                    .distinctBy { it.id }
                                    .sortedByDescending { it.createdAt },
                                unreadCount = current.unreadCount + if (notification.isRead) 0 else 1,
                            )
                        }
                    }
                }
            }
    }

    fun stopRealtime() {
        subscription?.cancel()
        subscription = null
    }

    override fun onCleared() {
        stopRealtime()
        super.onCleared()
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback
}
