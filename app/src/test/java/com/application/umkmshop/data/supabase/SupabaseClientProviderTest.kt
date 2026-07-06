package com.application.umkmshop.data.supabase

import org.junit.Test
import kotlin.test.assertNotNull

class SupabaseClientProviderTest {
    @Test
    fun `hasValidConfig check`() {
        SupabaseClientProvider.hasValidConfig
    }

    @Test
    fun `client access check`() {
        runCatching {
            val client = SupabaseClientProvider.client
            assertNotNull(client)
        }
    }
}
