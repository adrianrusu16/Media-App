package com.adrianrusu.mediaapp.core.rust.bridge.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object MediaEngineServiceContract {
    fun bindIntent(context: Context): Intent = Intent().setComponent(
        ComponentName(
            context,
            MediaEngineService::class.java
        )
    )
}
