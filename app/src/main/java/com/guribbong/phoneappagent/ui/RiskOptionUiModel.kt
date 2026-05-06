package com.guribbong.phoneappagent.ui

import com.guribbong.phoneappagent.core.dsl.AgentAction
import com.guribbong.phoneappagent.core.runner.ExecutionStep

data class RiskOptionUiModel(
    val progressEyebrow: String,
    val title: String,
    val description: String,
    val progressSteps: List<String>,
    val activeProgressIndex: Int,
    val statusRows: List<RiskProgressRow>,
    val sheetTitle: String,
    val sheetSubtitle: String,
    val options: List<RiskOptionItem>,
    val refinementTitle: String,
    val refinementPlaceholder: String,
)

data class RiskProgressRow(
    val title: String,
    val detail: String,
    val state: RiskProgressState,
)

enum class RiskProgressState {
    DONE,
    ACTIVE,
    WAITING,
}

data class RiskOptionItem(
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val action: RiskOptionAction,
    val followUpPrompt: String? = null,
)

enum class RiskOptionAction {
    FOLLOW_UP,
    PROCEED,
    REFINE,
    STOP,
}

object RiskOptionUiBuilder {
    fun build(
        goal: String,
        planSummary: String,
        planSteps: List<ExecutionStep>,
        currentStepIndex: Int,
        reason: String,
    ): RiskOptionUiModel {
        val lowerGoal = goal.lowercase()
        return when {
            isKorailBooking(lowerGoal) -> buildKorailBooking(goal)
            isInstallOrPermission(lowerGoal) -> buildInstallOrPermission(goal, reason)
            isPaymentOrOrder(lowerGoal) -> buildPaymentOrOrder(goal)
            isSend(lowerGoal) -> buildSend(goal)
            else -> buildGeneric(goal, planSummary, planSteps, currentStepIndex, reason)
        }
    }

    private fun buildKorailBooking(goal: String): RiskOptionUiModel =
        RiskOptionUiModel(
            progressEyebrow = "현재 진행 과정",
            title = "KTX 예매 전 선택이 필요해요",
            description = "열차 조회는 완료했지만 예매로 넘어가기 전에는 사용자의 선택이 필요합니다.",
            progressSteps = listOf("요청 확인", "열차 옵션 탐색", "예매 여부 선택"),
            activeProgressIndex = 2,
            statusRows = listOf(
                RiskProgressRow(
                    title = "동대구-서울 KTX 조건 확인",
                    detail = "요청한 출발지, 도착지, 열차 조건을 확인했어요.",
                    state = RiskProgressState.DONE,
                ),
                RiskProgressRow(
                    title = "저녁 시간대 후보 정리 중",
                    detail = "조회 결과에서 KTX 후보를 비교할 준비가 됐어요.",
                    state = RiskProgressState.ACTIVE,
                ),
                RiskProgressRow(
                    title = "예매 단계 진입 대기",
                    detail = "좌석 선택이나 결제 화면으로 이동하기 전에 멈춰 있어요.",
                    state = RiskProgressState.WAITING,
                ),
            ),
            sheetTitle = "다음 작업 선택",
            sheetSubtitle = "예매로 넘어갈지, 조회 결과만 정리할지 선택하세요.",
            options = listOf(
                RiskOptionItem(
                    title = "KTX 후보만 정리",
                    subtitle = "예매하지 않고 조회 결과를 요약해요.",
                    badge = "추천",
                    action = RiskOptionAction.FOLLOW_UP,
                    followUpPrompt = "$goal 예매하지 말고 KTX 후보만 정리해서 보여줘.",
                ),
                RiskOptionItem(
                    title = "예매 단계로 이동",
                    subtitle = "선택 후 앱의 예매 버튼까지 진행해요.",
                    action = RiskOptionAction.PROCEED,
                ),
                RiskOptionItem(
                    title = "다른 조건 입력",
                    subtitle = "시간대나 열차 조건을 바꿔 다시 찾아요.",
                    action = RiskOptionAction.REFINE,
                ),
            ),
            refinementTitle = "다른 조건",
            refinementPlaceholder = "예: 더 이른 시간, KTX만, 매진 제외",
        )

    private fun buildPaymentOrOrder(goal: String): RiskOptionUiModel =
        RiskOptionUiModel(
            progressEyebrow = "확인 필요",
            title = "결제 또는 주문 전 확인이 필요해요",
            description = "금액, 상품, 수신처처럼 되돌리기 어려운 정보를 다시 확인한 뒤 진행해야 합니다.",
            progressSteps = listOf("요청 확인", "정보 검토", "진행 여부 선택"),
            activeProgressIndex = 2,
            statusRows = listOf(
                RiskProgressRow("요청 내용 파악 완료", "주문 또는 결제 단계로 이어질 수 있는 작업이에요.", RiskProgressState.DONE),
                RiskProgressRow("중요 정보 확인 중", "화면의 금액과 조건을 사용자가 확인해야 해요.", RiskProgressState.ACTIVE),
                RiskProgressRow("실행 대기", "확인 전에는 결제, 주문, 예약을 누르지 않아요.", RiskProgressState.WAITING),
            ),
            sheetTitle = "진행 방식 선택",
            sheetSubtitle = "실행 전에 확인할 작업을 선택하세요.",
            options = listOf(
                RiskOptionItem("진행 전 요약 보기", "결제나 주문 없이 화면 정보를 요약해요.", "추천", RiskOptionAction.FOLLOW_UP, "$goal 진행하지 말고 결제 또는 주문 전 확인 정보만 요약해줘."),
                RiskOptionItem("계속 진행", "사용자 확인 후 다음 위험 단계로 넘어가요.", null, RiskOptionAction.PROCEED),
                RiskOptionItem("중단", "현재 작업을 멈춰요.", null, RiskOptionAction.STOP),
            ),
            refinementTitle = "확인 조건",
            refinementPlaceholder = "예: 금액 먼저 확인, 할인 적용 여부 확인",
        )

    private fun buildSend(goal: String): RiskOptionUiModel =
        RiskOptionUiModel(
            progressEyebrow = "전송 전 확인",
            title = "전송하기 전에 멈췄어요",
            description = "메시지나 공유 내용은 사용자 확인 없이는 보내지 않습니다.",
            progressSteps = listOf("내용 작성", "전송 전 확인", "사용자 선택"),
            activeProgressIndex = 2,
            statusRows = listOf(
                RiskProgressRow("내용 준비 완료", "전송할 수 있는 단계까지 준비했어요.", RiskProgressState.DONE),
                RiskProgressRow("수신처와 내용 확인 중", "보내기 전에 마지막 확인이 필요해요.", RiskProgressState.ACTIVE),
                RiskProgressRow("전송 대기", "확인 전에는 보내기 버튼을 누르지 않아요.", RiskProgressState.WAITING),
            ),
            sheetTitle = "다음 작업 선택",
            sheetSubtitle = "전송을 진행하거나 내용을 다시 조정할 수 있어요.",
            options = listOf(
                RiskOptionItem("내용만 검토", "전송하지 않고 작성 내용을 확인해요.", "추천", RiskOptionAction.FOLLOW_UP, "$goal 전송하지 말고 작성 내용과 수신처만 검토해줘."),
                RiskOptionItem("전송 진행", "사용자 확인 후 보내기 버튼으로 진행해요.", null, RiskOptionAction.PROCEED),
                RiskOptionItem("다른 내용 입력", "내용이나 수신처를 바꿔요.", null, RiskOptionAction.REFINE),
            ),
            refinementTitle = "수정 내용",
            refinementPlaceholder = "예: 문구를 더 짧게, 수신처 변경",
        )

    private fun buildInstallOrPermission(
        goal: String,
        reason: String,
    ): RiskOptionUiModel {
        if (isFailureReason(reason)) {
            return RiskOptionUiModel(
                progressEyebrow = "진행 상황 보고",
                title = "설치 전 단계에서 멈췄어요",
                description = "앱 설치 버튼까지 도달하지 못해 설치를 진행하지 않았습니다.",
                progressSteps = listOf("요청 확인", "Play Store 탐색", "설치 전 확인"),
                activeProgressIndex = 1,
                statusRows = listOf(
                    RiskProgressRow("요청 확인 완료", "설치 작업이 기기 상태를 바꾸는 위험 작업임을 확인했어요.", RiskProgressState.DONE),
                    RiskProgressRow("Play Store 탐색 중 문제 발생", reason.ifBlank { "검색 또는 화면 요소 확인에 실패했어요." }.take(96), RiskProgressState.ACTIVE),
                    RiskProgressRow("설치 버튼 미도달", "사용자 확인 팝업 전 단계라 설치를 누르지 않았어요.", RiskProgressState.WAITING),
                ),
                sheetTitle = "설치하지 못한 이유",
                sheetSubtitle = "설치 버튼에 도달하지 못해 멈췄습니다. 다시 시도하거나 조건을 바꿀 수 있어요.",
                options = listOf(
                    RiskOptionItem("다시 시도", "Play Store 검색부터 다시 진행해요.", "추천", RiskOptionAction.FOLLOW_UP, goal),
                    RiskOptionItem("다른 조건 입력", "앱 이름이나 설치 조건을 바꿔요.", null, RiskOptionAction.REFINE),
                    RiskOptionItem("중단", "현재 작업을 닫아요.", null, RiskOptionAction.STOP),
                ),
                refinementTitle = "다른 조건",
                refinementPlaceholder = "예: Claude by Anthropic으로 검색",
            )
        }
        return RiskOptionUiModel(
            progressEyebrow = "권한 변경 전 확인",
            title = "설치 또는 권한 변경 전 확인이 필요해요",
            description = "기기 권한이나 앱 설치 상태가 바뀌기 전에 사용자가 직접 선택해야 합니다.",
            progressSteps = listOf("요청 확인", "대상 확인", "허용 여부 선택"),
            activeProgressIndex = 2,
            statusRows = listOf(
                RiskProgressRow("대상 화면 확인 완료", "설치 또는 권한 화면까지 접근했어요.", RiskProgressState.DONE),
                RiskProgressRow("영향 범위 확인 중", "기기에 적용될 변경 사항을 확인해야 해요.", RiskProgressState.ACTIVE),
                RiskProgressRow("변경 대기", "확인 전에는 허용이나 설치를 누르지 않아요.", RiskProgressState.WAITING),
            ),
            sheetTitle = "처리 방식 선택",
            sheetSubtitle = "변경을 진행할지, 정보만 확인할지 선택하세요.",
            options = listOf(
                RiskOptionItem("정보만 확인", "설치나 권한 변경 없이 현재 화면만 설명해요.", "추천", RiskOptionAction.FOLLOW_UP, "$goal 변경하지 말고 현재 설치 또는 권한 정보만 확인해줘."),
                RiskOptionItem("변경 진행", "사용자 확인 후 다음 단계로 진행해요.", null, RiskOptionAction.PROCEED),
                RiskOptionItem("중단", "현재 작업을 멈춰요.", null, RiskOptionAction.STOP),
            ),
            refinementTitle = "추가 조건",
            refinementPlaceholder = "예: 권한 설명만 보기, 설치하지 않기",
        )
    }

    private fun buildGeneric(
        goal: String,
        planSummary: String,
        planSteps: List<ExecutionStep>,
        currentStepIndex: Int,
        reason: String,
    ): RiskOptionUiModel {
        val currentAction = planSteps.getOrNull((currentStepIndex + 1).coerceAtLeast(0))?.action
        val currentDetail = currentAction?.let(::describeAction) ?: reason.ifBlank { planSummary }
        return RiskOptionUiModel(
            progressEyebrow = "사용자 확인",
            title = "위험 작업 전 확인이 필요해요",
            description = reason.ifBlank { "이 다음 단계는 사용자의 명시적인 선택 후에만 진행됩니다." },
            progressSteps = listOf("요청 확인", "작업 준비", "진행 여부 선택"),
            activeProgressIndex = 2,
            statusRows = listOf(
                RiskProgressRow("요청 분석 완료", "사용자 요청과 현재 화면을 확인했어요.", RiskProgressState.DONE),
                RiskProgressRow("다음 단계 확인 중", currentDetail.take(80), RiskProgressState.ACTIVE),
                RiskProgressRow("실행 대기", "확인 전에는 위험 작업을 실행하지 않아요.", RiskProgressState.WAITING),
            ),
            sheetTitle = "다음 작업 선택",
            sheetSubtitle = "진행하거나 조건을 바꿔 다시 요청할 수 있어요.",
            options = listOf(
                RiskOptionItem("현재 상태 요약", "위험 작업 없이 지금까지의 결과만 정리해요.", "추천", RiskOptionAction.FOLLOW_UP, "$goal 위험 작업은 진행하지 말고 현재 상태만 요약해줘."),
                RiskOptionItem("계속 진행", "사용자 확인 후 다음 단계로 진행해요.", null, RiskOptionAction.PROCEED),
                RiskOptionItem("조건 수정", "새 조건으로 다시 요청해요.", null, RiskOptionAction.REFINE),
            ),
            refinementTitle = "수정 조건",
            refinementPlaceholder = "원하는 조건을 입력하세요",
        )
    }

    private fun isKorailBooking(lowerGoal: String): Boolean =
        listOf("코레일", "korail", "ktx").any { it in lowerGoal } &&
            listOf("book", "booking", "reserve", "reservation", "예매", "예약").any { it in lowerGoal }

    private fun isPaymentOrOrder(lowerGoal: String): Boolean =
        listOf("pay", "payment", "purchase", "buy", "order", "checkout", "결제", "구매", "주문").any { it in lowerGoal }

    private fun isSend(lowerGoal: String): Boolean =
        listOf("send", "share", "post", "전송", "공유", "게시").any { it in lowerGoal }

    private fun isInstallOrPermission(lowerGoal: String): Boolean =
        listOf("install", "permission", "grant", "설치", "권한", "허용").any { it in lowerGoal }

    private fun isFailureReason(reason: String): Boolean {
        val lowerReason = reason.lowercase()
        return listOf("failed", "timed out", "not found", "failure", "실패", "찾을 수").any { it in lowerReason }
    }

    private fun describeAction(action: AgentAction): String =
        when (action) {
            is AgentAction.LaunchApp -> "앱 실행: ${action.packageName}"
            is AgentAction.OpenUri -> "앱 화면 열기: ${action.packageName ?: action.uri}"
            is AgentAction.WaitForApp -> "앱 전환 대기: ${action.packageName}"
            is AgentAction.WaitForNode -> "화면 요소 대기: ${action.selector.label()}"
            is AgentAction.Tap -> "누를 대상: ${action.label}"
            is AgentAction.InputText -> "입력 대상: ${action.selector.label()}"
            is AgentAction.SubmitInput -> "입력 제출: ${action.selector.label()}"
            is AgentAction.ClearText -> "입력값 지우기: ${action.selector.label()}"
            is AgentAction.Scroll -> "화면 스크롤"
            is AgentAction.PressGlobal -> "시스템 동작: ${action.action.name}"
            is AgentAction.AssertVisible -> "화면 확인: ${action.selector.label()}"
            is AgentAction.WaitForCondition -> "조건 대기: ${action.condition}"
            is AgentAction.ConfirmUser -> action.reason
            AgentAction.Stop -> "작업 중지"
        }
}
