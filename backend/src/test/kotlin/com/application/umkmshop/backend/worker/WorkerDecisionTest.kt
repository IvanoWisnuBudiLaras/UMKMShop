package com.application.umkmshop.backend.worker

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkerDecisionTest {
    @Test
    fun processesJobBeforeMaxAttempts() {
        assertEquals(RetryDecision.Process, retryDecision(readCount = 4, maxAttempts = 5))
    }

    @Test
    fun archivesJobAtMaxAttempts() {
        assertEquals(RetryDecision.ArchiveMaxAttempts, retryDecision(readCount = 5, maxAttempts = 5))
    }

    @Test
    fun retriesDeliveryWhenAllTokensFailTransiently() {
        assertEquals(
            DeliveryCompletionDecision.RetryJob,
            deliveryCompletionDecision(successCount = 0, transientFailureCount = 2),
        )
    }

    @Test
    fun deletesDeliveryWhenAtLeastOneTokenSucceeded() {
        assertEquals(
            DeliveryCompletionDecision.DeleteJob,
            deliveryCompletionDecision(successCount = 1, transientFailureCount = 1),
        )
    }

    @Test
    fun appendsPrepareThresholdWhenMissing() {
        assertEquals(
            "jdbc:postgresql://host/db?sslmode=require&prepareThreshold=0",
            "jdbc:postgresql://host/db?sslmode=require".withPrepareThresholdDisabled(),
        )
    }

    @Test
    fun keepsExistingPrepareThreshold() {
        assertEquals(
            "jdbc:postgresql://host/db?prepareThreshold=0",
            "jdbc:postgresql://host/db?prepareThreshold=0".withPrepareThresholdDisabled(),
        )
    }
}
