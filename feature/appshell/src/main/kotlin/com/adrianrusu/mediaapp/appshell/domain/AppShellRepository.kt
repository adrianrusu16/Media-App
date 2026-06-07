package com.adrianrusu.mediaapp.appshell.domain

import kotlinx.coroutines.flow.StateFlow

interface AppShellRepository : AutoCloseable {
    val state: StateFlow<AppShellState>

    fun start()

    fun dispatch(intent: AppShellIntent)
}
