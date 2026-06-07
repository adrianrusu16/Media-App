package com.adrianrusu.mediaapp.appshell.domain

class DispatchAppShellIntentUseCase(private val repository: AppShellRepository) {
    operator fun invoke(intent: AppShellIntent) {
        repository.dispatch(intent)
    }
}
