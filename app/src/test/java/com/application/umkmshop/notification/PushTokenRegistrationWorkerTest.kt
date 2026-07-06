package com.application.umkmshop.notification

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.application.umkmshop.data.notification.PushTokenRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenRegistrationWorkerTest {
    private val context: Context = mockk(relaxed = true)
    private val workerParams: WorkerParameters = mockk(relaxed = true)
    private val repository: PushTokenRepository = mockk(relaxed = true)
    private lateinit var worker: PushTokenRegistrationWorker

    @Before
    fun setup() {
        worker = PushTokenRegistrationWorker(context, workerParams, repository)
    }

    @Test
    fun `doWork - success`() = runTest {
        every { workerParams.inputData.getString("fcm_token") } returns "valid_token"
        coEvery { repository.registerTokenForSignedInUser("valid_token") } returns true
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork - failure on empty token`() = runTest {
        every { workerParams.inputData.getString("fcm_token") } returns ""
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork - retry on repository failure`() = runTest {
        every { workerParams.inputData.getString("fcm_token") } returns "token"
        coEvery { repository.registerTokenForSignedInUser(any()) } returns false
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork - retry on exception`() = runTest {
        every { workerParams.inputData.getString("fcm_token") } returns "token"
        coEvery { repository.registerTokenForSignedInUser(any()) } throws Exception("DB Error")
        
        val result = worker.doWork()
        
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
