package com.application.umkmshop.ui.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.application.umkmshop.data.auth.AuthSessionState
import com.application.umkmshop.ui.theme.UMKMShopTheme
import com.application.umkmshop.ui.AuthShell
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoginSignupToggle() {
        var isSignupMode = false
        composeTestRule.setContent {
            UMKMShopTheme {
                AuthShell(
                    state = AuthFormState(isSignup = isSignupMode),
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onModeChange = { isSignupMode = it },
                    onSubmit = {},
                    onEnterApp = {}
                )
            }
        }

        // Initial: Login mode
        composeTestRule.onNodeWithText("Login").assertExists()
        composeTestRule.onNodeWithText("Belum punya akun? Daftar").assertExists().performClick()

        // Re-render with new state (simulated by re-setting content in this simple test or using a proper state holder)
        // For a more realistic test, we should use a real or fake ViewModel that handles the state.
    }

    @Test
    fun testSubmittingStateDisablesButtons() {
        val submittingState = AuthFormState(
            isSubmitting = true,
            session = AuthSessionState(isRestoring = false)
        )

        composeTestRule.setContent {
            UMKMShopTheme {
                AuthShell(
                    state = submittingState,
                    onNameChange = {},
                    onEmailChange = {},
                    onPasswordChange = {},
                    onModeChange = {},
                    onSubmit = {},
                    onEnterApp = {}
                )
            }
        }

        // Button should be present but disabled (or text might change if implemented, 
        // in AuthShell it's just enabled = !state.isSubmitting)
        composeTestRule.onNodeWithText("Login").assertIsNotEnabled()
    }
}
