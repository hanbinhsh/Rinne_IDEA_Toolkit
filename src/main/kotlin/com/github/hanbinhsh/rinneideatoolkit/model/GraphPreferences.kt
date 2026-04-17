package com.github.hanbinhsh.rinneideatoolkit.model

import com.intellij.ui.JBColor
import java.awt.Color

enum class ToolbarToggleId(val id: String) {
    SHOW_UNREACHED_METHODS("showUnreachedMethods"),
    SHOW_UNREACHED_CALLS("showUnreachedCalls"),
    SHOW_ACCESSOR_METHODS("showAccessorMethods"),
    SHOW_VISIBILITY_COLORS("showVisibilityColors"),
    SHOW_PRIVATE_METHODS("showPrivateMethods"),
    ENABLE_CLICK_HIGHLIGHT("enableClickHighlight"),
    SHOW_DETAILED_CALL_EDGES("showDetailedCallEdges"),
    SHOW_MAPPER_TABLES("showMapperTables"),
    ROUTE_SAME_COLUMN_EDGES_OUTSIDE("routeSameColumnEdgesOutside"),
    DRAW_EDGES_ON_TOP("drawEdgesOnTop"),
    DRAW_ARROWHEADS_ON_TOP("drawArrowheadsOnTop"),
    WHITE_BACKGROUND_FOR_EXPORT("whiteBackgroundForExport"),
    ;

    companion object {
        fun fromId(id: String): ToolbarToggleId? = values().firstOrNull { it.id == id }

        fun defaultVisible(): Set<ToolbarToggleId> = linkedSetOf(
            SHOW_UNREACHED_METHODS,
            SHOW_ACCESSOR_METHODS,
            SHOW_VISIBILITY_COLORS,
            SHOW_PRIVATE_METHODS,
            ENABLE_CLICK_HIGHLIGHT,
            SHOW_DETAILED_CALL_EDGES,
            SHOW_MAPPER_TABLES,
        )
    }
}

enum class SequenceToolbarToggleId(val id: String) {
    SHOW_ACCESSOR_METHODS("showAccessorMethods"),
    SHOW_PRIVATE_METHODS("showPrivateMethods"),
    SHOW_MAPPER_TABLES("showMapperTables"),
    WHITE_BACKGROUND_FOR_EXPORT("whiteBackgroundForExport"),
    SHOW_RETURN_MESSAGES("showReturnMessages"),
    SHOW_ACTIVATION_BARS("showActivationBars"),
    SHOW_CREATE_MESSAGES("showCreateMessages"),
    ;

    companion object {
        fun fromId(id: String): SequenceToolbarToggleId? = values().firstOrNull { it.id == id }

        fun defaultVisible(): Set<SequenceToolbarToggleId> = linkedSetOf(
            SHOW_ACCESSOR_METHODS,
            SHOW_PRIVATE_METHODS,
            SHOW_MAPPER_TABLES,
            SHOW_RETURN_MESSAGES,
            SHOW_ACTIVATION_BARS,
            SHOW_CREATE_MESSAGES,
            WHITE_BACKGROUND_FOR_EXPORT,
        )
    }
}

enum class ClipboardExportFormat(val id: String) {
    IMAGE("image"),
    SVG("svg"),
    MERMAID("mermaid"),
    ;

    companion object {
        fun fromId(id: String?): ClipboardExportFormat =
            values().firstOrNull { it.id == id } ?: IMAGE
    }
}

data class GraphColorSettings(
    val rootFillHex: String = "#E0EBFF",
    val rootFillDarkHex: String = "#42608F",
    val rootBorderHex: String = "#7792D6",
    val rootBorderDarkHex: String = "#96B5EA",
    val reachableFillHex: String = "#E2F4E9",
    val reachableFillDarkHex: String = "#4A715F",
    val reachableBorderHex: String = "#79A287",
    val reachableBorderDarkHex: String = "#97C7A4",
    val supplementalFillHex: String = "#F4F0E6",
    val supplementalFillDarkHex: String = "#6C6658",
    val supplementalBorderHex: String = "#B59B66",
    val supplementalBorderDarkHex: String = "#D2B980",
    val privateFillHex: String = "#FFE7EA",
    val privateFillDarkHex: String = "#8A5662",
    val privateBorderHex: String = "#D47686",
    val privateBorderDarkHex: String = "#E2A1AE",
    val targetFocusHex: String = "#E6B850",
    val targetFocusDarkHex: String = "#E4B14A",
    val callerFocusHex: String = "#578BEA",
    val callerFocusDarkHex: String = "#7BA6F1",
    val calleeFocusHex: String = "#4AB584",
    val calleeFocusDarkHex: String = "#67C59A",
    val mixedFocusHex: String = "#A276D6",
    val mixedFocusDarkHex: String = "#B38DE0",
    val selectionHighlightHex: String = "#FF4D6D",
    val selectionHighlightDarkHex: String = "#FF6E8A",
    val tableFillHex: String = "#FFF2CC",
    val tableFillDarkHex: String = "#7A6840",
    val tableBorderHex: String = "#C9A227",
    val tableBorderDarkHex: String = "#E4C45A",
    val columnFillHex: String = "#FDEBD7",
    val columnFillDarkHex: String = "#7B5A3F",
    val columnBorderHex: String = "#D28B45",
    val columnBorderDarkHex: String = "#E8A76B",
    val columnActionFillHex: String = "#F7E1FF",
    val columnActionFillDarkHex: String = "#654779",
    val columnActionBorderHex: String = "#A665D6",
    val columnActionBorderDarkHex: String = "#C792F0",
) {
    fun rootFillLightColor(): Color = parseColor(rootFillHex, Color(224, 235, 255))

    fun rootFillDarkColor(): Color = parseColor(rootFillDarkHex, Color(66, 96, 143))

    fun rootFillColor(): Color = themeColor(rootFillLightColor(), rootFillDarkColor())

    fun rootBorderLightColor(): Color = parseColor(rootBorderHex, Color(119, 146, 214))

    fun rootBorderDarkColor(): Color = parseColor(rootBorderDarkHex, Color(150, 181, 234))

    fun rootBorderColor(): Color = themeColor(rootBorderLightColor(), rootBorderDarkColor())

    fun reachableFillLightColor(): Color = parseColor(reachableFillHex, Color(226, 244, 233))

    fun reachableFillDarkColor(): Color = parseColor(reachableFillDarkHex, Color(74, 113, 95))

    fun reachableFillColor(): Color = themeColor(reachableFillLightColor(), reachableFillDarkColor())

    fun reachableBorderLightColor(): Color = parseColor(reachableBorderHex, Color(121, 162, 135))

    fun reachableBorderDarkColor(): Color = parseColor(reachableBorderDarkHex, Color(151, 199, 164))

    fun reachableBorderColor(): Color = themeColor(reachableBorderLightColor(), reachableBorderDarkColor())

    fun supplementalFillLightColor(): Color = parseColor(supplementalFillHex, Color(244, 240, 230))

    fun supplementalFillDarkColor(): Color = parseColor(supplementalFillDarkHex, Color(108, 102, 88))

    fun supplementalFillColor(): Color = themeColor(supplementalFillLightColor(), supplementalFillDarkColor())

    fun supplementalBorderLightColor(): Color = parseColor(supplementalBorderHex, Color(181, 155, 102))

    fun supplementalBorderDarkColor(): Color = parseColor(supplementalBorderDarkHex, Color(210, 185, 128))

    fun supplementalBorderColor(): Color = themeColor(supplementalBorderLightColor(), supplementalBorderDarkColor())

    fun privateFillLightColor(): Color = parseColor(privateFillHex, Color(255, 231, 234))

    fun privateFillDarkColor(): Color = parseColor(privateFillDarkHex, Color(138, 86, 98))

    fun privateFillColor(): Color = themeColor(privateFillLightColor(), privateFillDarkColor())

    fun privateBorderLightColor(): Color = parseColor(privateBorderHex, Color(212, 118, 134))

    fun privateBorderDarkColor(): Color = parseColor(privateBorderDarkHex, Color(226, 161, 174))

    fun privateBorderColor(): Color = themeColor(privateBorderLightColor(), privateBorderDarkColor())

    fun targetFocusLightColor(): Color = parseColor(targetFocusHex, Color(230, 184, 80))

    fun targetFocusDarkColor(): Color = parseColor(targetFocusDarkHex, Color(228, 177, 74))

    fun targetFocusColor(): Color = themeColor(targetFocusLightColor(), targetFocusDarkColor())

    fun callerFocusLightColor(): Color = parseColor(callerFocusHex, Color(87, 139, 234))

    fun callerFocusDarkColor(): Color = parseColor(callerFocusDarkHex, Color(123, 166, 241))

    fun callerFocusColor(): Color = themeColor(callerFocusLightColor(), callerFocusDarkColor())

    fun calleeFocusLightColor(): Color = parseColor(calleeFocusHex, Color(74, 181, 132))

    fun calleeFocusDarkColor(): Color = parseColor(calleeFocusDarkHex, Color(103, 197, 154))

    fun calleeFocusColor(): Color = themeColor(calleeFocusLightColor(), calleeFocusDarkColor())

    fun mixedFocusLightColor(): Color = parseColor(mixedFocusHex, Color(162, 118, 214))

    fun mixedFocusDarkColor(): Color = parseColor(mixedFocusDarkHex, Color(179, 141, 224))

    fun mixedFocusColor(): Color = themeColor(mixedFocusLightColor(), mixedFocusDarkColor())

    fun selectionHighlightLightColor(): Color = parseColor(selectionHighlightHex, Color(255, 77, 109))

    fun selectionHighlightDarkColor(): Color = parseColor(selectionHighlightDarkHex, Color(255, 110, 138))

    fun selectionHighlightColor(): Color = themeColor(selectionHighlightLightColor(), selectionHighlightDarkColor())

    fun tableFillLightColor(): Color = parseColor(tableFillHex, Color(255, 242, 204))

    fun tableFillDarkColor(): Color = parseColor(tableFillDarkHex, Color(122, 104, 64))

    fun tableFillColor(): Color = themeColor(tableFillLightColor(), tableFillDarkColor())

    fun tableBorderLightColor(): Color = parseColor(tableBorderHex, Color(201, 162, 39))

    fun tableBorderDarkColor(): Color = parseColor(tableBorderDarkHex, Color(228, 196, 90))

    fun tableBorderColor(): Color = themeColor(tableBorderLightColor(), tableBorderDarkColor())

    fun columnFillLightColor(): Color = parseColor(columnFillHex, Color(253, 235, 215))

    fun columnFillDarkColor(): Color = parseColor(columnFillDarkHex, Color(123, 90, 63))

    fun columnFillColor(): Color = themeColor(columnFillLightColor(), columnFillDarkColor())

    fun columnBorderLightColor(): Color = parseColor(columnBorderHex, Color(210, 139, 69))

    fun columnBorderDarkColor(): Color = parseColor(columnBorderDarkHex, Color(232, 167, 107))

    fun columnBorderColor(): Color = themeColor(columnBorderLightColor(), columnBorderDarkColor())

    fun columnActionFillLightColor(): Color = parseColor(columnActionFillHex, Color(247, 225, 255))

    fun columnActionFillDarkColor(): Color = parseColor(columnActionFillDarkHex, Color(101, 71, 121))

    fun columnActionFillColor(): Color = themeColor(columnActionFillLightColor(), columnActionFillDarkColor())

    fun columnActionBorderLightColor(): Color = parseColor(columnActionBorderHex, Color(166, 101, 214))

    fun columnActionBorderDarkColor(): Color = parseColor(columnActionBorderDarkHex, Color(199, 146, 240))

    fun columnActionBorderColor(): Color = themeColor(columnActionBorderLightColor(), columnActionBorderDarkColor())

    private fun parseColor(hex: String, fallback: Color): Color =
        runCatching { Color.decode(hex) }.getOrDefault(fallback)

    private fun themeColor(light: Color, dark: Color): Color = if (JBColor.isBright()) light else dark
}

data class SequenceColorSettings(
    val scenarioFillHex: String = "#F1F4F8",
    val scenarioFillDarkHex: String = "#474D58",
    val scenarioBorderHex: String = "#CDD5E1",
    val scenarioBorderDarkHex: String = "#7B8698",
    val participantFillHex: String = "#E3EBF6",
    val participantFillDarkHex: String = "#54667E",
    val participantBorderHex: String = "#788EB1",
    val participantBorderDarkHex: String = "#A1B2CC",
    val participantTextHex: String = "#23272A",
    val participantTextDarkHex: String = "#EEF3F8",
    val databaseParticipantFillHex: String = "#FFF2CC",
    val databaseParticipantFillDarkHex: String = "#6B571F",
    val databaseParticipantBorderHex: String = "#C9A227",
    val databaseParticipantBorderDarkHex: String = "#D9B44A",
    val databaseParticipantTextHex: String = "#3F2F10",
    val databaseParticipantTextDarkHex: String = "#FFF0C9",
    val lifelineHex: String = "#95A3BA",
    val lifelineDarkHex: String = "#B4C0D1",
    val databaseLifelineHex: String = "#C9A227",
    val databaseLifelineDarkHex: String = "#D9B44A",
    val callHex: String = "#5877A6",
    val callDarkHex: String = "#EEF3F8",
    val databaseCallHex: String = "#A87414",
    val databaseCallDarkHex: String = "#FFD67A",
    val returnHex: String = "#A6ACB4",
    val returnDarkHex: String = "#E7EDF3",
    val createHex: String = "#6D5CB3",
    val createDarkHex: String = "#C0B2F1",
    val activationFillHex: String = "#FFECC1",
    val activationFillDarkHex: String = "#F3D88D",
    val activationBorderHex: String = "#D1AE61",
    val activationBorderDarkHex: String = "#E6C97E",
    val methodHighlightHex: String = "#FF8A3D",
    val methodHighlightDarkHex: String = "#FFB16E",
) {
    fun scenarioFillLightColor(): Color = parseColor(scenarioFillHex, Color(241, 244, 248))

    fun scenarioFillDarkColor(): Color = parseColor(scenarioFillDarkHex, Color(71, 77, 88))

    fun scenarioFillColor(): Color = themeColor(scenarioFillLightColor(), scenarioFillDarkColor())

    fun scenarioBorderLightColor(): Color = parseColor(scenarioBorderHex, Color(205, 213, 225))

    fun scenarioBorderDarkColor(): Color = parseColor(scenarioBorderDarkHex, Color(123, 134, 152))

    fun scenarioBorderColor(): Color = themeColor(scenarioBorderLightColor(), scenarioBorderDarkColor())

    fun participantFillLightColor(): Color = parseColor(participantFillHex, Color(227, 235, 246))

    fun participantFillDarkColor(): Color = parseColor(participantFillDarkHex, Color(84, 102, 126))

    fun participantFillColor(): Color = themeColor(participantFillLightColor(), participantFillDarkColor())

    fun participantBorderLightColor(): Color = parseColor(participantBorderHex, Color(120, 142, 177))

    fun participantBorderDarkColor(): Color = parseColor(participantBorderDarkHex, Color(161, 178, 204))

    fun participantBorderColor(): Color = themeColor(participantBorderLightColor(), participantBorderDarkColor())

    fun participantTextLightColor(): Color = parseColor(participantTextHex, Color(35, 39, 42))

    fun participantTextDarkColor(): Color = parseColor(participantTextDarkHex, Color(238, 243, 248))

    fun participantTextColor(): Color = themeColor(participantTextLightColor(), participantTextDarkColor())

    fun databaseParticipantFillLightColor(): Color = parseColor(databaseParticipantFillHex, Color(255, 242, 204))

    fun databaseParticipantFillDarkColor(): Color = parseColor(databaseParticipantFillDarkHex, Color(107, 87, 31))

    fun databaseParticipantFillColor(): Color = themeColor(databaseParticipantFillLightColor(), databaseParticipantFillDarkColor())

    fun databaseParticipantBorderLightColor(): Color = parseColor(databaseParticipantBorderHex, Color(201, 162, 39))

    fun databaseParticipantBorderDarkColor(): Color = parseColor(databaseParticipantBorderDarkHex, Color(217, 180, 74))

    fun databaseParticipantBorderColor(): Color = themeColor(databaseParticipantBorderLightColor(), databaseParticipantBorderDarkColor())

    fun databaseParticipantTextLightColor(): Color = parseColor(databaseParticipantTextHex, Color(63, 47, 16))

    fun databaseParticipantTextDarkColor(): Color = parseColor(databaseParticipantTextDarkHex, Color(255, 240, 201))

    fun databaseParticipantTextColor(): Color = themeColor(databaseParticipantTextLightColor(), databaseParticipantTextDarkColor())

    fun lifelineLightColor(): Color = parseColor(lifelineHex, Color(149, 163, 186))

    fun lifelineDarkColor(): Color = parseColor(lifelineDarkHex, Color(180, 192, 209))

    fun lifelineColor(): Color = themeColor(lifelineLightColor(), lifelineDarkColor())

    fun databaseLifelineLightColor(): Color = parseColor(databaseLifelineHex, Color(201, 162, 39))

    fun databaseLifelineDarkColor(): Color = parseColor(databaseLifelineDarkHex, Color(217, 180, 74))

    fun databaseLifelineColor(): Color = themeColor(databaseLifelineLightColor(), databaseLifelineDarkColor())

    fun callLightColor(): Color = parseColor(callHex, Color(88, 119, 166))

    fun callDarkColor(): Color = parseColor(callDarkHex, Color(238, 243, 248))

    fun callColor(): Color = themeColor(callLightColor(), callDarkColor())

    fun databaseCallLightColor(): Color = parseColor(databaseCallHex, Color(168, 116, 20))

    fun databaseCallDarkColor(): Color = parseColor(databaseCallDarkHex, Color(255, 214, 122))

    fun databaseCallColor(): Color = themeColor(databaseCallLightColor(), databaseCallDarkColor())

    fun returnLightColor(): Color = parseColor(returnHex, Color(166, 172, 180))

    fun returnDarkColor(): Color = parseColor(returnDarkHex, Color(231, 237, 243))

    fun returnColor(): Color = themeColor(returnLightColor(), returnDarkColor())

    fun createLightColor(): Color = parseColor(createHex, Color(109, 92, 179))

    fun createDarkColor(): Color = parseColor(createDarkHex, Color(192, 178, 241))

    fun createColor(): Color = themeColor(createLightColor(), createDarkColor())

    fun activationFillLightColor(): Color = parseColor(activationFillHex, Color(255, 236, 193))

    fun activationFillDarkColor(): Color = parseColor(activationFillDarkHex, Color(243, 216, 141))

    fun activationFillColor(): Color = themeColor(activationFillLightColor(), activationFillDarkColor())

    fun activationBorderLightColor(): Color = parseColor(activationBorderHex, Color(209, 174, 97))

    fun activationBorderDarkColor(): Color = parseColor(activationBorderDarkHex, Color(230, 201, 126))

    fun activationBorderColor(): Color = themeColor(activationBorderLightColor(), activationBorderDarkColor())

    fun methodHighlightLightColor(): Color = parseColor(methodHighlightHex, Color(255, 138, 61))

    fun methodHighlightDarkColor(): Color = parseColor(methodHighlightDarkHex, Color(255, 177, 110))

    fun methodHighlightColor(): Color = themeColor(methodHighlightLightColor(), methodHighlightDarkColor())

    private fun parseColor(hex: String, fallback: Color): Color =
        runCatching { Color.decode(hex) }.getOrDefault(fallback)

    private fun themeColor(light: Color, dark: Color): Color = if (JBColor.isBright()) light else dark
}

data class GraphUiPreferences(
    val graphOptions: GraphOptions = GraphOptions(),
    val visibleToolbarToggles: Set<ToolbarToggleId> = ToolbarToggleId.defaultVisible(),
    val showWhiteBackgroundForExport: Boolean = false,
    val copyButtonFormat: ClipboardExportFormat = ClipboardExportFormat.IMAGE,
    val colorSettings: GraphColorSettings = GraphColorSettings(),
)

data class SequenceUiPreferences(
    val sequenceOptions: GraphOptions = GraphOptions(),
    val visibleToolbarToggles: Set<SequenceToolbarToggleId> = SequenceToolbarToggleId.defaultVisible(),
    val showWhiteBackgroundForExport: Boolean = false,
    val showWhiteBackgroundForCopy: Boolean = false,
    val copyButtonFormat: ClipboardExportFormat = ClipboardExportFormat.IMAGE,
    val showReturnMessages: Boolean = false,
    val showActivationBars: Boolean = false,
    val showCreateMessages: Boolean = false,
    val colorSettings: SequenceColorSettings = SequenceColorSettings(),
)
