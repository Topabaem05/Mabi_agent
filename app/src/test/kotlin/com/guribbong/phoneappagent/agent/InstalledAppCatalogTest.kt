package com.guribbong.phoneappagent.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class InstalledAppCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager

    @Before
    fun resetKoinBefore() {
        stopKoin()
    }

    @After
    fun resetKoinAfter() {
        stopKoin()
    }

    @Test
    fun `korailtalk goal includes korailtalk package candidate`() {
        installLauncherApp(
            packageName = "com.korail.talk",
            label = "코레일톡",
            activityName = ".ui.intro.IntroActivity",
        )
        installLauncherApp(
            packageName = "com.ktshow.cs",
            label = "마이 케이티",
            activityName = ".MainActivity",
        )

        val candidates = InstalledAppCatalog(context).candidateAppsForGoal(
            "Open the 코레일톡 app and show evening KTX train options from Daegu to Seoul.",
        )

        assertEquals("com.korail.talk", candidates.first().packageName)
    }

    @Test
    fun `install goal includes play store package candidate`() {
        installLauncherApp(
            packageName = "com.android.vending",
            label = "Google Play Store",
            activityName = ".AssetBrowserActivity",
        )

        val candidates = InstalledAppCatalog(context).candidateAppsForGoal(
            "Install Claude from Google Play Store.",
        )

        assertEquals("com.android.vending", candidates.first().packageName)
    }

    private fun installLauncherApp(
        packageName: String,
        label: String,
        activityName: String,
    ) {
        val resolveInfo =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        this.packageName = packageName
                        name = activityName
                        nonLocalizedLabel = label
                    }
            }
        val launcherIntent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        shadowOf(packageManager).addPackage(PackageInfo().apply { this.packageName = packageName })
        shadowOf(packageManager).addResolveInfoForIntent(launcherIntent, resolveInfo)
    }
}
