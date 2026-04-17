package com.github.hanbinhsh.rinneideatoolkit.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.github.hanbinhsh.rinneideatoolkit.model.GraphColorSettings
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.GraphUiPreferences
import com.github.hanbinhsh.rinneideatoolkit.model.ClipboardExportFormat
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceColorSettings
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceToolbarToggleId
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceUiPreferences
import com.github.hanbinhsh.rinneideatoolkit.model.ToolbarToggleId

@Service(Service.Level.PROJECT)
@State(name = "MethodCallGraphPreferences", storages = [Storage("RinneIdeaToolkit.xml")])
class GraphPreferencesService : PersistentStateComponent<GraphPreferencesService.GraphPreferencesState> {

    private var state = GraphPreferencesState()

    override fun getState(): GraphPreferencesState = state

    override fun loadState(state: GraphPreferencesState) {
        val migratedState = state.copy(
            visibleToolbarToggles = state.visibleToolbarToggles.toMutableList(),
            visibleSequenceToolbarToggles = state.visibleSequenceToolbarToggles.toMutableList(),
            selectionHighlightHex = if (
                state.selectionHighlightHex.equals(LEGACY_SELECTION_HIGHLIGHT_HEX, ignoreCase = true) ||
                state.selectionHighlightHex.equals(INTERMEDIATE_SELECTION_HIGHLIGHT_HEX, ignoreCase = true)
            ) {
                DEFAULT_SELECTION_HIGHLIGHT_HEX
            } else {
                state.selectionHighlightHex
            },
        )
        this.state = migratedState
    }

    fun getPreferences(): GraphUiPreferences = GraphUiPreferences(
        graphOptions = GraphOptions(
            showUnreachedMethods = state.showUnreachedMethods,
            showAccessorMethods = state.showAccessorMethods,
            showVisibilityColors = state.showVisibilityColors,
            showPrivateMethods = state.showPrivateMethods,
            enableClickHighlight = state.enableClickHighlight,
            showDetailedCallEdges = state.showDetailedCallEdges,
            showCallsFromUnreachedMethods = state.showCallsFromUnreachedMethods,
            routeSameColumnEdgesOutside = state.routeSameColumnEdgesOutside,
            drawEdgesOnTop = state.drawEdgesOnTop,
            drawArrowheadsOnTop = state.drawArrowheadsOnTop,
            showMapperTables = state.showMapperTables,
        ),
        visibleToolbarToggles = state.visibleToolbarToggles.mapNotNull(ToolbarToggleId::fromId).toSet(),
        showWhiteBackgroundForExport = state.showWhiteBackgroundForExport,
        copyButtonFormat = ClipboardExportFormat.fromId(state.graphCopyButtonFormat),
        colorSettings = GraphColorSettings(
            rootFillHex = state.rootFillHex,
            rootFillDarkHex = state.rootFillDarkHex,
            rootBorderHex = state.rootBorderHex,
            rootBorderDarkHex = state.rootBorderDarkHex,
            reachableFillHex = state.reachableFillHex,
            reachableFillDarkHex = state.reachableFillDarkHex,
            reachableBorderHex = state.reachableBorderHex,
            reachableBorderDarkHex = state.reachableBorderDarkHex,
            supplementalFillHex = state.supplementalFillHex,
            supplementalFillDarkHex = state.supplementalFillDarkHex,
            supplementalBorderHex = state.supplementalBorderHex,
            supplementalBorderDarkHex = state.supplementalBorderDarkHex,
            privateFillHex = state.privateFillHex,
            privateFillDarkHex = state.privateFillDarkHex,
            privateBorderHex = state.privateBorderHex,
            privateBorderDarkHex = state.privateBorderDarkHex,
            targetFocusHex = state.targetFocusHex,
            targetFocusDarkHex = state.targetFocusDarkHex,
            callerFocusHex = state.callerFocusHex,
            callerFocusDarkHex = state.callerFocusDarkHex,
            calleeFocusHex = state.calleeFocusHex,
            calleeFocusDarkHex = state.calleeFocusDarkHex,
            mixedFocusHex = state.mixedFocusHex,
            mixedFocusDarkHex = state.mixedFocusDarkHex,
            selectionHighlightHex = state.selectionHighlightHex,
            selectionHighlightDarkHex = state.selectionHighlightDarkHex,
            tableFillHex = state.tableFillHex,
            tableFillDarkHex = state.tableFillDarkHex,
            tableBorderHex = state.tableBorderHex,
            tableBorderDarkHex = state.tableBorderDarkHex,
            columnFillHex = state.columnFillHex,
            columnFillDarkHex = state.columnFillDarkHex,
            columnBorderHex = state.columnBorderHex,
            columnBorderDarkHex = state.columnBorderDarkHex,
            columnActionFillHex = state.columnActionFillHex,
            columnActionFillDarkHex = state.columnActionFillDarkHex,
            columnActionBorderHex = state.columnActionBorderHex,
            columnActionBorderDarkHex = state.columnActionBorderDarkHex,
        ),
    )

    fun setPreferences(preferences: GraphUiPreferences) {
        state = GraphPreferencesState(
            showUnreachedMethods = preferences.graphOptions.showUnreachedMethods,
            showAccessorMethods = preferences.graphOptions.showAccessorMethods,
            showVisibilityColors = preferences.graphOptions.showVisibilityColors,
            showPrivateMethods = preferences.graphOptions.showPrivateMethods,
            enableClickHighlight = preferences.graphOptions.enableClickHighlight,
            showDetailedCallEdges = preferences.graphOptions.showDetailedCallEdges,
            showCallsFromUnreachedMethods = preferences.graphOptions.showCallsFromUnreachedMethods,
            routeSameColumnEdgesOutside = preferences.graphOptions.routeSameColumnEdgesOutside,
            drawEdgesOnTop = preferences.graphOptions.drawEdgesOnTop,
            drawArrowheadsOnTop = preferences.graphOptions.drawArrowheadsOnTop,
            showMapperTables = preferences.graphOptions.showMapperTables,
            showWhiteBackgroundForExport = preferences.showWhiteBackgroundForExport,
            graphCopyButtonFormat = preferences.copyButtonFormat.id,
            visibleToolbarToggles = preferences.visibleToolbarToggles.map { it.id }.toMutableList(),
            rootFillHex = preferences.colorSettings.rootFillHex,
            rootFillDarkHex = preferences.colorSettings.rootFillDarkHex,
            rootBorderHex = preferences.colorSettings.rootBorderHex,
            rootBorderDarkHex = preferences.colorSettings.rootBorderDarkHex,
            reachableFillHex = preferences.colorSettings.reachableFillHex,
            reachableFillDarkHex = preferences.colorSettings.reachableFillDarkHex,
            reachableBorderHex = preferences.colorSettings.reachableBorderHex,
            reachableBorderDarkHex = preferences.colorSettings.reachableBorderDarkHex,
            supplementalFillHex = preferences.colorSettings.supplementalFillHex,
            supplementalFillDarkHex = preferences.colorSettings.supplementalFillDarkHex,
            supplementalBorderHex = preferences.colorSettings.supplementalBorderHex,
            supplementalBorderDarkHex = preferences.colorSettings.supplementalBorderDarkHex,
            privateFillHex = preferences.colorSettings.privateFillHex,
            privateFillDarkHex = preferences.colorSettings.privateFillDarkHex,
            privateBorderHex = preferences.colorSettings.privateBorderHex,
            privateBorderDarkHex = preferences.colorSettings.privateBorderDarkHex,
            targetFocusHex = preferences.colorSettings.targetFocusHex,
            targetFocusDarkHex = preferences.colorSettings.targetFocusDarkHex,
            callerFocusHex = preferences.colorSettings.callerFocusHex,
            callerFocusDarkHex = preferences.colorSettings.callerFocusDarkHex,
            calleeFocusHex = preferences.colorSettings.calleeFocusHex,
            calleeFocusDarkHex = preferences.colorSettings.calleeFocusDarkHex,
            mixedFocusHex = preferences.colorSettings.mixedFocusHex,
            mixedFocusDarkHex = preferences.colorSettings.mixedFocusDarkHex,
            selectionHighlightHex = preferences.colorSettings.selectionHighlightHex,
            selectionHighlightDarkHex = preferences.colorSettings.selectionHighlightDarkHex,
            tableFillHex = preferences.colorSettings.tableFillHex,
            tableFillDarkHex = preferences.colorSettings.tableFillDarkHex,
            tableBorderHex = preferences.colorSettings.tableBorderHex,
            tableBorderDarkHex = preferences.colorSettings.tableBorderDarkHex,
            columnFillHex = preferences.colorSettings.columnFillHex,
            columnFillDarkHex = preferences.colorSettings.columnFillDarkHex,
            columnBorderHex = preferences.colorSettings.columnBorderHex,
            columnBorderDarkHex = preferences.colorSettings.columnBorderDarkHex,
            columnActionFillHex = preferences.colorSettings.columnActionFillHex,
            columnActionFillDarkHex = preferences.colorSettings.columnActionFillDarkHex,
            columnActionBorderHex = preferences.colorSettings.columnActionBorderHex,
            columnActionBorderDarkHex = preferences.colorSettings.columnActionBorderDarkHex,
            sequenceShowAccessorMethods = state.sequenceShowAccessorMethods,
            sequenceShowPrivateMethods = state.sequenceShowPrivateMethods,
            sequenceShowMapperTables = state.sequenceShowMapperTables,
            sequenceShowWhiteBackgroundForCopy = state.sequenceShowWhiteBackgroundForCopy,
            sequenceCopyButtonFormat = state.sequenceCopyButtonFormat,
            sequenceShowReturnMessages = state.sequenceShowReturnMessages,
            sequenceShowActivationBars = state.sequenceShowActivationBars,
            sequenceShowCreateMessages = state.sequenceShowCreateMessages,
            sequenceScenarioFillHex = state.sequenceScenarioFillHex,
            sequenceScenarioFillDarkHex = state.sequenceScenarioFillDarkHex,
            sequenceScenarioBorderHex = state.sequenceScenarioBorderHex,
            sequenceScenarioBorderDarkHex = state.sequenceScenarioBorderDarkHex,
            sequenceParticipantFillHex = state.sequenceParticipantFillHex,
            sequenceParticipantFillDarkHex = state.sequenceParticipantFillDarkHex,
            sequenceParticipantBorderHex = state.sequenceParticipantBorderHex,
            sequenceParticipantBorderDarkHex = state.sequenceParticipantBorderDarkHex,
            sequenceParticipantTextHex = state.sequenceParticipantTextHex,
            sequenceParticipantTextDarkHex = state.sequenceParticipantTextDarkHex,
            sequenceDatabaseParticipantFillHex = state.sequenceDatabaseParticipantFillHex,
            sequenceDatabaseParticipantFillDarkHex = state.sequenceDatabaseParticipantFillDarkHex,
            sequenceDatabaseParticipantBorderHex = state.sequenceDatabaseParticipantBorderHex,
            sequenceDatabaseParticipantBorderDarkHex = state.sequenceDatabaseParticipantBorderDarkHex,
            sequenceDatabaseParticipantTextHex = state.sequenceDatabaseParticipantTextHex,
            sequenceDatabaseParticipantTextDarkHex = state.sequenceDatabaseParticipantTextDarkHex,
            sequenceLifelineHex = state.sequenceLifelineHex,
            sequenceLifelineDarkHex = state.sequenceLifelineDarkHex,
            sequenceCallHex = state.sequenceCallHex,
            sequenceCallDarkHex = state.sequenceCallDarkHex,
            sequenceReturnHex = state.sequenceReturnHex,
            sequenceReturnDarkHex = state.sequenceReturnDarkHex,
            sequenceCreateHex = state.sequenceCreateHex,
            sequenceCreateDarkHex = state.sequenceCreateDarkHex,
            sequenceActivationFillHex = state.sequenceActivationFillHex,
            sequenceActivationFillDarkHex = state.sequenceActivationFillDarkHex,
            sequenceActivationBorderHex = state.sequenceActivationBorderHex,
            sequenceActivationBorderDarkHex = state.sequenceActivationBorderDarkHex,
            sequenceMethodHighlightHex = state.sequenceMethodHighlightHex,
            sequenceMethodHighlightDarkHex = state.sequenceMethodHighlightDarkHex,
            visibleSequenceToolbarToggles = state.visibleSequenceToolbarToggles.toMutableList(),
        )
    }

    fun getSequencePreferences(): SequenceUiPreferences = SequenceUiPreferences(
        sequenceOptions = GraphOptions(
            showAccessorMethods = state.sequenceShowAccessorMethods,
            showPrivateMethods = state.sequenceShowPrivateMethods,
            showMapperTables = state.sequenceShowMapperTables,
        ),
        visibleToolbarToggles = state.visibleSequenceToolbarToggles.mapNotNull(SequenceToolbarToggleId::fromId).toSet(),
        showWhiteBackgroundForExport = state.showWhiteBackgroundForExport,
        showWhiteBackgroundForCopy = state.showWhiteBackgroundForExport,
        copyButtonFormat = ClipboardExportFormat.fromId(state.sequenceCopyButtonFormat),
        showReturnMessages = state.sequenceShowReturnMessages,
        showActivationBars = state.sequenceShowActivationBars,
        showCreateMessages = state.sequenceShowCreateMessages,
        colorSettings = SequenceColorSettings(
            scenarioFillHex = state.sequenceScenarioFillHex,
            scenarioFillDarkHex = state.sequenceScenarioFillDarkHex,
            scenarioBorderHex = state.sequenceScenarioBorderHex,
            scenarioBorderDarkHex = state.sequenceScenarioBorderDarkHex,
            participantFillHex = state.sequenceParticipantFillHex,
            participantFillDarkHex = state.sequenceParticipantFillDarkHex,
            participantBorderHex = state.sequenceParticipantBorderHex,
            participantBorderDarkHex = state.sequenceParticipantBorderDarkHex,
            participantTextHex = state.sequenceParticipantTextHex,
            participantTextDarkHex = state.sequenceParticipantTextDarkHex,
            databaseParticipantFillHex = state.sequenceDatabaseParticipantFillHex,
            databaseParticipantFillDarkHex = state.sequenceDatabaseParticipantFillDarkHex,
            databaseParticipantBorderHex = state.sequenceDatabaseParticipantBorderHex,
            databaseParticipantBorderDarkHex = state.sequenceDatabaseParticipantBorderDarkHex,
            databaseParticipantTextHex = state.sequenceDatabaseParticipantTextHex,
            databaseParticipantTextDarkHex = state.sequenceDatabaseParticipantTextDarkHex,
            lifelineHex = state.sequenceLifelineHex,
            lifelineDarkHex = state.sequenceLifelineDarkHex,
            databaseLifelineHex = state.sequenceDatabaseLifelineHex,
            databaseLifelineDarkHex = state.sequenceDatabaseLifelineDarkHex,
            callHex = state.sequenceCallHex,
            callDarkHex = state.sequenceCallDarkHex,
            databaseCallHex = state.sequenceDatabaseCallHex,
            databaseCallDarkHex = state.sequenceDatabaseCallDarkHex,
            returnHex = state.sequenceReturnHex,
            returnDarkHex = state.sequenceReturnDarkHex,
            createHex = state.sequenceCreateHex,
            createDarkHex = state.sequenceCreateDarkHex,
            activationFillHex = state.sequenceActivationFillHex,
            activationFillDarkHex = state.sequenceActivationFillDarkHex,
            activationBorderHex = state.sequenceActivationBorderHex,
            activationBorderDarkHex = state.sequenceActivationBorderDarkHex,
            methodHighlightHex = state.sequenceMethodHighlightHex,
            methodHighlightDarkHex = state.sequenceMethodHighlightDarkHex,
        ),
    )

    fun setSequencePreferences(preferences: SequenceUiPreferences) {
        state = state.copy(
            sequenceShowAccessorMethods = preferences.sequenceOptions.showAccessorMethods,
            sequenceShowPrivateMethods = preferences.sequenceOptions.showPrivateMethods,
            sequenceShowMapperTables = preferences.sequenceOptions.showMapperTables,
            visibleSequenceToolbarToggles = preferences.visibleToolbarToggles.map { it.id }.toMutableList(),
            showWhiteBackgroundForExport = preferences.showWhiteBackgroundForExport,
            sequenceShowWhiteBackgroundForCopy = preferences.showWhiteBackgroundForExport,
            sequenceCopyButtonFormat = preferences.copyButtonFormat.id,
            sequenceShowReturnMessages = preferences.showReturnMessages,
            sequenceShowActivationBars = preferences.showActivationBars,
            sequenceShowCreateMessages = preferences.showCreateMessages,
            sequenceScenarioFillHex = preferences.colorSettings.scenarioFillHex,
            sequenceScenarioFillDarkHex = preferences.colorSettings.scenarioFillDarkHex,
            sequenceScenarioBorderHex = preferences.colorSettings.scenarioBorderHex,
            sequenceScenarioBorderDarkHex = preferences.colorSettings.scenarioBorderDarkHex,
            sequenceParticipantFillHex = preferences.colorSettings.participantFillHex,
            sequenceParticipantFillDarkHex = preferences.colorSettings.participantFillDarkHex,
            sequenceParticipantBorderHex = preferences.colorSettings.participantBorderHex,
            sequenceParticipantBorderDarkHex = preferences.colorSettings.participantBorderDarkHex,
            sequenceParticipantTextHex = preferences.colorSettings.participantTextHex,
            sequenceParticipantTextDarkHex = preferences.colorSettings.participantTextDarkHex,
            sequenceDatabaseParticipantFillHex = preferences.colorSettings.databaseParticipantFillHex,
            sequenceDatabaseParticipantFillDarkHex = preferences.colorSettings.databaseParticipantFillDarkHex,
            sequenceDatabaseParticipantBorderHex = preferences.colorSettings.databaseParticipantBorderHex,
            sequenceDatabaseParticipantBorderDarkHex = preferences.colorSettings.databaseParticipantBorderDarkHex,
            sequenceDatabaseParticipantTextHex = preferences.colorSettings.databaseParticipantTextHex,
            sequenceDatabaseParticipantTextDarkHex = preferences.colorSettings.databaseParticipantTextDarkHex,
            sequenceLifelineHex = preferences.colorSettings.lifelineHex,
            sequenceLifelineDarkHex = preferences.colorSettings.lifelineDarkHex,
            sequenceDatabaseLifelineHex = preferences.colorSettings.databaseLifelineHex,
            sequenceDatabaseLifelineDarkHex = preferences.colorSettings.databaseLifelineDarkHex,
            sequenceCallHex = preferences.colorSettings.callHex,
            sequenceCallDarkHex = preferences.colorSettings.callDarkHex,
            sequenceDatabaseCallHex = preferences.colorSettings.databaseCallHex,
            sequenceDatabaseCallDarkHex = preferences.colorSettings.databaseCallDarkHex,
            sequenceReturnHex = preferences.colorSettings.returnHex,
            sequenceReturnDarkHex = preferences.colorSettings.returnDarkHex,
            sequenceCreateHex = preferences.colorSettings.createHex,
            sequenceCreateDarkHex = preferences.colorSettings.createDarkHex,
            sequenceActivationFillHex = preferences.colorSettings.activationFillHex,
            sequenceActivationFillDarkHex = preferences.colorSettings.activationFillDarkHex,
            sequenceActivationBorderHex = preferences.colorSettings.activationBorderHex,
            sequenceActivationBorderDarkHex = preferences.colorSettings.activationBorderDarkHex,
            sequenceMethodHighlightHex = preferences.colorSettings.methodHighlightHex,
            sequenceMethodHighlightDarkHex = preferences.colorSettings.methodHighlightDarkHex,
        )
    }

    fun updateSequenceOptions(options: GraphOptions) {
        setSequencePreferences(getSequencePreferences().copy(sequenceOptions = options))
    }

    fun updateGraphOptions(options: GraphOptions) {
        setPreferences(getPreferences().copy(graphOptions = options))
    }

    fun updateWhiteBackgroundForExport(enabled: Boolean) {
        setPreferences(getPreferences().copy(showWhiteBackgroundForExport = enabled))
    }

    fun resetGraphPreferences(): GraphUiPreferences {
        setPreferences(GraphUiPreferences())
        return getPreferences()
    }

    fun resetSequencePreferences(): SequenceUiPreferences {
        setSequencePreferences(SequenceUiPreferences())
        return getSequencePreferences()
    }

    data class GraphPreferencesState(
        var showUnreachedMethods: Boolean = false,
        var showAccessorMethods: Boolean = false,
        var showVisibilityColors: Boolean = true,
        var showPrivateMethods: Boolean = true,
        var enableClickHighlight: Boolean = true,
        var showDetailedCallEdges: Boolean = true,
        var showCallsFromUnreachedMethods: Boolean = false,
        var routeSameColumnEdgesOutside: Boolean = true,
        var drawEdgesOnTop: Boolean = false,
        var drawArrowheadsOnTop: Boolean = false,
        var showMapperTables: Boolean = false,
        var showWhiteBackgroundForExport: Boolean = false,
        var graphCopyButtonFormat: String = ClipboardExportFormat.IMAGE.id,
        var visibleToolbarToggles: MutableList<String> = ToolbarToggleId.defaultVisible().map { it.id }.toMutableList(),
        var rootFillHex: String = "#E0EBFF",
        var rootFillDarkHex: String = "#42608F",
        var rootBorderHex: String = "#7792D6",
        var rootBorderDarkHex: String = "#96B5EA",
        var reachableFillHex: String = "#E2F4E9",
        var reachableFillDarkHex: String = "#4A715F",
        var reachableBorderHex: String = "#79A287",
        var reachableBorderDarkHex: String = "#97C7A4",
        var supplementalFillHex: String = "#F4F0E6",
        var supplementalFillDarkHex: String = "#6C6658",
        var supplementalBorderHex: String = "#B59B66",
        var supplementalBorderDarkHex: String = "#D2B980",
        var privateFillHex: String = "#FFE7EA",
        var privateFillDarkHex: String = "#8A5662",
        var privateBorderHex: String = "#D47686",
        var privateBorderDarkHex: String = "#E2A1AE",
        var targetFocusHex: String = "#E6B850",
        var targetFocusDarkHex: String = "#E4B14A",
        var callerFocusHex: String = "#578BEA",
        var callerFocusDarkHex: String = "#7BA6F1",
        var calleeFocusHex: String = "#4AB584",
        var calleeFocusDarkHex: String = "#67C59A",
        var mixedFocusHex: String = "#A276D6",
        var mixedFocusDarkHex: String = "#B38DE0",
        var selectionHighlightHex: String = DEFAULT_SELECTION_HIGHLIGHT_HEX,
        var selectionHighlightDarkHex: String = "#FF6E8A",
        var tableFillHex: String = "#FFF2CC",
        var tableFillDarkHex: String = "#7A6840",
        var tableBorderHex: String = "#C9A227",
        var tableBorderDarkHex: String = "#E4C45A",
        var columnFillHex: String = "#FDEBD7",
        var columnFillDarkHex: String = "#7B5A3F",
        var columnBorderHex: String = "#D28B45",
        var columnBorderDarkHex: String = "#E8A76B",
        var columnActionFillHex: String = "#F7E1FF",
        var columnActionFillDarkHex: String = "#654779",
        var columnActionBorderHex: String = "#A665D6",
        var columnActionBorderDarkHex: String = "#C792F0",
        var sequenceShowAccessorMethods: Boolean = false,
        var sequenceShowPrivateMethods: Boolean = true,
        var sequenceShowMapperTables: Boolean = false,
        var sequenceShowWhiteBackgroundForCopy: Boolean = false,
        var sequenceCopyButtonFormat: String = ClipboardExportFormat.IMAGE.id,
        var sequenceShowReturnMessages: Boolean = false,
        var sequenceShowActivationBars: Boolean = false,
        var sequenceShowCreateMessages: Boolean = false,
        var sequenceScenarioFillHex: String = "#F1F4F8",
        var sequenceScenarioFillDarkHex: String = "#474D58",
        var sequenceScenarioBorderHex: String = "#CDD5E1",
        var sequenceScenarioBorderDarkHex: String = "#7B8698",
        var sequenceParticipantFillHex: String = "#E3EBF6",
        var sequenceParticipantFillDarkHex: String = "#54667E",
        var sequenceParticipantBorderHex: String = "#788EB1",
        var sequenceParticipantBorderDarkHex: String = "#A1B2CC",
        var sequenceParticipantTextHex: String = "#23272A",
        var sequenceParticipantTextDarkHex: String = "#EEF3F8",
        var sequenceDatabaseParticipantFillHex: String = "#FFF2CC",
        var sequenceDatabaseParticipantFillDarkHex: String = "#6B571F",
        var sequenceDatabaseParticipantBorderHex: String = "#C9A227",
        var sequenceDatabaseParticipantBorderDarkHex: String = "#D9B44A",
        var sequenceDatabaseParticipantTextHex: String = "#3F2F10",
        var sequenceDatabaseParticipantTextDarkHex: String = "#FFF0C9",
        var sequenceLifelineHex: String = "#95A3BA",
        var sequenceLifelineDarkHex: String = "#B4C0D1",
        var sequenceDatabaseLifelineHex: String = "#C9A227",
        var sequenceDatabaseLifelineDarkHex: String = "#D9B44A",
        var sequenceCallHex: String = "#5877A6",
        var sequenceCallDarkHex: String = "#EEF3F8",
        var sequenceDatabaseCallHex: String = "#A87414",
        var sequenceDatabaseCallDarkHex: String = "#FFD67A",
        var sequenceReturnHex: String = "#A6ACB4",
        var sequenceReturnDarkHex: String = "#E7EDF3",
        var sequenceCreateHex: String = "#6D5CB3",
        var sequenceCreateDarkHex: String = "#C0B2F1",
        var sequenceActivationFillHex: String = "#FFECC1",
        var sequenceActivationFillDarkHex: String = "#F3D88D",
        var sequenceActivationBorderHex: String = "#D1AE61",
        var sequenceActivationBorderDarkHex: String = "#E6C97E",
        var sequenceMethodHighlightHex: String = "#FF8A3D",
        var sequenceMethodHighlightDarkHex: String = "#FFB16E",
        var visibleSequenceToolbarToggles: MutableList<String> =
            SequenceToolbarToggleId.defaultVisible().map { it.id }.toMutableList(),
    )

    private companion object {
        const val LEGACY_SELECTION_HIGHLIGHT_HEX = "#F08A3C"
        const val INTERMEDIATE_SELECTION_HIGHLIGHT_HEX = "#19BFD3"
        const val DEFAULT_SELECTION_HIGHLIGHT_HEX = "#FF4D6D"
    }
}
