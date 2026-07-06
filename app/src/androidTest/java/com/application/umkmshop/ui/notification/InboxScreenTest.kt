package com.application.umkmshop.ui.notification

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.application.umkmshop.ui.theme.UMKMShopTheme
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test

class FakeInboxViewModel : InboxViewModel() {
    fun updateState(newState: InboxUiState) {
        _state.update { newState }
    }
}

class InboxScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEmptyInboxMessage() {
        val fakeViewModel = FakeInboxViewModel()
        fakeViewModel.updateState(InboxUiState(notifications = emptyList(), isLoading = false))

        composeTestRule.setContent {
            UMKMShopTheme {
                InboxScreen(
                    viewModel = fakeViewModel,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Belum ada notifikasi pesanan.").assertExists()
    }
}
