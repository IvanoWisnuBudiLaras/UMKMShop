package com.application.umkmshop.ui.product

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.application.umkmshop.data.product.SellerProduct
import com.application.umkmshop.ui.theme.UMKMShopTheme
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test

class FakeSellerProductViewModel : SellerProductViewModel() {
    fun updateState(newState: SellerProductUiState) {
        _state.update { newState }
    }
}

class SellerDashboardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEmptyDashboardMessage() {
        val fakeViewModel = FakeSellerProductViewModel()
        fakeViewModel.updateState(SellerProductUiState(products = emptyList(), isLoading = false))

        composeTestRule.setContent {
            UMKMShopTheme {
                SellerDashboardScreen(
                    viewModel = fakeViewModel,
                    unreadInboxCount = 0,
                    onAddProduct = {},
                    onEditProduct = {},
                    onOpenChats = {},
                    onOpenInbox = {},
                    onOpenTransactions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Belum ada produk.").assertExists()
    }

    @Test
    fun testProductListDisplay() {
        val products = listOf(
            SellerProduct("p1", "s1", "Beras UMKM", 15000.0, "Beras pulen", "Food", "active", emptyList())
        )
        val fakeViewModel = FakeSellerProductViewModel()
        fakeViewModel.updateState(SellerProductUiState(products = products, isLoading = false))

        composeTestRule.setContent {
            UMKMShopTheme {
                SellerDashboardScreen(
                    viewModel = fakeViewModel,
                    unreadInboxCount = 0,
                    onAddProduct = {},
                    onEditProduct = {},
                    onOpenChats = {},
                    onOpenInbox = {},
                    onOpenTransactions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Beras UMKM").assertExists()
    }
}
