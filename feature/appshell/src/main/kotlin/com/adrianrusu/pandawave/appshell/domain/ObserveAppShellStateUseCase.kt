package com.adrianrusu.pandawave.appshell.domain

class ObserveAppShellStateUseCase(private val repository: AppShellRepository) {
    operator fun invoke() = repository.state
}
