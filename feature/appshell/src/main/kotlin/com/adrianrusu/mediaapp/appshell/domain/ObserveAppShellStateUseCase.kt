package com.adrianrusu.mediaapp.appshell.domain

class ObserveAppShellStateUseCase(private val repository: AppShellRepository) {
    operator fun invoke() = repository.state
}
