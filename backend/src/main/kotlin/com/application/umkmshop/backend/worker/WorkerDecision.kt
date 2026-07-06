package com.application.umkmshop.backend.worker

enum class RetryDecision {
    Process,
    ArchiveMaxAttempts,
}

enum class DeliveryCompletionDecision {
    DeleteJob,
    RetryJob,
}

fun retryDecision(readCount: Long, maxAttempts: Int): RetryDecision =
    if (readCount >= maxAttempts) RetryDecision.ArchiveMaxAttempts else RetryDecision.Process

fun deliveryCompletionDecision(successCount: Int, transientFailureCount: Int): DeliveryCompletionDecision =
    if (transientFailureCount > 0 && successCount == 0) {
        DeliveryCompletionDecision.RetryJob
    } else {
        DeliveryCompletionDecision.DeleteJob
    }
