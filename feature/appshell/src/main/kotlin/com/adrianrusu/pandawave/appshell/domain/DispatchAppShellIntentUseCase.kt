package com.adrianrusu.pandawave.appshell.domain

class DispatchAppShellIntentUseCase(private val repository: AppShellRepository) {
    operator fun invoke(intent: AppShellIntent) {
        repository.dispatch(intent)
    }
}
