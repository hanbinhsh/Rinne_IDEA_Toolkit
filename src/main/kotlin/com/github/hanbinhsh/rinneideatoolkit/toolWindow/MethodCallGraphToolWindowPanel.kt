package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.github.hanbinhsh.rinneideatoolkit.MyBundle
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.GraphUiPreferences
import com.github.hanbinhsh.rinneideatoolkit.model.GraphViewState
import com.github.hanbinhsh.rinneideatoolkit.model.ToolbarToggleId
import com.github.hanbinhsh.rinneideatoolkit.services.GraphDataService
import com.github.hanbinhsh.rinneideatoolkit.services.GraphPreferencesService
import com.github.hanbinhsh.rinneideatoolkit.services.GraphUpdateListener
import com.github.hanbinhsh.rinneideatoolkit.services.GraphUpdateTopic
import com.github.hanbinhsh.rinneideatoolkit.services.MethodCallAnalyzer
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

class MethodCallGraphToolWindowPanel(
    project: Project,
    private val toolWindow: ToolWindow,
) : JBPanel<MethodCallGraphToolWindowPanel>(BorderLayout()), Disposable {

    private val graphDataService = project.service<GraphDataService>()
    private val preferencesService = project.service<GraphPreferencesService>()
    private val titleLabel = JBLabel(MyBundle.message("toolWindow.noClassSelected"))
    private val summaryLabel = JBLabel(MyBundle.message("toolWindow.summaryNoData"))
    private val refreshButton = JButton(MyBundle.message("toolWindow.refresh"))
    private val reanalyzeButton = JButton(MyBundle.message("toolWindow.reanalyze"))
    private val settingsButton = JButton(MyBundle.message("toolWindow.settings"))
    private val showUnreachedCheckBox = JBCheckBox(MyBundle.message("toolWindow.showUnreachedMethods"))
    private val showUnreachedCallsCheckBox = JBCheckBox(MyBundle.message("toolWindow.showCallsFromUnreachedMethods"))
    private val showAccessorCheckBox = JBCheckBox(MyBundle.message("toolWindow.showAccessorMethods"))
    private val showVisibilityColorsCheckBox = JBCheckBox(MyBundle.message("toolWindow.showVisibilityColors"))
    private val showPrivateMethodsCheckBox = JBCheckBox(MyBundle.message("toolWindow.showPrivateMethods"))
    private val enableClickHighlightCheckBox = JBCheckBox(MyBundle.message("toolWindow.enableClickHighlight"))
    private val showDetailedEdgesCheckBox = JBCheckBox(MyBundle.message("toolWindow.showDetailedCallEdges"))
    private val showMapperTablesCheckBox = JBCheckBox(MyBundle.message("toolWindow.showMapperTables"))
    private val routeSameColumnEdgesCheckBox = JBCheckBox(MyBundle.message("toolWindow.routeSameColumnEdgesOutside"))
    private val drawEdgesOnTopCheckBox = JBCheckBox(MyBundle.message("toolWindow.drawEdgesOnTop"))
    private val drawArrowheadsOnTopCheckBox = JBCheckBox(MyBundle.message("toolWindow.drawArrowheadsOnTop"))
    private val whiteBackgroundCheckBox = JBCheckBox(MyBundle.message("toolWindow.whiteBackground"))
    private val exportButton = JButton(MyBundle.message("toolWindow.exportImage"))
    private val copyImageButton = JButton(MyBundle.message("toolWindow.copyImage"))
    private val zoomSlider = JSlider(25, 200, 100).apply {
        preferredSize = JBUI.size(132, preferredSize.height)
        toolTipText = MyBundle.message("toolWindow.zoomReset")
    }
    private val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
    private val emptyToggleLabel = JBLabel(MyBundle.message("toolWindow.noVisibleToggles")).apply {
        foreground = JBColor.GRAY
    }
    private val graphPanel = MethodCallGraphPanel(project, project.service<MethodCallAnalyzer>())
    private val sequenceContents = ConcurrentHashMap<String, Content>()
    private val toggleCheckBoxes = linkedMapOf(
        ToolbarToggleId.SHOW_UNREACHED_METHODS to showUnreachedCheckBox,
        ToolbarToggleId.SHOW_UNREACHED_CALLS to showUnreachedCallsCheckBox,
        ToolbarToggleId.SHOW_ACCESSOR_METHODS to showAccessorCheckBox,
        ToolbarToggleId.SHOW_VISIBILITY_COLORS to showVisibilityColorsCheckBox,
        ToolbarToggleId.SHOW_PRIVATE_METHODS to showPrivateMethodsCheckBox,
        ToolbarToggleId.ENABLE_CLICK_HIGHLIGHT to enableClickHighlightCheckBox,
        ToolbarToggleId.SHOW_DETAILED_CALL_EDGES to showDetailedEdgesCheckBox,
        ToolbarToggleId.SHOW_MAPPER_TABLES to showMapperTablesCheckBox,
        ToolbarToggleId.ROUTE_SAME_COLUMN_EDGES_OUTSIDE to routeSameColumnEdgesCheckBox,
        ToolbarToggleId.DRAW_EDGES_ON_TOP to drawEdgesOnTopCheckBox,
        ToolbarToggleId.DRAW_ARROWHEADS_ON_TOP to drawArrowheadsOnTopCheckBox,
        ToolbarToggleId.WHITE_BACKGROUND_FOR_EXPORT to whiteBackgroundCheckBox,
    )
    private var updatingFromState = false

    init {
        border = JBUI.Borders.empty(8)
        graphPanel.onZoomChanged = { zoomText ->
            val zoomPercent = zoomText.removeSuffix("%").toIntOrNull()
            if (zoomPercent != null) {
                if (zoomSlider.value != zoomPercent) {
                    zoomSlider.value = zoomPercent
                }
                zoomSlider.toolTipText = zoomText
            }
        }
        graphPanel.onSequenceAnalysisRequested = { method ->
            openSequenceAnalysisTab(project, method)
        }
        applyUiPreferences(preferencesService.getPreferences())

        val titlePanel = JPanel(BorderLayout()).apply {
            add(JBLabel(MyBundle.message("toolWindow.graphTitle")), BorderLayout.NORTH)
            add(titleLabel, BorderLayout.CENTER)
        }

        val actionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(refreshButton)
            add(reanalyzeButton)
            add(settingsButton)
            add(exportButton)
            add(copyImageButton)
            add(zoomSlider)
        }

        val topPanel = JPanel(BorderLayout(0, 6)).apply {
            add(titlePanel, BorderLayout.NORTH)
            add(summaryLabel, BorderLayout.CENTER)
            add(
                JPanel(BorderLayout(0, 6)).apply {
                    add(optionsPanel, BorderLayout.NORTH)
                    add(actionsPanel, BorderLayout.SOUTH)
                },
                BorderLayout.SOUTH,
            )
            border = JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(JBColor.border()),
                JBUI.Borders.emptyBottom(8),
            )
        }

        add(topPanel, BorderLayout.NORTH)
        add(JBScrollPane(graphPanel), BorderLayout.CENTER)

        refreshButton.addActionListener {
            graphDataService.refresh()
        }
        reanalyzeButton.addActionListener {
            graphDataService.reanalyzeSelectedClass()
        }
        showUnreachedCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showUnreachedCallsCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showAccessorCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showVisibilityColorsCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showPrivateMethodsCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        enableClickHighlightCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showDetailedEdgesCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showMapperTablesCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        routeSameColumnEdgesCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        drawEdgesOnTopCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        drawArrowheadsOnTopCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        whiteBackgroundCheckBox.addActionListener {
            if (!updatingFromState) {
                preferencesService.updateWhiteBackgroundForExport(whiteBackgroundCheckBox.isSelected)
            }
            graphPanel.useWhiteBackgroundForExport = whiteBackgroundCheckBox.isSelected
            sequenceContents.values.forEach { content ->
                (content.component as? SequenceDiagramToolWindowPanel)?.useWhiteBackgroundForCopyExport =
                    whiteBackgroundCheckBox.isSelected
            }
        }
        settingsButton.addActionListener {
            openSettingsDialog(project)
        }
        exportButton.addActionListener {
            exportGraphImage()
        }
        copyImageButton.addActionListener {
            copyGraphImage()
        }
        zoomSlider.addChangeListener {
            graphPanel.setZoomPercent(zoomSlider.value)
        }

        val connection = project.messageBus.connect(this)
        connection.subscribe(GraphUpdateTopic.TOPIC, GraphUpdateListener { state ->
            SwingUtilities.invokeLater {
                renderState(state)
            }
        })

        renderState(graphDataService.getState())
    }

    private fun renderState(state: GraphViewState) {
        val preferences = preferencesService.getPreferences()
        updatingFromState = true
        try {
            applyUiPreferences(preferences)
            titleLabel.text = state.rootClassDisplayName ?: MyBundle.message("toolWindow.noClassSelected")
            summaryLabel.text = state.graph?.let { graph ->
                MyBundle.message("toolWindow.summary", graph.nodes.size, graph.edges.size)
            } ?: MyBundle.message("toolWindow.summaryNoData")
            showUnreachedCheckBox.isSelected = state.options.showUnreachedMethods
            showUnreachedCallsCheckBox.isSelected = state.options.showCallsFromUnreachedMethods
            showUnreachedCallsCheckBox.isEnabled = state.options.showUnreachedMethods
            showAccessorCheckBox.isSelected = state.options.showAccessorMethods
            showVisibilityColorsCheckBox.isSelected = state.options.showVisibilityColors
            showPrivateMethodsCheckBox.isSelected = state.options.showPrivateMethods
            enableClickHighlightCheckBox.isSelected = state.options.enableClickHighlight
            showDetailedEdgesCheckBox.isSelected = state.options.showDetailedCallEdges
            showMapperTablesCheckBox.isSelected = state.options.showMapperTables
            routeSameColumnEdgesCheckBox.isSelected = state.options.routeSameColumnEdgesOutside
            drawEdgesOnTopCheckBox.isSelected = state.options.drawEdgesOnTop
            drawArrowheadsOnTopCheckBox.isSelected = state.options.drawArrowheadsOnTop
            graphPanel.renderGraph(state.graph, clearFocus = state.clearFocus)
        } finally {
            updatingFromState = false
        }
    }

    private fun publishOptions() {
        graphDataService.updateOptions(
            GraphOptions(
                showUnreachedMethods = showUnreachedCheckBox.isSelected,
                showCallsFromUnreachedMethods = showUnreachedCallsCheckBox.isSelected,
                showAccessorMethods = showAccessorCheckBox.isSelected,
                showVisibilityColors = showVisibilityColorsCheckBox.isSelected,
                showPrivateMethods = showPrivateMethodsCheckBox.isSelected,
                enableClickHighlight = enableClickHighlightCheckBox.isSelected,
                showDetailedCallEdges = showDetailedEdgesCheckBox.isSelected,
                showMapperTables = showMapperTablesCheckBox.isSelected,
                routeSameColumnEdgesOutside = routeSameColumnEdgesCheckBox.isSelected,
                drawEdgesOnTop = drawEdgesOnTopCheckBox.isSelected,
                drawArrowheadsOnTop = drawArrowheadsOnTopCheckBox.isSelected,
            ),
        )
    }

    private fun applyUiPreferences(preferences: GraphUiPreferences) {
        rebuildOptionsPanel(preferences.visibleToolbarToggles)
        whiteBackgroundCheckBox.isSelected = preferences.showWhiteBackgroundForExport
        graphPanel.useWhiteBackgroundForExport = preferences.showWhiteBackgroundForExport
        sequenceContents.values.forEach { content ->
            (content.component as? SequenceDiagramToolWindowPanel)?.useWhiteBackgroundForCopyExport =
                preferences.showWhiteBackgroundForExport
        }
        graphPanel.colorSettings = preferences.colorSettings
    }

    private fun rebuildOptionsPanel(visibleToggles: Set<ToolbarToggleId>) {
        optionsPanel.removeAll()
        val visibleDefinitions = TOOLBAR_TOGGLE_DEFINITIONS.filter { visibleToggles.contains(it.id) }
        if (visibleDefinitions.isEmpty()) {
            optionsPanel.add(emptyToggleLabel)
        } else {
            visibleDefinitions.forEach { definition ->
                optionsPanel.add(toggleCheckBoxes.getValue(definition.id))
            }
        }
        optionsPanel.revalidate()
        optionsPanel.repaint()
    }

    private fun openSettingsDialog(project: Project) {
        val dialog = MethodCallGraphSettingsDialog(project, preferencesService.getPreferences())
        if (!dialog.showAndGet()) {
            return
        }

        val updatedPreferences = dialog.selectedPreferences()
        preferencesService.setPreferences(updatedPreferences)
        applyUiPreferences(updatedPreferences)
        graphDataService.updateOptions(updatedPreferences.graphOptions)
    }

    private fun exportGraphImage() {
        val chooser = JFileChooser().apply {
            dialogTitle = MyBundle.message("toolWindow.exportImage")
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter(MyBundle.message("toolWindow.exportFileFilter"), "png")
            selectedFile = File(buildSuggestedFileName())
        }

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return
        }

        val selected = chooser.selectedFile ?: return
        val outputFile = if (selected.extension.equals("png", ignoreCase = true)) {
            selected
        } else {
            File(selected.parentFile ?: File("."), "${selected.name}.png")
        }

        runCatching {
            graphPanel.exportToPng(outputFile)
        }.onSuccess {
            Messages.showInfoMessage(
                this,
                MyBundle.message("toolWindow.exportSuccess", outputFile.absolutePath),
                MyBundle.message("toolWindow.exportImage"),
            )
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.exportFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message("toolWindow.exportImage"),
            )
        }
    }

    private fun copyGraphImage() {
        runCatching {
            graphPanel.copyImageToClipboard()
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.copyFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message("toolWindow.copyImage"),
            )
        }
    }

    private fun buildSuggestedFileName(): String {
        val className = graphDataService.getState().rootClassDisplayName
            ?: MyBundle.message("toolWindow.exportDefaultFileName")
        return "$className${MyBundle.message("toolWindow.exportFileSuffix")}.png"
    }

    private fun openSequenceAnalysisTab(project: Project, method: PsiMethod) {
        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method)
        val methodId = "${method.containingClass?.qualifiedName ?: method.name}#${method.name}(${method.parameterList.parameters.joinToString(",") { it.type.presentableText }})"
        val contentManager = toolWindow.contentManager
        sequenceContents[methodId]?.let { existing ->
            (existing.component as? SequenceDiagramToolWindowPanel)?.let { panel ->
                panel.useWhiteBackgroundForCopyExport = preferencesService.getSequencePreferences().showWhiteBackgroundForExport
                panel.refresh()
            }
            contentManager.setSelectedContent(existing, true)
            return
        }

        val panel = SequenceDiagramToolWindowPanel(project, pointer).apply {
            useWhiteBackgroundForCopyExport = preferencesService.getSequencePreferences().showWhiteBackgroundForExport
            onSequenceAnalysisRequested = { nextMethod ->
                openSequenceAnalysisTab(project, nextMethod)
            }
        }
        val content = ContentFactory.getInstance().createContent(
            panel,
            MyBundle.message("sequence.tabTitle", method.name),
            false,
        ).apply {
            isCloseable = true
            setDisposer(panel)
        }
        panel.onTitleChanged = { title ->
            content.displayName = title
        }

        Disposer.register(panel, Disposable {
            sequenceContents.remove(methodId)
        })

        sequenceContents[methodId] = content
        contentManager.addContent(content)
        contentManager.setSelectedContent(content, true)
    }

    override fun dispose() = Unit
}
