package com.guribbong.phoneappagent.ui

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.runner.ExecutionStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskOptionUiBuilderTest {
    @Test
    fun `korail booking confirmation uses train-specific option copy`() {
        val model = RiskOptionUiBuilder.build(
            goal = "Run 코레일톡 and find KTX trains from Daegu to Seoul, then prepare to book one evening option.",
            planSummary = "KorailTalk train results are visible.",
            planSteps = listOf(ExecutionStep(AgentAction.ConfirmUser("booking"), "pause")),
            currentStepIndex = -1,
            reason = "booking",
        )

        assertEquals("KTX 예매 전 선택이 필요해요", model.title)
        assertEquals(listOf("요청 확인", "열차 옵션 탐색", "예매 여부 선택"), model.progressSteps)
        assertTrue(model.options.any { it.title == "KTX 후보만 정리" && it.action == RiskOptionAction.FOLLOW_UP })
        assertTrue(model.options.any { it.title == "예매 단계로 이동" && it.action == RiskOptionAction.PROCEED })
        assertTrue(model.options.any { it.title == "다른 조건 입력" && it.action == RiskOptionAction.REFINE })
    }

    @Test
    fun `payment and order confirmation uses payment-specific copy`() {
        val model = RiskOptionUiBuilder.build(
            goal = "Continue to checkout and order this item.",
            planSummary = "Checkout is ready.",
            planSteps = emptyList(),
            currentStepIndex = -1,
            reason = "High-risk goal must stop at confirm_user before commit actions.",
        )

        assertEquals("결제 또는 주문 전 확인이 필요해요", model.title)
        assertTrue(model.options.any { it.title == "진행 전 요약 보기" })
        assertTrue(model.options.any { it.title == "계속 진행" })
        assertTrue(model.options.any { it.title == "중단" })
    }

    @Test
    fun `generic confirmation keeps shared template with different content`() {
        val korail = RiskOptionUiBuilder.build(
            goal = "KTX 예매를 준비해줘",
            planSummary = "",
            planSteps = emptyList(),
            currentStepIndex = -1,
            reason = "",
        )
        val generic = RiskOptionUiBuilder.build(
            goal = "Run a risky app step.",
            planSummary = "Next step needs confirmation.",
            planSteps = emptyList(),
            currentStepIndex = -1,
            reason = "User confirmation required.",
        )

        assertEquals("위험 작업 전 확인이 필요해요", generic.title)
        assertEquals(korail.progressSteps.size, generic.progressSteps.size)
        assertEquals(korail.statusRows.size, generic.statusRows.size)
        assertNotEquals(korail.title, generic.title)
        assertNotEquals(korail.options.first().title, generic.options.first().title)
    }

    @Test
    fun `install failure reports why installation did not proceed`() {
        val model = RiskOptionUiBuilder.build(
            goal = "Install Claude from Google Play Store. Stop before payment or login.",
            planSummary = "Search failed before install.",
            planSteps = emptyList(),
            currentStepIndex = 0,
            reason = "Editable field not found.",
        )

        assertEquals("설치 전 단계에서 멈췄어요", model.title)
        assertEquals("설치하지 못한 이유", model.sheetTitle)
        assertTrue(model.statusRows.any { "Editable field not found" in it.detail })
        assertTrue(model.statusRows.any { it.title == "설치 버튼 미도달" })
        assertTrue(model.options.any { it.title == "다시 시도" && it.action == RiskOptionAction.FOLLOW_UP })
    }
}
