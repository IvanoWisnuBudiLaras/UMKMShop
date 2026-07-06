package com.application.umkmshop.data.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCategoriesTest {
    @Test
    fun phase3CategoriesMatchB2BPivotOptions() {
        assertEquals(
            listOf(
                "Makanan (bahan makanan)",
                "Minuman (bahan belum diolah)",
                "Komponen Motor",
                "Komponen HP",
                "Komponen IoT",
            ),
            PHASE3_PRODUCT_CATEGORIES.map { it.value },
        )
    }

    @Test
    fun legacyCategoryUsesFallbackLabel() {
        assertFalse(isPhase3ProductCategory("Fashion"))
        assertEquals("Kategori lama", productCategoryDisplayLabel("Fashion"))
    }

    @Test
    fun blankCategoryUsesEmptyFallbackLabel() {
        assertEquals("Tanpa kategori", productCategoryDisplayLabel(""))
    }

    @Test
    fun phase3CategoryKeepsItsDisplayLabel() {
        val category = "Komponen IoT"

        assertTrue(isPhase3ProductCategory(category))
        assertEquals(category, productCategoryDisplayLabel(category))
    }
}
