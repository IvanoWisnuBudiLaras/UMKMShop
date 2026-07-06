package com.application.umkmshop.ui.order.logic

import com.application.umkmshop.data.order.OrderInput
import com.application.umkmshop.data.order.OrderRepository
import com.application.umkmshop.data.shipping.ShippingRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModelTest {
    private lateinit var viewModel: OrderViewModel
    private val repository: OrderRepository = mockk(relaxed = true)
    private val shippingRepository: ShippingRepository = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OrderViewModel(repository, shippingRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `resolveShippingCost - fallback to manual when addresses missing`() = runTest {
        coEvery { repository.getParticipantVillageCodes(any(), any()) } returns (null to null)
        
        viewModel.setSubtotal("10000")
        viewModel.setWeightGrams("1000")
        
        val input = OrderInput(
            chatRoomId = "r1",
            buyerId = "b1",
            sellerId = "s1",
            productId = "p1",
            itemNote = null,
            weightGrams = 1000,
            subtotal = 10000.0,
            shippingCost = 0.0
        )
        viewModel.createOrder(input)
        
        val state = viewModel.createState.value
        assertEquals("Alamat penjual atau pembeli belum lengkap. Isi ongkir manual untuk lanjut.", state.message)
        assertFalse(state.isSaving)
    }

    @Test
    fun `resolveShippingCost - success from repository`() = runTest {
        coEvery { repository.getParticipantVillageCodes(any(), any()) } returns ("v1" to "v2")
        coEvery { shippingRepository.estimateShipping(any(), any(), any()) } returns mockk {
            every { cost } returns 5000.0
            every { serviceName } returns "Reguler"
        }
        
        viewModel.setSubtotal("10000")
        viewModel.setWeightGrams("1000")
        val input = OrderInput(
            chatRoomId = "r1",
            buyerId = "b1",
            sellerId = "s1",
            productId = "p1",
            itemNote = null,
            weightGrams = 1000,
            subtotal = 10000.0,
            shippingCost = 0.0
        )
        
        viewModel.createOrder(input)
        
        coVerify { shippingRepository.estimateShipping("v1", "v2", 1000) }
    }
}
