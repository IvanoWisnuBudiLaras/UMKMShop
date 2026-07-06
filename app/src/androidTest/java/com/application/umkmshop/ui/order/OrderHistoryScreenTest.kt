package com.application.umkmshop.ui.order

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.application.umkmshop.ui.theme.UMKMShopTheme
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test

class FakeOrderViewModel : OrderViewModel() {
    fun updateHistoryState(newState: OrderHistoryUiState) {
        _historyState.update { newState }
    }
}

class OrderHistoryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEmptyOrderHistory() {
        val fakeViewModel = FakeOrderViewModel()
        fakeViewModel.updateHistoryState(OrderHistoryUiState(orders = emptyList(), isLoading = false))

        composeTestRule.setContent {
            UMKMShopTheme {
                OrderHistoryScreen(
                    viewModel = fakeViewModel,
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Belum ada transaksi.").assertExists()
    }
}
