package com.application.umkmshop.ui.components

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Extension function to handle debouncing search queries reactively.
 * Ensures API is not hit too frequently and ignores queries that are too short (except empty).
 */
fun Flow<String>.debounceSearch(
    timeoutMillis: Long = 500L
): Flow<String> = this
    .debounce(timeoutMillis)
    .distinctUntilChanged()
    .filter { it.trim().length >= 2 || it.isBlank() }
