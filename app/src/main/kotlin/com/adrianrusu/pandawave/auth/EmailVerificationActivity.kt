package com.adrianrusu.pandawave.auth

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.adrianrusu.pandawave.BuildConfig
import com.adrianrusu.pandawave.MainActivity
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Exported, credential-sanitizing entry point for one-shot email verification links. */
@AndroidEntryPoint
class EmailVerificationActivity : ComponentActivity() {
    @Inject
    lateinit var authGateway: EngineAuthGateway

    private var coordinator: EmailVerificationCoordinator? = null
    private var createdAtElapsedRealtime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        createdAtElapsedRealtime = SystemClock.elapsedRealtime()
        val link = takeAndSanitizeLink(intent)
        super.onCreate(savedInstanceState)
        PandaLog.i(PandaLog.Tag.AUTH) { "verify-email.activity_create restored=${savedInstanceState != null}" }

        if (savedInstanceState != null) {
            openMainApp()
            return
        }

        val parser = EmailVerificationLinkParser(
            EmailVerificationLinkConfig(
                appLinkHost = BuildConfig.VERIFICATION_APP_LINK_HOST,
                actionPath = BuildConfig.VERIFICATION_ACTION_PATH,
                tokenParameter = BuildConfig.VERIFICATION_TOKEN_PARAMETER,
                debugScheme = BuildConfig.VERIFICATION_DEBUG_SCHEME.ifEmpty { null }
            )
        )
        coordinator = EmailVerificationCoordinator(
            authGateway = authGateway,
            parseLink = parser::parse,
            deviceLabel = DEVICE_LABEL,
            scope = lifecycleScope,
            clock = { SystemClock.elapsedRealtime() },
            log = { message -> PandaLog.i(PandaLog.Tag.AUTH) { message } },
            onComplete = { result ->
                PandaLog.i(PandaLog.Tag.AUTH) {
                    "verify-email.activity_complete status=${result.status} " +
                        "sinceCreateMs=${SystemClock.elapsedRealtime() - createdAtElapsedRealtime}"
                }
                runOnUiThread { openMainApp(result) }
            }
        ).also { it.consume(link) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val link = takeAndSanitizeLink(intent)
        coordinator?.consume(link)
    }

    override fun onDestroy() {
        coordinator?.close()
        coordinator = null
        super.onDestroy()
    }

    private fun takeAndSanitizeLink(source: Intent?): String? {
        val link = source?.dataString
        source?.data = null
        setIntent(Intent())
        return link
    }

    private fun openMainApp(result: EngineAuthOperationResult? = null) {
        if (isFinishing || isDestroyed) return
        PandaLog.i(PandaLog.Tag.AUTH) {
            "verify-email.activity_exit status=${result?.status ?: "restored"} " +
                "sinceCreateMs=${SystemClock.elapsedRealtime() - createdAtElapsedRealtime}"
        }
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        finish()
    }

    private companion object {
        const val DEVICE_LABEL = "PandaWave Android"
    }
}
