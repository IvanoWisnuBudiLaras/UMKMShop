package com.application.umkmshop.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
) {
    data object Auth : AppDestination("auth", "Auth")
    data object BuyerCatalog : AppDestination("buyer-catalog", "Katalog")
    data object Favorites : AppDestination("favorites", "Favorit")
    data object Inbox : AppDestination("inbox", "Inbox")
    data object TransactionHistory : AppDestination("transaction-history", "Transaksi")
    data object Profile : AppDestination("profile", "Profile")
    data object SellerDashboard : AppDestination("seller-dashboard", "Toko Saya")
    data object ProductForm : AppDestination("product-form", "Form Produk")
    data object ProductDetail : AppDestination("product-detail/{productId}", "Detail Produk") {
        const val PRODUCT_ID_ARG = "productId"

        fun createRoute(productId: String): String = "product-detail/$productId"
    }
    data object ChatList : AppDestination("chat-list", "Chat")
    data object ChatRoom : AppDestination("chat-room?productId={productId}&roomId={roomId}", "Ruang Chat") {
        const val PRODUCT_ID_ARG = "productId"
        const val ROOM_ID_ARG = "roomId"

        fun createRoute(): String = "chat-room"

        fun createRoute(productId: String): String = "chat-room?productId=$productId"

        fun createRoute(roomId: String, productId: String): String =
            "chat-room?productId=$productId&roomId=$roomId"
    }
}

enum class UserMode {
    Buyer,
    Seller,
}

val UserMode.startRoute: String
    get() = when (this) {
        UserMode.Buyer -> AppDestination.BuyerCatalog.route
        UserMode.Seller -> AppDestination.SellerDashboard.route
    }
