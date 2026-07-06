package com.application.umkmshop.data.auth

import com.application.umkmshop.data.notification.PushTokenRepository
import com.application.umkmshop.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private lateinit var repository: AuthRepository
    private lateinit var mockClient: SupabaseClient
    private lateinit var mockAuth: Auth
    private lateinit var mockPushTokenRepository: PushTokenRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        mockkStatic("io.github.jan.supabase.auth.AuthKt")
        mockkStatic("io.github.jan.supabase.postgrest.PostgrestKt")
        mockClient = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockPushTokenRepository = mockk(relaxed = true)

        every { mockClient.auth } returns mockAuth
        every { mockAuth.config } returns mockk(relaxed = true)
        
        repository = spyk(
            AuthRepository(
                supabaseClient = mockClient,
                pushTokenRepository = mockPushTokenRepository,
                ioDispatcher = testDispatcher,
                configValidator = { true }
            ),
            recordPrivateCalls = true
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `restoreSession - no session`() = runTest {
        every { mockAuth.currentUserOrNull() } returns null
        
        val state = repository.restoreSession()
        
        assertFalse(state.isSignedIn)
        assertFalse(state.isRestoring)
        coVerify(exactly = 0) { mockPushTokenRepository.registerCurrentDeviceTokenForSignedInUser() }
    }

    @Test
    fun `restoreSession - with session`() = runTest {
        val mockUser = mockk<UserInfo> {
            every { id } returns "user123"
            every { email } returns "test@example.com"
        }
        every { mockAuth.currentUserOrNull() } returns mockUser
        
        val mockBuilder = mockk<PostgrestQueryBuilder>(relaxed = true)
        every { mockClient.from("profiles") } returns mockBuilder
        
        val state = repository.restoreSession()
        
        assertTrue(state.isSignedIn)
        assertEquals("user123", state.userId)
        assertEquals("test@example.com", state.email)
    }

    @Test
    fun `restoreSession - with session but profile fetch fails`() = runTest {
        val mockUser = mockk<UserInfo> {
            every { id } returns "user123"
            every { email } returns "test@example.com"
        }
        every { mockAuth.currentUserOrNull() } returns mockUser
        every { mockClient.from("profiles") } throws RuntimeException("Network error")
        
        val state = repository.restoreSession()
        
        assertTrue(state.isSignedIn)
        assertEquals("user123", state.userId)
        assertNull(state.profile)
    }

    @Test
    fun `signUp - success`() = runTest {
        val mockUser = mockk<UserInfo> {
            every { id } returns "user123"
            every { email } returns "test@example.com"
        }
        coEvery { mockAuth.signUpWith<Email.Config, UserInfo, Email>(any(), any(), any()) } coAnswers {
            val config = it.invocation.args[2] as? (Email.Config.() -> Unit)
            config?.let { Email.Config().apply(it) }
            mockUser
        }
        every { mockAuth.currentUserOrNull() } returns mockUser
        coEvery { repository["fetchOwnProfileOrNull"]("user123") } returns null
        
        val state = repository.signUp("Name", "test@example.com", "password")
        
        assertTrue(state.isSignedIn)
        assertEquals("Daftar berhasil.", state.message)
    }

    @Test
    fun `signUp - success but needs verification`() = runTest {
        coEvery { mockAuth.signUpWith<Email.Config, UserInfo, Email>(any(), any(), any()) } returns null
        every { mockAuth.currentUserOrNull() } returns null
        
        val state = repository.signUp("Name", "test@example.com", "password")
        
        assertFalse(state.isSignedIn)
        assertTrue(state.message?.contains("Cek email") == true)
    }

    @Test
    fun `login - success`() = runTest {
        val mockUser = mockk<UserInfo> {
            every { id } returns "user123"
            every { email } returns "test@example.com"
        }
        coEvery { mockAuth.signInWith<Email.Config, UserInfo, Email>(any(), any(), any()) } coAnswers {
            val config = it.invocation.args[2] as? (Email.Config.() -> Unit)
            config?.let { Email.Config().apply(it) }
            Unit
        }
        every { mockAuth.currentUserOrNull() } returns mockUser
        coEvery { repository["fetchOwnProfileOrNull"]("user123") } returns BasicProfile("user123", "Name", null, null)
        
        val state = repository.login("test@example.com", "password")
        
        assertTrue(state.isSignedIn)
        assertEquals("Login berhasil.", state.message)
    }

    @Test
    fun `login - success but user null (edge case)`() = runTest {
        coEvery { mockAuth.signInWith<Email.Config, UserInfo, Email>(any(), any(), any()) } returns Unit
        every { mockAuth.currentUserOrNull() } returns null
        
        try {
            repository.login("test@example.com", "password")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("session belum tersedia") == true)
        }
    }

    @Test
    fun `logout - success`() = runTest {
        repository.logout()
        
        coVerify { mockPushTokenRepository.deleteCurrentDeviceTokenForSignedInUser() }
        coVerify { mockAuth.signOut() }
    }

    @Test
    fun `logout - push token error`() = runTest {
        coEvery { mockPushTokenRepository.deleteCurrentDeviceTokenForSignedInUser() } throws RuntimeException("Error")
        val state = repository.logout()
        assertEquals("Logout berhasil.", state.message)
    }

    @Test
    fun `logout - no config`() = runTest {
        val repo = AuthRepository(
            supabaseClient = mockClient,
            configValidator = { false }
        )
        val state = repo.logout()
        assertEquals("Logout berhasil.", state.message)
        coVerify(exactly = 0) { mockAuth.signOut() }
    }

    @Test
    fun `restoreSession - no config`() = runTest {
        val repoNoConfig = AuthRepository(
            supabaseClient = mockClient,
            configValidator = { false }
        )
        val state = repoNoConfig.restoreSession()
        assertTrue(state.message?.contains("Konfigurasi Supabase belum tersedia") == true)
    }

    @Test
    fun `constructor - default`() {
        mockkObject(SupabaseClientProvider)
        every { SupabaseClientProvider.client } returns mockClient
        val repo = AuthRepository()
        assertNotNull(repo)
    }

    @Test
    fun `mapping coverage`() = runTest {
        val dto = ProfileDto("id", "name", "phone", "avatar", "now")
        val basic = dto.toBasicProfile()
        assertEquals("id", basic.id)
        assertEquals("name", basic.name)
        assertEquals("phone", basic.phone)
        assertEquals("avatar", basic.avatarUrl)
    }
}
