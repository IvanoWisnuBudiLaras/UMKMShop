package com.application.umkmshop.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.application.umkmshop.data.auth.AuthRepository
import com.application.umkmshop.data.auth.AuthSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthFormState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val isSignup: Boolean = false,
    val isSubmitting: Boolean = false,
    val session: AuthSessionState = AuthSessionState(),
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(AuthFormState())
    val state: StateFlow<AuthFormState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setEmail(value: String) = _state.update { it.copy(email = value) }

    fun setPassword(value: String) = _state.update { it.copy(password = value) }

    fun setSignup(value: Boolean) = _state.update { it.copy(isSignup = value, session = it.session.copy(message = null)) }

    fun restoreSession() {
        viewModelScope.launch {
            _state.update { it.copy(session = it.session.copy(isRestoring = true, message = null)) }
            val restored = repository.restoreSession()
            _state.update { it.copy(session = restored) }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.email.isBlank() || current.password.isBlank() || (current.isSignup && current.name.isBlank())) {
            _state.update {
                it.copy(session = it.session.copy(isRestoring = false, message = "Nama, email, dan password wajib diisi sesuai mode."))
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, session = it.session.copy(message = null)) }
            val result = runCatching {
                if (current.isSignup) {
                    repository.signUp(
                        name = current.name,
                        email = current.email,
                        password = current.password,
                    )
                } else {
                    repository.login(
                        email = current.email,
                        password = current.password,
                    )
                }
            }.getOrElse { error ->
                AuthSessionState(isRestoring = false, message = error.message ?: "Auth gagal.")
            }

            _state.update { it.copy(isSubmitting = false, session = result) }
        }
    }

    fun logout(onLoggedOut: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val logoutResult = runCatching { repository.logout() }
            val result = logoutResult
                .getOrElse { error -> AuthSessionState(isRestoring = false, message = error.message ?: "Logout gagal.") }
            _state.update { it.copy(isSubmitting = false, session = result, password = "") }
            if (logoutResult.isSuccess) {
                onLoggedOut()
            }
        }
    }
}
