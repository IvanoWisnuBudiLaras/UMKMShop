package com.application.umkmshop.data.notification

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRepositoryTest {
    private lateinit var repository: PushTokenRepository
    private val mockClient = mockk<SupabaseClient>(relaxed = true)
    private val mockAuth = mockk<Auth>(relaxed = true)
    private val mockPostgrest = mockk<Postgrest>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic("io.github.jan.supabase.auth.AuthKt")
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        every { mockClient.auth } returns mockAuth
        every { mockClient.postgrest } returns mockPostgrest
        repository = PushTokenRepository(mockClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `registerTokenForSignedInUser - fails when no session`() = runTest {
        every { mockAuth.currentUserOrNull() } returns null
        val result = repository.registerTokenForSignedInUser("token")
        assertFalse(result)
    }

    @Test
    fun `registerTokenForSignedInUser - success`() = runTest {
        val mockUser: io.github.jan.supabase.auth.user.UserInfo = mockk {
            every { id } returns "u1"
        }
        every { mockAuth.currentUserOrNull() } returns mockUser
        
        val mockQueryBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)
        every { mockClient.from("push_tokens") } returns mockQueryBuilder
        
        val result = repository.registerTokenForSignedInUser("  token  ")
        assertTrue(result)
        
        coVerify { 
            mockQueryBuilder.upsert(
                match<PushTokenUpsertDto> { it.fcmToken == "token" && it.userId == "u1" },
                any()
            ) 
        }
    }

    @Test
    fun `registerTokenForSignedInUser - ignores empty token`() = runTest {
        val mockUser: io.github.jan.supabase.auth.user.UserInfo = mockk {
            every { id } returns "u1"
        }
        every { mockAuth.currentUserOrNull() } returns mockUser
        
        val result = repository.registerTokenForSignedInUser("  ")
        assertFalse(result)
    }

    @Test
    fun `deleteCurrentDeviceTokenForSignedInUser - handle no session`() = runTest {
        every { mockAuth.currentUserOrNull() } returns null
        val result = repository.deleteCurrentDeviceTokenForSignedInUser()
        assertFalse(result)
    }
}
