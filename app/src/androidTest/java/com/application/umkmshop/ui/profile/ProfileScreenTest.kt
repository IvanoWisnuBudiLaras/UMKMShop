package com.application.umkmshop.ui.profile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.application.umkmshop.data.auth.AuthSessionState
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test

class FakeProfileViewModel : ProfileViewModel() {
    fun updateState(newState: ProfileUiState) {
        _state.update { newState }
    }
}

class ProfileScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSaveAddressButtonState() {
        val fakeViewModel = FakeProfileViewModel()
        val session = AuthSessionState(userId = "u1")

        composeTestRule.setContent {
            ProfileScreen(
                session = session,
                viewModel = fakeViewModel,
                unreadInboxCount = 0,
                onOpenInbox = {},
                onOpenTransactions = {},
                onLogout = {}
            )
        }

        // Initial state: Enabled
        composeTestRule.onNodeWithText("Simpan Alamat Ongkir").assertIsEnabled()

        // Transition to saving state
        fakeViewModel.updateState(ProfileUiState(isSavingAddress = true))
        
        // Assert: Button text changes and is disabled
        composeTestRule.onNodeWithText("Menyimpan...").assertIsNotEnabled()
    }
}
