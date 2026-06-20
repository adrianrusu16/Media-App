package com.adrianrusu.mediaapp.core.ui.focus

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch

fun Modifier.bambooBringIntoViewOnFocus(): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch { bringIntoViewRequester.bringIntoView() }
            }
        }
}
