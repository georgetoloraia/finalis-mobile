package com.finalis.mobile.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class HistoryViewModel : ViewModel() {
    var state by mutableStateOf<DashboardState>(DashboardState.Loading)
        private set

    fun bind(readState: DashboardState) {
        state = readState
    }

    fun clear() {
        state = DashboardState.Empty
    }
}
