package com.max97k.pddf.feature.viewer

data class OutlineItem(
    val title: String,
    val pageIndex: Int,
    val children: List<OutlineItem> = emptyList()
)
