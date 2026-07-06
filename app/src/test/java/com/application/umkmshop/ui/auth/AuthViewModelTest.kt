package com.application.umkmshop.ui.auth

import com.application.umkmshop.data.auth.AuthRepository
import com.application.umkmshop.data.auth.AuthSessionState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private lateinit var viewModel: AuthViewModel
    private lateinit var repository: AuthRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        // Ensure restoreSession returns something so init doesn't hang or fail
        coEvery { repository.restoreSession() } returns AuthSessionState(isRestoring = false)
        viewModel = AuthViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state - default`() = runTest {
        // Initial state during init
        val state = viewModel.state.value
        // After init completes (due to StandardTestDispatcher and no advancement yet, 
        // it might still be in the middle of restoreSession if we didn't advance)
        advanceUntilIdle()
        val finalState = viewModel.state.value
        assertFalse(finalState.session.isRestoring)
    }

    @Test
    fun `setters - update state`() {
        viewModel.setEmail("test@mail.com")
        viewModel.setPassword("pass")
        viewModel.setName("Name")
        viewModel.setSignup(true)
        
        val state = viewModel.state.value
        assertEquals("test@mail.com", state.email)
        assertEquals("pass", state.password)
        assertEquals("Name", state.name)
        assertTrue(state.isSignup)
    }

    @Test
    fun `submit login - success`() = runTest {
        val mockState = AuthSessionState(userId = "u1", email = "t@m.com", isRestoring = false)
        coEvery { repository.login(any(), any()) } returns mockState
        
        viewModel.setEmail("t@m.com")
        viewModel.setPassword("123")
        viewModel.setSignup(false)
        viewModel.submit()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("u1", state.session.userId)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `submit login - failure`() = runTest {
        coEvery { repository.login(any(), any()) } throws Exception("Auth Error")
        
        viewModel.setEmail("t@m.com")
        viewModel.setPassword("123")
        viewModel.setSignup(false)
        viewModel.submit()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Auth Error", state.session.message)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `submit signup - validation failure`() = runTest {
        viewModel.setEmail("  ")
        viewModel.setSignup(true)
        viewModel.submit()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Nama, email, dan password wajib diisi sesuai mode.", state.session.message)
    }

    @Test
    fun `submit signup - success`() = runTest {
        val mockState = AuthSessionState(userId = "u1", message = "OK", isRestoring = false)
        coEvery { repository.signUp(any(), any(), any()) } returns mockState
        
        viewModel.setName("User")
        viewModel.setEmail("t@m.com")
        viewModel.setPassword("123")
        viewModel.setSignup(true)
        viewModel.submit()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("u1", state.session.userId)
        assertEquals("OK", state.session.message)
    }

    @Test
    fun `logout - success`() = runTest {
        coEvery { repository.logout() } returns AuthSessionState(userId = null, isRestoring = false)
        
        viewModel.logout()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertFalse(state.session.isSignedIn)
    }
}
