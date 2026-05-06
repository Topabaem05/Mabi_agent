package com.guribbong.phoneappagent.core.policy

import com.guribbong.phoneappagent.core.dsl.AgentAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyGateTest {
    private val gate = PolicyGate()

    @Test
    fun `Korean high-risk goals require confirmation`() {
        val goals = listOf(
            "메시지를 전송해줘",
            "친구에게 5000원 송금해줘",
            "주문 결제 진행해줘",
            "선택한 파일 삭제해줘",
            "사진을 공유해줘",
            "앱을 설치해줘",
            "위치 권한 허용해줘",
            "문서를 업로드해줘",
            "KTX 예매를 진행해줘",
            "저녁 열차를 예약해줘",
            "상품을 주문해줘",
        )

        goals.forEach { goal ->
            assertRequiresConfirmation(goal)
        }
    }

    @Test
    fun `Korean low-risk navigation is allowed without confirmation`() {
        val decision = gate.evaluate(
            goal = "설정 앱을 열고 와이파이 화면으로 이동해줘",
            actions = emptyList(),
            runtimeRiskLevel = RiskLevel.LOW,
        )

        assertTrue(decision.allowed)
        assertFalse(decision.needsConfirmation)
    }

    @Test
    fun `English high-risk goals still require confirmation`() {
        val goals = listOf(
            "send this message",
            "pay the invoice",
            "delete this photo",
            "purchase the subscription",
            "share this document",
            "install the app",
            "grant permission for location",
            "take a photo with the camera",
            "press the shutter button",
            "book the evening train",
            "reserve this ticket",
            "continue to checkout",
            "order this item",
        )

        goals.forEach { goal ->
            assertRequiresConfirmation(goal)
        }
    }

    @Test
    fun `ConfirmUser action requires confirmation`() {
        val decision = gate.evaluate(
            goal = "계산기 결과를 확인해줘",
            actions = listOf(AgentAction.ConfirmUser("User confirmation required before commit actions.")),
            runtimeRiskLevel = RiskLevel.LOW,
        )

        assertFalse(decision.allowed)
        assertTrue(decision.needsConfirmation)
    }

    @Test
    fun `opening camera without capture is low risk`() {
        val decision = gate.evaluate(
            goal = "Open the Camera app only.",
            actions = emptyList(),
            runtimeRiskLevel = RiskLevel.LOW,
        )

        assertTrue(decision.allowed)
        assertFalse(decision.needsConfirmation)
    }

    private fun assertRequiresConfirmation(goal: String) {
        val decision = gate.evaluate(
            goal = goal,
            actions = emptyList(),
            runtimeRiskLevel = RiskLevel.LOW,
        )

        assertFalse("Expected confirmation to block goal: $goal", decision.allowed)
        assertTrue("Expected confirmation for goal: $goal", decision.needsConfirmation)
    }
}
