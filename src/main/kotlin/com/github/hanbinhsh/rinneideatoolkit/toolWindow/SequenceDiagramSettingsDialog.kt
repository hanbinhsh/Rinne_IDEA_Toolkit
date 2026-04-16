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
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceColorSettings
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceToolbarToggleId
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceUiPreferences
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
import javax.swing.JComponent
import javax.swing.JPanel

class SequenceDiagramSettingsDialog(
    project: Project,
    preferences: SequenceUiPreferences,
) : DialogWrapper(project) {
    private val resetSettingsButton = JButton(MyBundle.message("toolWindow.resetSettings"))

    private val showAccessorCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showAccessorMethods"),
        preferences.sequenceOptions.showAccessorMethods,
    )
    private val showPrivateMethodsCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showPrivateMethods"),
        preferences.sequenceOptions.showPrivateMethods,
    )
    private val showMapperTablesCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.showMapperTables"),
        preferences.sequenceOptions.showMapperTables,
    )
    private val whiteBackgroundCheckBox = JBCheckBox(
        MyBundle.message("toolWindow.whiteBackground"),
        preferences.showWhiteBackgroundForExport,
    )
    private val showReturnMessagesCheckBox = JBCheckBox(
        MyBundle.message("sequence.showReturnMessages"),
        preferences.showReturnMessages,
    )
    private val showActivationBarsCheckBox = JBCheckBox(
        MyBundle.message("sequence.showActivationBars"),
        preferences.showActivationBars,
    )
    private val showCreateMessagesCheckBox = JBCheckBox(
        MyBundle.message("sequence.showCreateMessages"),
        preferences.showCreateMessages,
    )
    private val scenarioFillButtons = ThemeColorButtons(
        preferences.colorSettings.scenarioFillLightColor(),
        preferences.colorSettings.scenarioFillDarkColor(),
    )
    private val scenarioBorderButtons = ThemeColorButtons(
        preferences.colorSettings.scenarioBorderLightColor(),
        preferences.colorSettings.scenarioBorderDarkColor(),
    )
    private val participantFillButtons = ThemeColorButtons(
        preferences.colorSettings.participantFillLightColor(),
        preferences.colorSettings.participantFillDarkColor(),
    )
    private val participantBorderButtons = ThemeColorButtons(
        preferences.colorSettings.participantBorderLightColor(),
        preferences.colorSettings.participantBorderDarkColor(),
    )
    private val participantTextButtons = ThemeColorButtons(
        preferences.colorSettings.participantTextLightColor(),
        preferences.colorSettings.participantTextDarkColor(),
    )
    private val databaseParticipantFillButtons = ThemeColorButtons(
        preferences.colorSettings.databaseParticipantFillLightColor(),
        preferences.colorSettings.databaseParticipantFillDarkColor(),
    )
    private val databaseParticipantBorderButtons = ThemeColorButtons(
        preferences.colorSettings.databaseParticipantBorderLightColor(),
        preferences.colorSettings.databaseParticipantBorderDarkColor(),
    )
    private val databaseParticipantTextButtons = ThemeColorButtons(
        preferences.colorSettings.databaseParticipantTextLightColor(),
        preferences.colorSettings.databaseParticipantTextDarkColor(),
    )
    private val lifelineButtons = ThemeColorButtons(
        preferences.colorSettings.lifelineLightColor(),
        preferences.colorSettings.lifelineDarkColor(),
    )
    private val databaseLifelineButtons = ThemeColorButtons(
        preferences.colorSettings.databaseLifelineLightColor(),
        preferences.colorSettings.databaseLifelineDarkColor(),
    )
    private val callButtons = ThemeColorButtons(
        preferences.colorSettings.callLightColor(),
        preferences.colorSettings.callDarkColor(),
    )
    private val databaseCallButtons = ThemeColorButtons(
        preferences.colorSettings.databaseCallLightColor(),
        preferences.colorSettings.databaseCallDarkColor(),
    )
    private val returnButtons = ThemeColorButtons(
        preferences.colorSettings.returnLightColor(),
        preferences.colorSettings.returnDarkColor(),
    )
    private val createButtons = ThemeColorButtons(
        preferences.colorSettings.createLightColor(),
        preferences.colorSettings.createDarkColor(),
    )
    private val activationFillButtons = ThemeColorButtons(
        preferences.colorSettings.activationFillLightColor(),
        preferences.colorSettings.activationFillDarkColor(),
    )
    private val activationBorderButtons = ThemeColorButtons(
        preferences.colorSettings.activationBorderLightColor(),
        preferences.colorSettings.activationBorderDarkColor(),
    )
    private val methodHighlightButtons = ThemeColorButtons(
        preferences.colorSettings.methodHighlightLightColor(),
        preferences.colorSettings.methodHighlightDarkColor(),
    )

    private val toolbarToggleCheckBoxes = linkedMapOf<SequenceToolbarToggleId, JBCheckBox>().apply {
        SEQUENCE_TOOLBAR_TOGGLE_DEFINITIONS.forEach { definition ->
            put(
                definition.id,
                JBCheckBox(
                    MyBundle.message(definition.messageKey),
                    preferences.visibleToolbarToggles.contains(definition.id),
                ),
            )
        }
    }

    init {
        title = MyBundle.message("sequence.settings.title")
        init()
        resetSettingsButton.addActionListener {
            applyPreferences(SequenceUiPreferences())
        }
    }

    fun selectedPreferences(): SequenceUiPreferences = SequenceUiPreferences(
        sequenceOptions = GraphOptions(
            showAccessorMethods = showAccessorCheckBox.isSelected,
            showPrivateMethods = showPrivateMethodsCheckBox.isSelected,
            showMapperTables = showMapperTablesCheckBox.isSelected,
        ),
        visibleToolbarToggles = toolbarToggleCheckBoxes
            .filterValues { it.isSelected }
            .keys,
        showWhiteBackgroundForExport = whiteBackgroundCheckBox.isSelected,
        showWhiteBackgroundForCopy = whiteBackgroundCheckBox.isSelected,
        showReturnMessages = showReturnMessagesCheckBox.isSelected,
        showActivationBars = showActivationBarsCheckBox.isSelected,
        showCreateMessages = showCreateMessagesCheckBox.isSelected,
        colorSettings = SequenceColorSettings(
            scenarioFillHex = scenarioFillButtons.lightHex(),
            scenarioFillDarkHex = scenarioFillButtons.darkHex(),
            scenarioBorderHex = scenarioBorderButtons.lightHex(),
            scenarioBorderDarkHex = scenarioBorderButtons.darkHex(),
            participantFillHex = participantFillButtons.lightHex(),
            participantFillDarkHex = participantFillButtons.darkHex(),
            participantBorderHex = participantBorderButtons.lightHex(),
            participantBorderDarkHex = participantBorderButtons.darkHex(),
            participantTextHex = participantTextButtons.lightHex(),
            participantTextDarkHex = participantTextButtons.darkHex(),
            databaseParticipantFillHex = databaseParticipantFillButtons.lightHex(),
            databaseParticipantFillDarkHex = databaseParticipantFillButtons.darkHex(),
            databaseParticipantBorderHex = databaseParticipantBorderButtons.lightHex(),
            databaseParticipantBorderDarkHex = databaseParticipantBorderButtons.darkHex(),
            databaseParticipantTextHex = databaseParticipantTextButtons.lightHex(),
            databaseParticipantTextDarkHex = databaseParticipantTextButtons.darkHex(),
            lifelineHex = lifelineButtons.lightHex(),
            lifelineDarkHex = lifelineButtons.darkHex(),
            databaseLifelineHex = databaseLifelineButtons.lightHex(),
            databaseLifelineDarkHex = databaseLifelineButtons.darkHex(),
            callHex = callButtons.lightHex(),
            callDarkHex = callButtons.darkHex(),
            databaseCallHex = databaseCallButtons.lightHex(),
            databaseCallDarkHex = databaseCallButtons.darkHex(),
            returnHex = returnButtons.lightHex(),
            returnDarkHex = returnButtons.darkHex(),
            createHex = createButtons.lightHex(),
            createDarkHex = createButtons.darkHex(),
            activationFillHex = activationFillButtons.lightHex(),
            activationFillDarkHex = activationFillButtons.darkHex(),
            activationBorderHex = activationBorderButtons.lightHex(),
            activationBorderDarkHex = activationBorderButtons.darkHex(),
            methodHighlightHex = methodHighlightButtons.lightHex(),
            methodHighlightDarkHex = methodHighlightButtons.darkHex(),
        ),
    )

    override fun createCenterPanel(): JComponent {
        val tabs = JBTabbedPane().apply {
            addTab(MyBundle.message("sequence.settings.tab.switches"), createScrollableTab(buildSwitchesTab()))
            addTab(MyBundle.message("sequence.settings.tab.toolbar"), createScrollableTab(buildToolbarTab()))
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

    private fun applyPreferences(preferences: SequenceUiPreferences) {
        showAccessorCheckBox.isSelected = preferences.sequenceOptions.showAccessorMethods
        showPrivateMethodsCheckBox.isSelected = preferences.sequenceOptions.showPrivateMethods
        showMapperTablesCheckBox.isSelected = preferences.sequenceOptions.showMapperTables
        whiteBackgroundCheckBox.isSelected = preferences.showWhiteBackgroundForExport
        showReturnMessagesCheckBox.isSelected = preferences.showReturnMessages
        showActivationBarsCheckBox.isSelected = preferences.showActivationBars
        showCreateMessagesCheckBox.isSelected = preferences.showCreateMessages
        toolbarToggleCheckBoxes.forEach { (toggleId, checkBox) ->
            checkBox.isSelected = toggleId in preferences.visibleToolbarToggles
        }

        scenarioFillButtons.lightButton.setColor(preferences.colorSettings.scenarioFillLightColor())
        scenarioFillButtons.darkButton.setColor(preferences.colorSettings.scenarioFillDarkColor())
        scenarioBorderButtons.lightButton.setColor(preferences.colorSettings.scenarioBorderLightColor())
        scenarioBorderButtons.darkButton.setColor(preferences.colorSettings.scenarioBorderDarkColor())
        participantFillButtons.lightButton.setColor(preferences.colorSettings.participantFillLightColor())
        participantFillButtons.darkButton.setColor(preferences.colorSettings.participantFillDarkColor())
        participantBorderButtons.lightButton.setColor(preferences.colorSettings.participantBorderLightColor())
        participantBorderButtons.darkButton.setColor(preferences.colorSettings.participantBorderDarkColor())
        participantTextButtons.lightButton.setColor(preferences.colorSettings.participantTextLightColor())
        participantTextButtons.darkButton.setColor(preferences.colorSettings.participantTextDarkColor())
        databaseParticipantFillButtons.lightButton.setColor(preferences.colorSettings.databaseParticipantFillLightColor())
        databaseParticipantFillButtons.darkButton.setColor(preferences.colorSettings.databaseParticipantFillDarkColor())
        databaseParticipantBorderButtons.lightButton.setColor(preferences.colorSettings.databaseParticipantBorderLightColor())
        databaseParticipantBorderButtons.darkButton.setColor(preferences.colorSettings.databaseParticipantBorderDarkColor())
        databaseParticipantTextButtons.lightButton.setColor(preferences.colorSettings.databaseParticipantTextLightColor())
        databaseParticipantTextButtons.darkButton.setColor(preferences.colorSettings.databaseParticipantTextDarkColor())
        lifelineButtons.lightButton.setColor(preferences.colorSettings.lifelineLightColor())
        lifelineButtons.darkButton.setColor(preferences.colorSettings.lifelineDarkColor())
        databaseLifelineButtons.lightButton.setColor(preferences.colorSettings.databaseLifelineLightColor())
        databaseLifelineButtons.darkButton.setColor(preferences.colorSettings.databaseLifelineDarkColor())
        callButtons.lightButton.setColor(preferences.colorSettings.callLightColor())
        callButtons.darkButton.setColor(preferences.colorSettings.callDarkColor())
        databaseCallButtons.lightButton.setColor(preferences.colorSettings.databaseCallLightColor())
        databaseCallButtons.darkButton.setColor(preferences.colorSettings.databaseCallDarkColor())
        returnButtons.lightButton.setColor(preferences.colorSettings.returnLightColor())
        returnButtons.darkButton.setColor(preferences.colorSettings.returnDarkColor())
        createButtons.lightButton.setColor(preferences.colorSettings.createLightColor())
        createButtons.darkButton.setColor(preferences.colorSettings.createDarkColor())
        activationFillButtons.lightButton.setColor(preferences.colorSettings.activationFillLightColor())
        activationFillButtons.darkButton.setColor(preferences.colorSettings.activationFillDarkColor())
        activationBorderButtons.lightButton.setColor(preferences.colorSettings.activationBorderLightColor())
        activationBorderButtons.darkButton.setColor(preferences.colorSettings.activationBorderDarkColor())
        methodHighlightButtons.lightButton.setColor(preferences.colorSettings.methodHighlightLightColor())
        methodHighlightButtons.darkButton.setColor(preferences.colorSettings.methodHighlightDarkColor())
    }

    private fun buildSwitchesTab(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            add(showAccessorCheckBox)
            add(Box.createVerticalStrut(8))
            add(showPrivateMethodsCheckBox)
            add(Box.createVerticalStrut(8))
            add(showMapperTablesCheckBox)
            add(Box.createVerticalStrut(8))
            add(showReturnMessagesCheckBox)
            add(Box.createVerticalStrut(8))
            add(showActivationBarsCheckBox)
            add(Box.createVerticalStrut(8))
            add(showCreateMessagesCheckBox)
            add(Box.createVerticalStrut(8))
            add(whiteBackgroundCheckBox)
            add(Box.createVerticalStrut(16))
            add(JBLabel(MyBundle.message("sequence.settings.switchesHint")).apply {
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            })
        }

    private fun buildToolbarTab(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(12)
            add(JBLabel(MyBundle.message("sequence.settings.toolbarHint")).apply {
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(12))
            SEQUENCE_TOOLBAR_TOGGLE_DEFINITIONS.forEachIndexed { index, definition ->
                add(toolbarToggleCheckBoxes.getValue(definition.id).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                if (index != SEQUENCE_TOOLBAR_TOGGLE_DEFINITIONS.lastIndex) {
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
                JBLabel(MyBundle.message("sequence.settings.colorsHint")).apply {
                    foreground = JBColor.GRAY
                },
                BorderLayout.NORTH,
            )
            add(themeTabs, BorderLayout.CENTER)
        }
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
            JBLabel(MyBundle.message("sequence.settings.colors.baseHint")).apply {
                foreground = JBColor.GRAY
            },
            constraints(baseConstraints, 0, 0, 2),
        )
        addColorRow(panel, baseConstraints, 1, "sequence.settings.color.scenarioFill", "sequence.settings.color.scenarioFill.desc", scenarioFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 2, "sequence.settings.color.scenarioBorder", "sequence.settings.color.scenarioBorder.desc", scenarioBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 3, "sequence.settings.color.participantFill", "sequence.settings.color.participantFill.desc", participantFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 4, "sequence.settings.color.participantBorder", "sequence.settings.color.participantBorder.desc", participantBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 5, "sequence.settings.color.participantText", "sequence.settings.color.participantText.desc", participantTextButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 6, "sequence.settings.color.databaseParticipantFill", "sequence.settings.color.databaseParticipantFill.desc", databaseParticipantFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 7, "sequence.settings.color.databaseParticipantBorder", "sequence.settings.color.databaseParticipantBorder.desc", databaseParticipantBorderButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 8, "sequence.settings.color.databaseParticipantText", "sequence.settings.color.databaseParticipantText.desc", databaseParticipantTextButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 9, "sequence.settings.color.lifeline", "sequence.settings.color.lifeline.desc", lifelineButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 10, "sequence.settings.color.databaseLifeline", "sequence.settings.color.databaseLifeline.desc", databaseLifelineButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 11, "sequence.settings.color.call", "sequence.settings.color.call.desc", callButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 12, "sequence.settings.color.databaseCall", "sequence.settings.color.databaseCall.desc", databaseCallButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 13, "sequence.settings.color.return", "sequence.settings.color.return.desc", returnButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 14, "sequence.settings.color.create", "sequence.settings.color.create.desc", createButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 15, "sequence.settings.color.activationFill", "sequence.settings.color.activationFill.desc", activationFillButtons, isDarkMode)
        addColorRow(panel, baseConstraints, 16, "sequence.settings.color.activationBorder", "sequence.settings.color.activationBorder.desc", activationBorderButtons, isDarkMode)
        panel.add(
            JBLabel(MyBundle.message("sequence.settings.colors.highlightHint")).apply {
                foreground = JBColor.GRAY
            },
            constraints(baseConstraints, 0, 17, 2),
        )
        addColorRow(panel, baseConstraints, 18, "sequence.settings.color.methodHighlight", "sequence.settings.color.methodHighlight.desc", methodHighlightButtons, isDarkMode)
        return panel
    }

    private fun createScrollableTab(content: JComponent): JComponent =
        JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
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
            constraints(baseConstraints, 0, row, weightx = 0.0),
        )
        panel.add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(if (isDarkMode) buttons.darkButton else buttons.lightButton)
            },
            constraints(baseConstraints, 1, row, weightx = 1.0),
        )
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
