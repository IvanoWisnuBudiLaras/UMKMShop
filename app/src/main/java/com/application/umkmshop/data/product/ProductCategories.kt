package com.application.umkmshop.data.product

data class ProductCategoryOption(
    val value: String,
    val label: String,
)

val PHASE3_PRODUCT_CATEGORIES = listOf(
    ProductCategoryOption(
        value = "Makanan (bahan makanan)",
        label = "Makanan (bahan makanan)",
    ),
    ProductCategoryOption(
        value = "Minuman (bahan belum diolah)",
        label = "Minuman (bahan belum diolah)",
    ),
    ProductCategoryOption(
        value = "Komponen Motor",
        label = "Komponen Motor",
    ),
    ProductCategoryOption(
        value = "Komponen HP",
        label = "Komponen HP",
    ),
    ProductCategoryOption(
        value = "Komponen IoT",
        label = "Komponen IoT",
    ),
)

private val phase3ProductCategoryValues = PHASE3_PRODUCT_CATEGORIES.map { it.value }.toSet()

fun isPhase3ProductCategory(category: String): Boolean =
    category in phase3ProductCategoryValues

fun productCategoryDisplayLabel(category: String?): String =
    when {
        category.isNullOrBlank() -> "Tanpa kategori"
        isPhase3ProductCategory(category) -> category
        else -> "Kategori lama"
    }
