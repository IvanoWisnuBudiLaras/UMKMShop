package com.application.umkmshop.data.auth

data class BasicProfile(
    val id: String,
    val name: String,
    val phone: String?,
    val avatarUrl: String?,
)

data class AuthSessionState(
    val isRestoring: Boolean = true,
    val userId: String? = null,
    val email: String? = null,
    val profile: BasicProfile? = null,
    val message: String? = null,
) {
    val isSignedIn: Boolean = userId != null
}
