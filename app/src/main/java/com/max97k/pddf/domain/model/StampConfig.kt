package com.max97k.pddf.domain.model

data class StampConfig(
    val format: String = "Page %d of %d",
    val position: String = "bottom-center",
    val fontSize: Float = 12f,
    val margin: Float = 20f
)
