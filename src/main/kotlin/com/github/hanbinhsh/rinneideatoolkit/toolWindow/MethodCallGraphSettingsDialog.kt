package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import com.github.hanbinhsh.rinneideatoolkit.MyBundle
import com.github.hanbinhsh.rinneideatoolkit.model.ClipboardExportFormat
import com.github.hanbinhsh.rinneideatoolkit.model.GraphColorSettings
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.GraphUiPreferences
import com.github.hanbinhsh.rinneideatoolkit.model.ToolbarToggleId
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JColorChooser
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class MethodCallGraphSettingsDialog(
    project: Project,
    preferences: GraphUiPreferences,
) : DialogWrapper(project) {
    private val resetSettingsButton = JButton(MyBundle.message("toolWindow.resetSettings"))

    private val showUnreachedCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showUnreachedMethods"),
        preferences.graphOptions.showUnreachedMethods,
    )
    private val showUnreachedCallsCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showCallsFromUnreachedMethods"),
        preferences.graphOptions.showCallsFromUnreachedMethods,
    )
    private val showAccessorCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showAccessorMethods"),
        preferences.graphOptions.showAccessorMethods,
    )
    private val showVisibilityColorsCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showVisibilityColors"),
        preferences.graphOptions.showVisibilityColors,
    )
    private val showPrivateMethodsCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showPrivateMethods"),
        preferences.graphOptions.showPrivateMethods,
    )
    private val enableClickHighlightCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.enableClickHighlight"),
        preferences.graphOptions.enableClickHighlight,
    )
    private val showDetailedEdgesCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showDetailedCallEdges"),
        preferences.graphOptions.showDetailedCallEdges,
    )
    private val showMapperTablesCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showMapperTables"),
        preferences.graphOptions.showMapperTables,
    )
    private val routeSameColumnEdgesCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.routeSameColumnEdgesOutside"),
        preferences.graphOptions.routeSameColumnEdgesOutside,
    )
    private val drawEdgesOnTopCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.drawEdgesOnTop"),
        preferences.graphOptions.drawEdgesOnTop,
    )
    private val drawArrowheadsOnTopCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.drawArrowheadsOnTop"),
        preferences.graphOptions.drawArrowheadsOnTop,
    )
    private val whiteBackgroundCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.whiteBackground"),
        preferences.showWhiteBackgroundForExport,
    )
    private val copyButtonFormatComboBox = JComboBox(ClipboardExportFormat.values()).apply {
        selectedItem = preferences.copyButtonFormat
        renderer = ExportFormatListCellRenderer()
    }

    private val toolbarToggleCheckBoxes = linkedMapOf<ToolbarToggleId, JBCheckBox>().apply {
        TOOLBAR_TOGGLE_DEFINITIONS.forEach { definition ->
            put(
                definition.id,
                JBCheckBox(
                    MyBundle.message(definition.messageKey),
                    preferences.visibleToolbarToggles.contains(definition.id),
                ),
            )
        }
    }

    private val rootFillButtons = ThemeColorButtons(
        preferences.colorSettings.rootFillLightColor(),
        preferences.colorSettings.rootFillDarkColor(),
    )
    private val rootBorderButtons = ThemeColorButtons(
        preferences.colorSettings.rootBorderLightColor(),
        preferences.colorSettings.rootBorderDarkColor(),
    )
    private val reachableFillButtons = ThemeColorButtons(
        preferences.colorSettings.reachableFillLightColor(),
        preferences.colorSettings.reachableFillDarkColor(),
    )
    private val reachableBorderButtons = ThemeColorButtons(
        preferences.colorSettings.reachableBorderLightColor(),
        preferences.colorSettings.reachableBorderDarkColor(),
    )
    private val supplementalFillButtons = ThemeColorButtons(
        preferences.colorSettings.supplementalFillLightColor(),
        preferences.colorSettings.supplementalFillDarkColor(),
    )
    private val supplementalBorderButtons = ThemeColorButtons(
        preferences.colorSettings.supplementalBorderLightColor(),
        preferences.colorSettings.supplementalBorderDarkColor(),
    )
    private val privateFillButtons = ThemeColorButtons(
        preferences.colorSettings.privateFillLightColor(),
        preferences.colorSettings.privateFillDarkColor(),
    )
    private val privateBorderButtons = ThemeColorButtons(
        preferences.colorSettings.privateBorderLightColor(),
        preferences.colorSettings.privateBorderDarkColor(),
    )
    private val targetFocusButtons = ThemeColorButtons(
        preferences.colorSettings.targetFocusLightColor(),
        preferences.colorSettings.targetFocusDarkColor(),
    )
    private val callerFocusButtons = ThemeColorButtons(
        preferences.colorSettings.callerFocusLightColor(),
        preferences.colorSettings.callerFocusDarkColor(),
    )
    private val calleeFocusButtons = ThemeColorButtons(
        preferences.colorSettings.calleeFocusLightColor(),
        preferences.colorSettings.calleeFocusDarkColor(),
    )
    private val mixedFocusButtons = ThemeColorButtons(
        preferences.colorSettings.mixedFocusLightColor(),
        preferences.colorSettings.mixedFocusDarkColor(),
    )
    private val selectionHighlightButtons = ThemeColorButtons(
        preferences.colorSettings.selectionHighlightLightColor(),
        preferences.colorSettings.selectionHighlightDarkColor(),
    )
    private val tableFillButtons = ThemeColorButtons(
        preferences.colorSettings.tableFillLightColor(),
        preferences.colorSettings.tableFillDarkColor(),
    )
    private val tableBorderButtons = ThemeColorButtons(
        preferences.colorSettings.tableBorderLightColor(),
        preferences.colorSettings.tableBorderDarkColor(),
    )
    private val columnFillButtons = ThemeColorButtons(
        preferences.colorSettings.columnFillLightColor(),
        preferences.colorSettings.columnFillDarkColor(),
    )
    private val columnBorderButtons = ThemeColorButtons(
        preferences.colorSettings.columnBorderLightColor(),
        preferences.colorSettings.columnBorderDarkColor(),
    )
    private val columnActionFillButtons = ThemeColorButtons(
        preferences.colorSettings.columnActionFillLightColor(),
        preferences.colorSettings.columnActionFillDarkColor(),
    )
    private val columnActionBorderButtons = ThemeColorButtons(
        preferences.colorSettings.columnActionBorderLightColor(),
        preferences.colorSettings.columnActionBorderDarkColor(),
    )

    init {
        title = MyBundle.message("settings.title")
        init()
        showUnreachedCheckBox.addActionListener { updateDependentState() }
        resetSettingsButton.addActionListener {
            applyPreferences(GraphUiPreferences())
        }
        updateDependentState()
    }

    fun selectedPreferences(): GraphUiPreferences = GraphUiPreferences(
        graphOptions = GraphOptions(
            showUnreachedMethods = showUnreachedCheckBox.isSelected,
            showAccessorMethods = showAccessorCheckBox.isSelected,
            showVisibilityColors = showVisibilityColorsCheckBox.isSelected,
            showPrivateMethods = showPrivateMethodsCheckBox.isSelected,
            enableClickHighlight = enableClickHighlightCheckBox.isSelected,
            showDetailedCallEdges = showDetailedEdgesCheckBox.isSelected,
            showMapperTables = showMapperTablesCheckBox.isSelected,
            showCallsFromUnreachedMethods = showUnreachedCallsCheckBox.isSelected,
            routeSameColumnEdgesOutside = routeSameColumnEdgesCheckBox.isSelected,
            drawEdgesOnTop = drawEdgesOnTopCheckBox.isSelected,
            drawArrowheadsOnTop = drawArrowheadsOnTopCheckBox.isSelected,
        ),
        visibleToolbarToggles = toolbarToggleCheckBoxes
            .filterValues { it.isSelected }
            .keys,
        showWhiteBackgroundForExport = whiteBackgroundCheckBox.isSelected,
        copyButtonFormat = copyButtonFormatComboBox.selectedItem as? ClipboardExportFormat ?: ClipboardExportFormat.IMAGE,
        colorSettings = GraphColorSettings(
            rootFillHex = rootFillButtons.lightHex(),
            rootFillDarkHex = rootFillButtons.darkHex(),
            rootBorderHex = rootBorderButtons.lightHex(),
            rootBorderDarkHex = rootBorderButtons.darkHex(),
            reachableFillHex = reachableFillButtons.lightHex(),
            reachableFillDarkHex = reachableFillButtons.darkHex(),
            reachableBorderHex = reachableBorderButtons.lightHex(),
            reachableBorderDarkHex = reachableBorderButtons.darkHex(),
            supplementalFillHex = supplementalFillButtons.lightHex(),
            supplementalFillDarkHex = supplementalFillButtons.darkHex(),
            supplementalBorderHex = supplementalBorderButtons.lightHex(),
            supplementalBorderDarkHex = supplementalBorderButtons.darkHex(),
            privateFillHex = privateFillButtons.lightHex(),
            privateFillDarkHex = privateFillButtons.darkHex(),
            privateBorderHex = privateBorderButtons.lightHex(),
            privateBorderDarkHex = privateBorderButtons.darkHex(),
            targetFocusHex = targetFocusButtons.lightHex(),
            targetFocusDarkHex = targetFocusButtons.darkHex(),
            callerFocusHex = callerFocusButtons.lightHex(),
            callerFocusDarkHex = callerFocusButtons.darkHex(),
            calleeFocusHex = calleeFocusButtons.lightHex(),
            calleeFocusDarkHex = calleeFocusButtons.darkHex(),
            mixedFocusHex = mixedFocusButtons.lightHex(),
            mixedFocusDarkHex = mixedFocusButtons.darkHex(),
            selectionHighlightHex = selectionHighlightButtons.lightHex(),
            selectionHighlightDarkHex = selectionHighlightButtons.darkHex(),
            tableFillHex = tableFillButtons.lightHex(),
            tableFillDarkHex = tableFillButtons.darkHex(),
            tableBorderHex = tableBorderButtons.lightHex(),
            tableBorderDarkHex = tableBorderButtons.darkHex(),
            columnFillHex = columnFillButtons.lightHex(),
            columnFillDarkHex = columnFillButtons.darkHex(),
            columnBorderHex = columnBorderButtons.lightHex(),
            columnBorderDarkHex = columnBorderButtons.darkHex(),
            columnActionFillHex = columnActionFillButtons.lightHex(),
            columnActionFillDarkHex = columnActionFillButtons.darkHex(),
            columnActionBorderHex = columnActionBorderButtons.lightHex(),
            columnActionBorderDarkHex = columnActionBorderButtons.darkHex(),
        ),
    )

    override fun createCenterPanel(): JComponent {
        val tabs = JBTabbedPane().apply {
            addTab(MyBundle.message("settings.tab.switches"), createScrollableTab(buildSwitchesTab()))
            addTab(MyBundle.message("settings.tab.toolbar"), createScrollableTab(buildToolbarTab()))
            addTab(MyBundle.message("settings.tab.colors"), createScrollableTab(buildColorsTab()))
        }

        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(700, 520)
            add(tabs, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    border = JBUI.Borders.empty(0, 12, 12, 12)
                    add(resetSettingsButton)
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun applyPreferences(preferences: GraphUiPreferences) {
        showUnreachedCheckBox.isSelected = preferences.graphOptions.showUnreachedMethods
        showUnreachedCallsCheckBox.isSelected = preferences.graphOptions.showCallsFromUnreachedMethods
        showAccessorCheckBox.isSelected = preferences.graphOptions.showAccessorMethods
        showVisibilityColorsCheckBox.isSelected = preferences.graphOptions.showVisibilityColors
        showPrivateMethodsCheckBox.isSelected = preferences.graphOptions.showPrivateMethods
        enableClickHighlightCheckBox.isSelected = preferences.graphOptions.enableClickHighlight
        showDetailedEdgesCheckBox.isSelected = preferences.graphOptions.showDetailedCallEdges
        showMapperTablesCheckBox.isSelected = preferences.graphOptions.showMapperTables
        routeSameColumnEdgesCheckBox.isSelected = preferences.graphOptions.routeSameColumnEdgesOutside
        drawEdgesOnTopCheckBox.isSelected = preferences.graphOptions.drawEdgesOnTop
        drawArrowheadsOnTopCheckBox.isSelected = preferences.graphOptions.drawArrowheadsOnTop
        whiteBackgroundCheckBox.isSelected = preferences.showWhiteBackgroundForExport
        copyButtonFormatComboBox.selectedItem = preferences.copyButtonFormat
        toolbarToggleCheckBoxes.forEach { (toggleId, checkBox) ->
            checkBox.isSelected = toggleId in preferences.visibleToolbarToggles
        }

        rootFillButtons.lightButton.setColor(preferences.colorSettings.rootFillLightColor())
        rootFillButtons.darkButton.setColor(preferences.colorSettings.rootFillDarkColor())
        rootBorderButtons.lightButton.setColor(preferences.colorSettings.rootBorderLightColor())
        rootBorderButtons.darkButton.setColor(preferences.colorSettings.rootBorderDarkColor())
        reachableFillButtons.lightButton.setColor(preferences.colorSettings.reachableFillLightColor())
        reachableFillButtons.darkButton.setColor(preferences.colorSettings.reachableFillDarkColor())
        reachableBorderButtons.lightButton.setColor(preferences.colorSettings.reachableBorderLightColor())
        reachableBorderButtons.darkButton.setColor(preferences.colorSettings.reachableBorderDarkColor())
        supplementalFillButtons.lightButton.setColor(preferences.colorSettings.supplementalFillLightColor())
        supplementalFillButtons.darkButton.setColor(preferences.colorSettings.supplementalFillDarkColor())
        supplementalBorderButtons.lightButton.setColor(preferences.colorSettings.supplementalBorderLightColor())
        supplementalBorderButtons.darkButton.setColor(preferences.colorSettings.supplementalBorderDarkColor())
        privateFillButtons.lightButton.setColor(preferences.colorSettings.privateFillLightColor())
        privateFillButtons.darkButton.setColor(preferences.colorSettings.privateFillDarkColor())
        privateBorderButtons.lightButton.setColor(preferences.colorSettings.privateBorderLightColor())
        privateBorderButtons.darkButton.setColor(preferences.colorSettings.privateBorderDarkColor())
        targetFocusButtons.lightButton.setColor(preferences.colorSettings.targetFocusLightColor())
        targetFocusButtons.darkButton.setColor(preferences.colorSettings.targetFocusDarkColor())
        callerFocusButtons.lightButton.setColor(preferences.colorSettings.callerFocusLightColor())
        callerFocusButtons.darkButton.setColor(preferences.colorSettings.callerFocusDarkColor())
        calleeFocusButtons.lightButton.setColor(preferences.colorSettings.calleeFocusLightColor())
        calleeFocusButtons.darkButton.setColor(preferences.colorSettings.calleeFocusDarkColor())
        mixedFocusButtons.lightButton.setColor(preferences.colorSettings.mixedFocusLightColor())
        mixedFocusButtons.darkButton.setColor(preferences.colorSettings.mixedFocusDarkColor())
        selectionHighlightButtons.lightButton.setColor(preferences.colorSettings.selectionHighlightLightColor())
        selectionHighlightButtons.darkButton.setColor(preferences.colorSettings.selectionHighlightDarkColor())
        tableFillButtons.lightButton.setColor(preferences.colorSettings.tableFillLightColor())
        tableFillButtons.darkButton.setColor(preferences.colorSettings.tableFillDarkColor())
        tableBorderButtons.lightButton.setColor(preferences.colorSettings.tableBorderLightColor())
        tableBorderButtons.darkButton.setColor(preferences.colorSettings.tableBorderDarkColor())
        columnFillButtons.lightButton.setColor(preferences.colorSettings.columnFillLightColor())
        columnFillButtons.darkButton.setColor(preferences.colorSettings.columnFillDarkColor())
        columnBorderButtons.lightButton.setColor(preferences.colorSettings.columnBorderLightColor())
        columnBorderButtons.darkButton.setColor(preferences.colorSettings.columnBorderDarkColor())
        columnActionFillButtons.lightButton.setColor(preferences.colorSettings.columnActionFillLightColor())
        columnActionFillButtons.darkButton.setColor(preferences.colorSettings.columnActionFillDarkColor())
        columnActionBorderButtons.lightButton.setColor(preferences.colorSettings.columnActionBorderLightColor())
        columnActionBorderButtons.darkButton.setColor(preferences.colorSettings.columnActionBorderDarkColor())
        updateDependentState()
    }

    private fun buildSwitchesTab(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            add(showUnreachedCheckBox)
            add(Box.createVerticalStrut(8))
            add(showUnreachedCallsCheckBox)
            add(Box.createVerticalStrut(8))
            add(showAccessorCheckBox)
            add(Box.createVerticalStrut(8))
            add(showVisibilityColorsCheckBox)
            add(Box.createVerticalStrut(8))
            add(showPrivateMethodsCheckBox)
            add(Box.createVerticalStrut(8))
            add(enableClickHighlightCheckBox)
            add(Box.createVerticalStrut(8))
            add(showDetailedEdgesCheckBox)
            add(Box.createVerticalStrut(8))
            add(showMapperTablesCheckBox)
            add(Box.createVerticalStrut(8))
            add(routeSameColumnEdgesCheckBox)
            add(Box.createVerticalStrut(8))
            add(drawEdgesOnTopCheckBox)
            add(Box.createVerticalStrut(8))
            add(drawArrowheadsOnTopCheckBox)
            add(Box.createVerticalStrut(8))
            add(whiteBackgroundCheckBox)
            add(Box.createVerticalStrut(12))
            add(buildLabeledField("settings.copyButtonFormat", copyButtonFormatComboBox))
            add(Box.createVerticalStrut(16))
            add(JBLabel(MyBundle.message("settings.switchesHint")).apply {
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            })
        }

    private fun buildToolbarTab(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            add(JBLabel(MyBundle.message("settings.toolbarHint")).apply {
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(12))
            TOOLBAR_TOGGLE_DEFINITIONS.forEachIndexed { index, definition ->
                add(toolbarToggleCheckBoxes.getValue(definition.id).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                if (index != TOOLBAR_TOGGLE_DEFINITIONS.lastIndex) {
                    add(Box.createVerticalStrut(8))
                }
            }
        }

    private fun buildColorsTab(): JComponent {
        val themeTabs = JBTabbedPane().apply {
            addTab(MyBundle.message("settings.theme.light"), createScrollableTab(buildThemeColorsPanel(isDarkMode = false)))
            addTab(MyBundle.message("settings.theme.dark"), createScrollableTab(buildThemeColorsPanel(isDarkMode = true)))
        }

        return JPanel(BorderLayout(0, 10)).apply {
            border = JBUI.Borders.empty(12)
            add(
                JBLabel(MyBundle.message("settings.colorsHint")).apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.NORTH,
            )
            add(themeTabs, BorderLayout.CENTER)
        }
    }

    private fun createScrollableTab(content: JComponent): JComponent =
        JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }

    private fun buildLabeledField(labelKey: String, field: JComponent): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel(MyBundle.message(labelKey)).apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(4))
            add(field.apply { alignmentX = Component.LEFT_ALIGNMENT })
        }

    private fun buildThemeColorsPanel(isDarkMode: Boolean): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(0)
        }
        val baseConstraints = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 10, 12)
            weightx = 1.0
        }
        panel.add(
            JBLabel(MyBundle.message("settings.colors.baseHint")).apply {
                foreground = JBColor.GRAY
            },
            constraints(baseConstraints, gridx = 0, gridy = 0, gridwidth = 2),
        )
        addColorRow(panel, baseConstraints, 1, "settings.color.rootFill", "settings.color.rootFill.desc", rootFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 2, "settings.color.rootBorder", "settings.color.rootBorder.desc", rootBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 3, "settings.color.reachableFill", "settings.color.reachableFill.desc", reachableFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 4, "settings.color.reachableBorder", "settings.color.reachableBorder.desc", reachableBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 5, "settings.color.supplementalFill", "settings.color.supplementalFill.desc", supplementalFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 6, "settings.color.supplementalBorder", "settings.color.supplementalBorder.desc", supplementalBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 7, "settings.color.privateFill", "settings.color.privateFill.desc", privateFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 8, "settings.color.privateBorder", "settings.color.privateBorder.desc", privateBorderButtons, isDarkMode)
        panel.add(
            JBLabel(MyBundle.message("settings.colors.focusHint")).apply {
                foreground = JBColor.GRAY
            },
            constraints(baseConstraints, gridx = 0, gridy = 9, gridwidth = 2),
        )
        addColorRow(panel, baseConstraints, 10, "settings.color.targetFocus", "settings.color.targetFocus.desc", targetFocusButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 11, "settings.color.callerFocus", "settings.color.callerFocus.desc", callerFocusButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 12, "settings.color.calleeFocus", "settings.color.calleeFocus.desc", calleeFocusButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 13, "settings.color.mixedFocus", "settings.color.mixedFocus.desc", mixedFocusButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 14, "settings.color.selectionHighlight", "settings.color.selectionHighlight.desc", selectionHighlightButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 15, "settings.color.tableFill", "settings.color.tableFill.desc", tableFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 16, "settings.color.tableBorder", "settings.color.tableBorder.desc", tableBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 17, "settings.color.columnFill", "settings.color.columnFill.desc", columnFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 18, "settings.color.columnBorder", "settings.color.columnBorder.desc", columnBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 19, "settings.color.columnActionFill", "settings.color.columnActionFill.desc", columnActionFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 20, "settings.color.columnActionBorder", "settings.color.columnActionBorder.desc", columnActionBorderButtons, isDarkMode)
        return panel
    }

    private fun addColorRow(
        panel: JPanel,
        baseConstraints: GridBagConstraints,
        row: Int,
        labelKey: String,
        descriptionKey: String,
        buttons: ThemeColorButtons,
        isDarkMode: Boolean,
    ) {
        panel.add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JBLabel(MyBundle.message(labelKey)).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                add(Box.createVerticalStrut(2))
                add(JBLabel(MyBundle.message(descriptionKey)).apply {
                    foreground = JBColor.GRAY
                    alignmentX = Component.LEFT_ALIGNMENT
                })
            },
            constraints(baseConstraints, gridx = 0, gridy = row, weightx = 0.0),
        )
        panel.add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(if (isDarkMode) buttons.darkButton else buttons.lightButton)
            },
            constraints(baseConstraints, gridx = 1, gridy = row, weightx = 1.0),
        )
    }

    private fun updateDependentState() {
        showUnreachedCallsCheckBox.isEnabled = showUnreachedCheckBox.isSelected
    }

    private fun constraints(
        base: GridBagConstraints,
        gridx: Int,
        gridy: Int,
        gridwidth: Int = 1,
        weightx: Double = base.weightx,
    ): GridBagConstraints = GridBagConstraints().also {
        it.gridx = gridx
        it.gridy = gridy
        it.gridwidth = gridwidth
        it.weightx = weightx
        it.anchor = base.anchor
        it.fill = base.fill
        it.insets = base.insets
    }

    private class ThemeColorButtons(lightColor: Color, darkColor: Color) {
        val lightButton = ColorButton(lightColor)
        val darkButton = ColorButton(darkColor)

        fun lightHex(): String = lightButton.colorHex()

        fun darkHex(): String = darkButton.colorHex()
    }

    private class ColorButton(initialColor: Color) : JButton() {
        private var selectedColor: Color = initialColor

        init {
            preferredSize = Dimension(120, 30)
            addActionListener {
                JColorChooser.showDialog(this, MyBundle.message("settings.pickColor"), selectedColor)?.let {
                    selectedColor = it
                    refresh()
                }
            }
            refresh()
        }

        fun colorHex(): String = "#%02X%02X%02X".format(selectedColor.red, selectedColor.green, selectedColor.blue)

        fun setColor(color: Color) {
            selectedColor = color
            refresh()
        }

        private fun refresh() {
            text = colorHex()
            background = selectedColor
            foreground = if ((selectedColor.red * 0.299) + (selectedColor.green * 0.587) + (selectedColor.blue * 0.114) > 186) {
                Color.BLACK
            } else {
                Color.WHITE
            }
            isOpaque = true
            border = JBUI.Borders.customLine(JBColor.border())
        }
    }
}
