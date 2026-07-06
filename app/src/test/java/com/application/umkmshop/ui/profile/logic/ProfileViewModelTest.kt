package com.application.umkmshop.ui.profile.logic

import com.application.umkmshop.data.profile.ProfileRepository
import com.application.umkmshop.data.profile.UserAddress
import com.application.umkmshop.data.shipping.ShippingRepository
import com.application.umkmshop.data.shipping.VillageSearchResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private lateinit var viewModel: ProfileViewModel
    private val profileRepository: ProfileRepository = mockk(relaxed = true)
    private val shippingRepository: ShippingRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ProfileViewModel(profileRepository, shippingRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `loadAddress - success`() = runTest {
        val mockAddress = UserAddress("Jakarta", "12345", "v123")
        coEvery { profileRepository.loadOwnAddress() } returns mockAddress
        
        viewModel.loadAddress()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Jakarta", state.city)
        assertEquals("12345", state.postalCode)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadAddress - failure`() = runTest {
        coEvery { profileRepository.loadOwnAddress() } throws Exception("Load Error")
        
        viewModel.loadAddress()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Load Error", state.message)
        assertFalse(state.isLoading)
    }

    @Test
    fun `setVillageQuery - triggers search after delay`() = runTest {
        val mockVillages = listOf(
            VillageSearchResult("v1", "11", "V1", "D1", "C1", "P1")
        )
        coEvery { shippingRepository.searchVillages("Cianjur") } returns mockVillages
        
        viewModel.setVillageQuery("Cianjur")
        
        // Wait for debounce delay (350ms)
        advanceTimeBy(400)
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals(mockVillages, state.villageOptions)
        assertFalse(state.isSearchingVillage)
    }

    @Test
    fun `setVillageQuery - empty query clears options`() = runTest {
        viewModel.setVillageQuery("")
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertTrue(state.villageOptions.isEmpty())
        assertEquals("", state.villageQuery)
    }

    @Test
    fun `setVillageQuery - handle error`() = runTest {
        coEvery { shippingRepository.searchVillages(any()) } throws Exception("Search Error")
        
        viewModel.setVillageQuery("error")
        advanceTimeBy(400)
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Search Error", state.message)
        assertFalse(state.isSearchingVillage)
    }

    @Test
    fun `selectVillage - updates state`() = runTest {
        val village = VillageSearchResult("v123", "12345", "N", "D", "C", "P")
        viewModel.selectVillage(village)
        
        val state = viewModel.state.value
        assertEquals("v123", state.villageCode)
        assertEquals("12345", state.postalCode)
        assertEquals("C", state.city)
        assertTrue(state.villageOptions.isEmpty())
    }

    @Test
    fun `saveAddress - success`() = runTest {
        // Setup state with a village
        val village = VillageSearchResult("v123", "12345", "N", "D", "C", "P")
        viewModel.selectVillage(village)
        
        coEvery { profileRepository.updateOwnAddress(any()) } returns UserAddress("C", "12345", "v123")
        
        viewModel.saveAddress()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Alamat ongkir tersimpan.", state.message)
        assertFalse(state.isSavingAddress)
        coVerify { profileRepository.updateOwnAddress(match { it.city == "C" && it.postalCode == "12345" }) }
    }

    @Test
    fun `saveAddress - failure`() = runTest {
        val village = VillageSearchResult("v123", "12345", "N", "D", "C", "P")
        viewModel.selectVillage(village)
        coEvery { profileRepository.updateOwnAddress(any()) } throws Exception("Save Error")
        
        viewModel.saveAddress()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Save Error", state.message)
        assertFalse(state.isSavingAddress)
    }

    @Test
    fun `saveAddress - validation failure if no village selected`() = runTest {
        // Ensure state is truly empty/initial
        viewModel.saveAddress()
        advanceUntilIdle()
        
        val state = viewModel.state.value
        assertEquals("Pilih kelurahan dari hasil pencarian.", state.message)
        assertFalse(state.isSavingAddress)
        coVerify(exactly = 0) { profileRepository.updateOwnAddress(any()) }
    }
}
