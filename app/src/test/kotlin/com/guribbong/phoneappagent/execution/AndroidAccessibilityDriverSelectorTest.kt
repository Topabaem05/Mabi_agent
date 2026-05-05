package com.guribbong.phoneappagent.execution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.guribbong.phoneappagent.agent.AgentPowerController
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidAccessibilityDriverSelectorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val driver = AndroidAccessibilityDriver(
        context = context,
        packageResolver = CanonicalPackageResolver(),
        powerController = AgentPowerController(context),
    )

    @Test
    fun legacySettingsSearchActionBarSelectorMatchesSamsungSearchDescription() {
        val matches = selectorMatches(
            selector = NodeSelector(
                resourceId = "com.android.settings:id/search_action_bar",
                className = "android.widget.LinearLayout",
                packageName = "com.android.settings",
            ),
            text = "",
            contentDescription = "설정 검색",
            resourceId = "",
            className = "android.widget.Button",
            packageName = "com.android.settings",
            editable = false,
            clickable = true,
        )

        assertTrue(matches)
    }

    private fun selectorMatches(
        selector: NodeSelector,
        text: String?,
        contentDescription: String?,
        resourceId: String?,
        className: String?,
        packageName: String?,
        editable: Boolean,
        clickable: Boolean,
    ): Boolean {
        val method = AndroidAccessibilityDriver::class.java.getDeclaredMethod(
            "selectorMatches",
            NodeSelector::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            com.guribbong.phoneappagent.core.dsl.ScreenBounds::class.java,
        )
        method.isAccessible = true
        return method.invoke(
            driver,
            selector,
            text,
            contentDescription,
            resourceId,
            className,
            packageName,
            editable,
            clickable,
            null,
        ) as Boolean
    }
}
