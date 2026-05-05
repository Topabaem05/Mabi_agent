package com.guribbong.phoneappagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.guribbong.phoneappagent.ui.AppRoot
import com.guribbong.phoneappagent.ui.theme.PhoneAppAgentTheme

class MainActivity : ComponentActivity() {
    private var seedPrompt by mutableStateOf<String?>(null)
    private var seedAutoQueue by mutableStateOf(false)
    private var seedNonce by mutableIntStateOf(0)
    private val plannerSeedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                seedPrompt = intent?.getStringExtra(EXTRA_SEED_PROMPT)
                seedAutoQueue = intent?.getBooleanExtra(EXTRA_AUTO_QUEUE, false) == true
                seedNonce += 1
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        seedPrompt = intent.getStringExtra(EXTRA_SEED_PROMPT)
        seedAutoQueue = intent.getBooleanExtra(EXTRA_AUTO_QUEUE, false)
        seedNonce += 1
        registerPlannerSeedReceiver()
        setContent {
            PhoneAppAgentTheme {
                AppRoot(
                    initialPrompt = seedPrompt,
                    initialPromptNonce = seedNonce,
                    autoQueueInitialPrompt = seedAutoQueue,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        seedPrompt = intent.getStringExtra(EXTRA_SEED_PROMPT)
        seedAutoQueue = intent.getBooleanExtra(EXTRA_AUTO_QUEUE, false)
        seedNonce += 1
    }

    override fun onDestroy() {
        unregisterReceiver(plannerSeedReceiver)
        super.onDestroy()
    }

    private fun registerPlannerSeedReceiver() {
        val filter = IntentFilter(ACTION_SET_PROMPT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(plannerSeedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(plannerSeedReceiver, filter)
        }
    }

    private fun configureSystemBars() {
        val figmaBackground = Color.rgb(242, 239, 233)
        window.statusBarColor = figmaBackground
        window.navigationBarColor = figmaBackground
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    companion object {
        const val EXTRA_SEED_PROMPT = "seed_prompt"
        const val EXTRA_AUTO_QUEUE = "auto_queue"
        const val ACTION_SET_PROMPT = "com.guribbong.phoneappagent.action.SET_PROMPT"
    }
}
