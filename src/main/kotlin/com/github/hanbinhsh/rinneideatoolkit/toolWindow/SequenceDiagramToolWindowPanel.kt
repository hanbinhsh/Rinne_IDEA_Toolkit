package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.github.hanbinhsh.rinneideatoolkit.MyBundle
import com.github.hanbinhsh.rinneideatoolkit.model.ClipboardExportFormat
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceDiagram
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceToolbarToggleId
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceUiPreferences
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceViewState
import com.github.hanbinhsh.rinneideatoolkit.services.GraphPreferencesService
import com.github.hanbinhsh.rinneideatoolkit.services.GraphUpdateListener
import com.github.hanbinhsh.rinneideatoolkit.services.GraphUpdateTopic
import com.github.hanbinhsh.rinneideatoolkit.services.SequenceDiagramAnalyzer
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JSlider
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

class SequenceDiagramToolWindowPanel(
    private val project: Project,
    private val methodPointer: SmartPsiElementPointer<PsiMethod>,
) : JBPanel<SequenceDiagramToolWindowPanel>(BorderLayout()), Disposable {

    private val sequenceAnalyzer = project.service<SequenceDiagramAnalyzer>()
    private val preferencesService = project.service<GraphPreferencesService>()
    private val titleLabel = JBLabel(MyBundle.message("sequence.loading"))
    private val summaryLabel = JBLabel(MyBundle.message("sequence.summaryNoData"))
    private val refreshButton = JButton(MyBundle.message("toolWindow.refresh"))
    private val settingsButton = JButton(MyBundle.message("toolWindow.settings"))
    private val highlightTargetMethodButton = JButton(MyBundle.message("sequence.highlightTargetMethod"))
    private val showAccessorCheckBox = JBCheckBox(MyBundle.message("toolWindow.showAccessorMethods"))
    private val showPrivateMethodsCheckBox = JBCheckBox(MyBundle.message("toolWindow.showPrivateMethods"))
    private val showMapperTablesCheckBox = JBCheckBox(MyBundle.message("toolWindow.showMapperTables"))
    private val showReturnMessagesCheckBox = JBCheckBox(MyBundle.message("sequence.showReturnMessages"))
    private val showActivationBarsCheckBox = JBCheckBox(MyBundle.message("sequence.showActivationBars"))
    private val showCreateMessagesCheckBox = JBCheckBox(MyBundle.message("sequence.showCreateMessages"))
    private val whiteBackgroundCheckBox = JBCheckBox(MyBundle.message("toolWindow.whiteBackground"))
    private val exportButton = JButton(MyBundle.message("toolWindow.exportMenu"))
    private val copyImageButton = JButton(MyBundle.message("toolWindow.copyImage"))
    private val zoomSlider = JSlider(25, 200, 100).apply {
        preferredSize = JBUI.size(132, preferredSize.height)
        toolTipText = MyBundle.message("toolWindow.zoomReset")
    }
    private val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
    private val emptyToggleLabel = JBLabel(MyBundle.message("sequence.noVisibleToggles")).apply {
        foreground = JBColor.GRAY
    }
    private val sequencePanel = SequenceDiagramPanel()
    private val toggleCheckBoxes = linkedMapOf(
        SequenceToolbarToggleId.SHOW_ACCESSOR_METHODS to showAccessorCheckBox,
        SequenceToolbarToggleId.SHOW_PRIVATE_METHODS to showPrivateMethodsCheckBox,
        SequenceToolbarToggleId.SHOW_MAPPER_TABLES to showMapperTablesCheckBox,
        SequenceToolbarToggleId.SHOW_RETURN_MESSAGES to showReturnMessagesCheckBox,
        SequenceToolbarToggleId.SHOW_ACTIVATION_BARS to showActivationBarsCheckBox,
        SequenceToolbarToggleId.SHOW_CREATE_MESSAGES to showCreateMessagesCheckBox,
        SequenceToolbarToggleId.WHITE_BACKGROUND_FOR_EXPORT to whiteBackgroundCheckBox,
    )
    private var state: SequenceViewState = SequenceViewState(loading = true)
    private var sequenceOptions: GraphOptions = preferencesService.getSequencePreferences().sequenceOptions
    private val loadRequestId = AtomicInteger()
    private var updatingFromState = false
    var useWhiteBackgroundForCopyExport: Boolean = false
        set(value) {
            field = value
            sequencePanel.useWhiteBackgroundForCopyExport = value
            if (!updatingFromState) {
                updatingFromState = true
                try {
                    whiteBackgroundCheckBox.isSelected = value
                } finally {
                    updatingFromState = false
                }
            }
        }
    var onTitleChanged: ((String) -> Unit)? = null
    var onSequenceAnalysisRequested: ((PsiMethod) -> Unit)? = null

    init {
        border = JBUI.Borders.empty(8)
        applySequencePreferences(preferencesService.getSequencePreferences())
        sequencePanel.onZoomChanged = { zoomText ->
            val zoomPercent = zoomText.removeSuffix("%").toIntOrNull()
            if (zoomPercent != null) {
                if (zoomSlider.value != zoomPercent) {
                    zoomSlider.value = zoomPercent
                }
                zoomSlider.toolTipText = zoomText
            }
        }
        sequencePanel.onSequenceAnalysisRequested = { method ->
            onSequenceAnalysisRequested?.invoke(method)
        }

        val titlePanel = JPanel(BorderLayout()).apply {
            add(JBLabel(MyBundle.message("sequence.title")), BorderLayout.NORTH)
            add(titleLabel, BorderLayout.CENTER)
        }

        val actionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(refreshButton)
            add(settingsButton)
            add(highlightTargetMethodButton)
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
        add(JBScrollPane(sequencePanel), BorderLayout.CENTER)

        refreshButton.addActionListener { refresh() }
        settingsButton.addActionListener { openSettingsDialog() }
        highlightTargetMethodButton.addActionListener { sequencePanel.toggleTargetMethodHighlight() }
        showAccessorCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showPrivateMethodsCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showMapperTablesCheckBox.addActionListener {
            if (!updatingFromState) {
                publishOptions()
            }
        }
        showReturnMessagesCheckBox.addActionListener {
            if (!updatingFromState) {
                preferencesService.setSequencePreferences(
                    preferencesService.getSequencePreferences().copy(
                        showReturnMessages = showReturnMessagesCheckBox.isSelected,
                    ),
                )
            }
            sequencePanel.showReturnMessages = showReturnMessagesCheckBox.isSelected
        }
        showActivationBarsCheckBox.addActionListener {
            if (!updatingFromState) {
                preferencesService.setSequencePreferences(
                    preferencesService.getSequencePreferences().copy(
                        showActivationBars = showActivationBarsCheckBox.isSelected,
                    ),
                )
            }
            sequencePanel.showActivationBars = showActivationBarsCheckBox.isSelected
        }
        showCreateMessagesCheckBox.addActionListener {
            if (!updatingFromState) {
                preferencesService.setSequencePreferences(
                    preferencesService.getSequencePreferences().copy(
                        showCreateMessages = showCreateMessagesCheckBox.isSelected,
                    ),
                )
            }
            sequencePanel.showCreateMessages = showCreateMessagesCheckBox.isSelected
        }
        whiteBackgroundCheckBox.addActionListener {
            if (!updatingFromState) {
                preferencesService.setSequencePreferences(
                    preferencesService.getSequencePreferences().copy(
                        showWhiteBackgroundForExport = whiteBackgroundCheckBox.isSelected,
                    ),
                )
            }
            useWhiteBackgroundForCopyExport = whiteBackgroundCheckBox.isSelected
        }
        exportButton.addActionListener { showExportMenu() }
        copyImageButton.addActionListener { copyDiagram() }
        zoomSlider.addChangeListener { sequencePanel.setZoomPercent(zoomSlider.value) }

        val connection = project.messageBus.connect(this)
        connection.subscribe(GraphUpdateTopic.TOPIC, GraphUpdateListener {
            SwingUtilities.invokeLater {
                val prefs = preferencesService.getSequencePreferences()
                useWhiteBackgroundForCopyExport = prefs.showWhiteBackgroundForExport
                sequencePanel.showReturnMessages = prefs.showReturnMessages
                sequencePanel.showActivationBars = prefs.showActivationBars
                sequencePanel.showCreateMessages = prefs.showCreateMessages
                refresh()
            }
        })

        refresh()
    }

    fun refresh() {
        val requestId = loadRequestId.incrementAndGet()
        renderState(state.copy(loading = true, errorMessage = null))
        ReadAction
            .nonBlocking<SequenceViewState> {
                val psiMethod = methodPointer.element
                    ?: return@nonBlocking SequenceViewState(
                        loading = false,
                        errorMessage = MyBundle.message("sequence.error.methodUnavailable"),
                    )
                val diagram = sequenceAnalyzer.analyze(psiMethod, sequenceOptions)
                SequenceViewState(
                    targetMethodDisplayName = diagram.targetMethodDisplayName,
                    diagram = diagram,
                    loading = false,
                )
            }
            .expireWith(project)
            .coalesceBy(this, methodPointer, sequenceOptions)
            .finishOnUiThread(ModalityState.any()) { nextState ->
                if (requestId != loadRequestId.get()) {
                    return@finishOnUiThread
                }
                renderState(nextState)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun renderState(state: SequenceViewState) {
        this.state = state
        sequencePanel.useWhiteBackgroundForCopyExport = useWhiteBackgroundForCopyExport
        syncControlsFromPreferences()
        val targetDisplay = state.targetMethodDisplayName ?: ReadAction.compute<String?, RuntimeException> {
            methodPointer.element?.let {
                "${it.containingClass?.name ?: it.name}.${it.name}"
            }
        } ?: MyBundle.message("sequence.unavailable")
        titleLabel.text = targetDisplay
        onTitleChanged?.invoke(MyBundle.message("sequence.tabTitle", targetDisplay.substringAfter('.')))

        summaryLabel.text = when {
            state.loading -> MyBundle.message("sequence.loading")
            state.errorMessage != null -> state.errorMessage
            else -> state.diagram?.let(::buildSummary) ?: MyBundle.message("sequence.summaryNoData")
        }
        sequencePanel.renderDiagram(state.diagram)
    }

    private fun buildSummary(diagram: SequenceDiagram): String {
        val messageCount = diagram.scenarios.sumOf { it.messages.size }
        return MyBundle.message("sequence.summary", diagram.scenarios.size, messageCount)
    }

    private fun publishOptions() {
        sequenceOptions = GraphOptions(
            showAccessorMethods = showAccessorCheckBox.isSelected,
            showPrivateMethods = showPrivateMethodsCheckBox.isSelected,
            showMapperTables = showMapperTablesCheckBox.isSelected,
        )
        preferencesService.updateSequenceOptions(sequenceOptions)
        refresh()
    }

    private fun applySequencePreferences(preferences: SequenceUiPreferences) {
        sequenceOptions = preferences.sequenceOptions
        rebuildOptionsPanel(preferences.visibleToolbarToggles)
        useWhiteBackgroundForCopyExport = preferences.showWhiteBackgroundForExport
        copyImageButton.text = MyBundle.message(clipboardButtonLabel(preferences.copyButtonFormat))
        sequencePanel.showReturnMessages = preferences.showReturnMessages
        sequencePanel.showActivationBars = preferences.showActivationBars
        sequencePanel.showCreateMessages = preferences.showCreateMessages
        sequencePanel.colorSettings = preferences.colorSettings
        syncControlsFromPreferences(preferences)
    }

    private fun syncControlsFromPreferences(preferences: SequenceUiPreferences = preferencesService.getSequencePreferences()) {
        updatingFromState = true
        try {
            sequenceOptions = preferences.sequenceOptions
            showAccessorCheckBox.isSelected = preferences.sequenceOptions.showAccessorMethods
            showPrivateMethodsCheckBox.isSelected = preferences.sequenceOptions.showPrivateMethods
            showMapperTablesCheckBox.isSelected = preferences.sequenceOptions.showMapperTables
            showReturnMessagesCheckBox.isSelected = preferences.showReturnMessages
            showActivationBarsCheckBox.isSelected = preferences.showActivationBars
            showCreateMessagesCheckBox.isSelected = preferences.showCreateMessages
            whiteBackgroundCheckBox.isSelected = preferences.showWhiteBackgroundForExport
        } finally {
            updatingFromState = false
        }
    }

    private fun rebuildOptionsPanel(visibleToggles: Set<SequenceToolbarToggleId>) {
        optionsPanel.removeAll()
        val visibleDefinitions = SEQUENCE_TOOLBAR_TOGGLE_DEFINITIONS.filter { visibleToggles.contains(it.id) }
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

    private fun openSettingsDialog() {
        val dialog = SequenceDiagramSettingsDialog(project, preferencesService.getSequencePreferences())
        if (!dialog.showAndGet()) {
            return
        }
        val updatedPreferences = dialog.selectedPreferences()
        preferencesService.setSequencePreferences(updatedPreferences)
        applySequencePreferences(updatedPreferences)
        refresh()
    }

    private fun showExportMenu() {
        JPopupMenu().apply {
            add(createExportMenuItem(ClipboardExportFormat.IMAGE))
            add(createExportMenuItem(ClipboardExportFormat.SVG))
            add(createExportMenuItem(ClipboardExportFormat.MERMAID))
            addSeparator()
            add(createCopyMenuItem(ClipboardExportFormat.IMAGE))
            add(createCopyMenuItem(ClipboardExportFormat.SVG))
            add(createCopyMenuItem(ClipboardExportFormat.MERMAID))
        }.show(exportButton, 0, exportButton.height)
    }

    private fun createExportMenuItem(format: ClipboardExportFormat) =
        javax.swing.JMenuItem(
            MyBundle.message(
                when (format) {
                    ClipboardExportFormat.IMAGE -> "toolWindow.exportImage"
                    ClipboardExportFormat.SVG -> "toolWindow.exportSvg"
                    ClipboardExportFormat.MERMAID -> "toolWindow.exportMermaid"
                },
            ),
        ).apply {
            addActionListener { exportDiagram(format) }
        }

    private fun createCopyMenuItem(format: ClipboardExportFormat) =
        javax.swing.JMenuItem(
            MyBundle.message(
                when (format) {
                    ClipboardExportFormat.IMAGE -> "toolWindow.copyImage"
                    ClipboardExportFormat.SVG -> "toolWindow.copySvg"
                    ClipboardExportFormat.MERMAID -> "toolWindow.copyMermaid"
                },
            ),
        ).apply {
            addActionListener { copyDiagram(format) }
        }

    private fun exportDiagram(format: ClipboardExportFormat) {
        val chooser = JFileChooser().apply {
            dialogTitle = MyBundle.message(
                when (format) {
                    ClipboardExportFormat.IMAGE -> "toolWindow.exportImage"
                    ClipboardExportFormat.SVG -> "toolWindow.exportSvg"
                    ClipboardExportFormat.MERMAID -> "toolWindow.exportMermaid"
                },
            )
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter(
                MyBundle.message(
                    when (format) {
                        ClipboardExportFormat.IMAGE -> "toolWindow.exportFileFilter"
                        ClipboardExportFormat.SVG -> "toolWindow.exportSvgFileFilter"
                        ClipboardExportFormat.MERMAID -> "toolWindow.exportMermaidFileFilter"
                    },
                ),
                fileExtension(format),
            )
            selectedFile = File(buildSuggestedFileName(fileExtension(format)))
        }

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return
        }

        val selected = chooser.selectedFile ?: return
        val extension = fileExtension(format)
        val outputFile = if (selected.extension.equals(extension, ignoreCase = true)) {
            selected
        } else {
            File(selected.parentFile ?: File("."), "${selected.name}.$extension")
        }

        runCatching {
            when (format) {
                ClipboardExportFormat.IMAGE -> sequencePanel.exportToPng(outputFile)
                ClipboardExportFormat.SVG -> sequencePanel.exportToSvg(outputFile)
                ClipboardExportFormat.MERMAID -> sequencePanel.exportToMermaid(outputFile)
            }
        }.onSuccess {
            Messages.showInfoMessage(
                this,
                MyBundle.message(
                    if (format == ClipboardExportFormat.IMAGE) "toolWindow.exportSuccess" else "toolWindow.exportTextSuccess",
                    outputFile.absolutePath,
                ),
                chooser.dialogTitle,
            )
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message(
                    if (format == ClipboardExportFormat.IMAGE) "toolWindow.exportFailed" else "toolWindow.exportTextFailed",
                    error.message ?: error.javaClass.simpleName,
                ),
                chooser.dialogTitle,
            )
        }
    }

    private fun copyDiagram() {
        copyDiagram(preferencesService.getSequencePreferences().copyButtonFormat)
    }

    private fun copyDiagram(format: ClipboardExportFormat) {
        runCatching {
            when (format) {
                ClipboardExportFormat.IMAGE -> sequencePanel.copyImageToClipboard()
                ClipboardExportFormat.SVG -> sequencePanel.copySvgToClipboard()
                ClipboardExportFormat.MERMAID -> sequencePanel.copyMermaidToClipboard()
            }
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.copyFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message(
                    when (format) {
                        ClipboardExportFormat.IMAGE -> "toolWindow.copyImage"
                        ClipboardExportFormat.SVG -> "toolWindow.copySvg"
                        ClipboardExportFormat.MERMAID -> "toolWindow.copyMermaid"
                    },
                ),
            )
        }
    }

    private fun buildSuggestedFileName(extension: String): String {
        val methodName = state.targetMethodDisplayName
            ?.substringAfter('.')
            ?.substringBefore('(')
            ?: MyBundle.message("sequence.exportDefaultFileName")
        return "$methodName${MyBundle.message("sequence.exportFileSuffix")}.$extension"
    }

    override fun dispose() = Unit

    private fun fileExtension(format: ClipboardExportFormat): String = when (format) {
        ClipboardExportFormat.IMAGE -> "png"
        ClipboardExportFormat.SVG -> "svg"
        ClipboardExportFormat.MERMAID -> "mmd"
    }
}
