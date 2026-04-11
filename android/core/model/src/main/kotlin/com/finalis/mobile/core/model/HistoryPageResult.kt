package com.finalis.mobile.core.model

data class HistoryPageResult(
    val items: List<HistoryEntry>,
    val nextCursor: String? = null,
    val hasMore: Boolean,
)
