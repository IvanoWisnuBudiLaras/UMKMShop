package com.application.umkmshop.navigation

import org.junit.Test
import kotlin.test.assertEquals

class AppDestinationTest {
    @Test
    fun `test route generation`() {
        assertEquals("auth", AppDestination.Auth.route)
        assertEquals("product-detail/p123", AppDestination.ProductDetail.createRoute("p123"))
        assertEquals("chat-room?productId=p789&roomId=r456", AppDestination.ChatRoom.createRoute("r456", "p789"))
        assertEquals("chat-room?productId=p789", AppDestination.ChatRoom.createRoute("p789"))
        assertEquals("chat-room", AppDestination.ChatRoom.createRoute())
    }

    @Test
    fun `test user modes`() {
        assertEquals("buyer-catalog", UserMode.Buyer.startRoute)
        assertEquals("seller-dashboard", UserMode.Seller.startRoute)
    }
}
