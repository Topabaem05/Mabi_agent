package com.guribbong.phoneappagent.core.runner

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.dsl.NodeSelector
import com.guribbong.phoneappagent.core.dsl.historyKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopDetectorTest {
    @Test
    fun triggersAfterSamePackageScreenAndActionHistoryKeyRepeatsThresholdTimes() {
        val detector = LoopDetector(repeatThreshold = 3)
        val action = AgentAction.Tap(NodeSelector(text = "Wi-Fi"))

        assertFalse(detector.record("com.android.settings", "Settings > Network", action).triggered)
        assertFalse(detector.record("com.android.settings", "Settings > Network", action).triggered)
        val result = detector.record("com.android.settings", "Settings > Network", action)

        assertTrue(result.triggered)
        assertEquals(3, result.repeatCount)
        assertEquals("com.android.settings", result.packageName)
        assertEquals("Settings > Network", result.screenSummary)
        assertEquals(action.historyKey(), result.actionHistoryKey)
    }

    @Test
    fun doesNotTriggerWhenPackageScreenOrActionProgresses() {
        val detector = LoopDetector(repeatThreshold = 3)
        val tapWifi = AgentAction.Tap(NodeSelector(text = "Wi-Fi"))
        val tapBluetooth = AgentAction.Tap(NodeSelector(text = "Bluetooth"))

        assertFalse(detector.record("com.android.settings", "Settings", tapWifi).triggered)
        assertFalse(detector.record("com.android.settings", "Settings", tapWifi).triggered)
        assertFalse(detector.record("com.android.settings", "Settings > Network", tapWifi).triggered)
        assertFalse(detector.record("com.android.settings", "Settings", tapWifi).triggered)
        assertFalse(detector.record("com.android.settings", "Settings", tapWifi).triggered)
        assertFalse(detector.record("com.android.settings", "Settings", tapBluetooth).triggered)
        assertFalse(detector.record("com.example.other", "Settings", tapBluetooth).triggered)
    }
}
