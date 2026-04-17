package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.github.hanbinhsh.rinneideatoolkit.MyBundle
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceColorSettings
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceDiagram
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceMessage
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceMessageKind
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceParticipant
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceScenario
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiMethod
import com.intellij.pom.Navigatable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.event.MouseInputAdapter
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class SequenceDiagramPanel : JPanel(), Scrollable {

    private var diagram: SequenceDiagram? = null
    private var scenarioLayouts: List<ScenarioLayout> = emptyList()
    private var basePreferredSize = Dimension(720, 480)
    private var zoomFactor = 1.0
    private var highlightedMethod: MessageHighlightKey? = null
    private var targetMethodHighlightKey: MessageHighlightKey? = null
    var useWhiteBackgroundForCopyExport = false
    var colorSettings: SequenceColorSettings = SequenceColorSettings()
        set(value) {
            field = value
            repaint()
        }
    var showReturnMessages = false
        set(value) {
            field = value
            rebuildAndRepaint()
        }
    var showActivationBars = false
        set(value) {
            field = value
            rebuildAndRepaint()
        }
    var showCreateMessages = false
        set(value) {
            field = value
            rebuildAndRepaint()
        }
    var onZoomChanged: ((String) -> Unit)? = null
    var onSequenceAnalysisRequested: ((PsiMethod) -> Unit)? = null

    init {
        border = JBUI.Borders.empty(12)
        background = JBColor(Color(250, 251, 253), Color(43, 45, 48))
        font = font.deriveFont(Font.PLAIN, font.size2D + 1)
        ToolTipManager.sharedInstance().registerComponent(this)

        val mouseHandler = object : MouseInputAdapter() {
            private var dragStartScreenPoint: Point? = null
            private var dragStartViewPosition: Point? = null
            private var didPan = false
            private var isDragging = false

            override fun mouseMoved(e: MouseEvent) {
                val translatedPoint = toDiagramPoint(e.point)
                cursor = when {
                    isDragging -> Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    findMessageAt(translatedPoint) != null -> Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    else -> Cursor.getDefaultCursor()
                }
            }

            override fun mousePressed(e: MouseEvent) {
                maybeShowScenarioContextMenu(e)
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return
                }
                dragStartScreenPoint = e.locationOnScreen
                dragStartViewPosition = viewport()?.viewPosition
                didPan = false
                isDragging = false
            }

            override fun mouseDragged(e: MouseEvent) {
                if (e.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK == 0) {
                    return
                }
                val startScreen = dragStartScreenPoint ?: return
                val startView = dragStartViewPosition ?: return
                val viewport = viewport() ?: return

                val dx = e.locationOnScreen.x - startScreen.x
                val dy = e.locationOnScreen.y - startScreen.y
                if (!didPan && (abs(dx) > DRAG_THRESHOLD || abs(dy) > DRAG_THRESHOLD)) {
                    didPan = true
                    isDragging = true
                }
                if (!didPan) {
                    return
                }

                val maxX = maxOf(preferredSize.width - viewport.width, 0)
                val maxY = maxOf(preferredSize.height - viewport.height, 0)
                viewport.viewPosition = Point(
                    (startView.x - dx).coerceIn(0, maxX),
                    (startView.y - dy).coerceIn(0, maxY),
                )
                cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            }

            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return
                }
                if (didPan) {
                    didPan = false
                    return
                }
                val translatedPoint = toDiagramPoint(e.point)
                val messageLayout = findMessageAt(translatedPoint) ?: return
                val target = ReadAction.compute<com.intellij.psi.PsiElement?, RuntimeException> {
                    messageLayout.message.pointer.element
                } ?: return
                ApplicationManager.getApplication().invokeLater {
                    (target as? Navigatable)?.navigate(true)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                maybeShowScenarioContextMenu(e)
                dragStartScreenPoint = null
                dragStartViewPosition = null
                isDragging = false
                cursor = Cursor.getDefaultCursor()
            }

            override fun mouseWheelMoved(e: java.awt.event.MouseWheelEvent) {
                if (e.isControlDown) {
                    if (e.wheelRotation < 0) {
                        zoomIn()
                    } else {
                        zoomOut()
                    }
                    e.consume()
                    return
                }

                if (scrollViewportByWheel(e)) {
                    e.consume()
                }
            }
        }

        addMouseMotionListener(mouseHandler)
        addMouseListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
        notifyZoomChanged()
    }

    fun renderDiagram(diagram: SequenceDiagram?) {
        this.diagram = diagram
        targetMethodHighlightKey = diagram?.let {
            MessageHighlightKey(
                toClassQualifiedName = it.targetClassQualifiedName,
                methodDisplaySignature = it.targetMethodSignature,
                kind = SequenceMessageKind.CALL,
            )
        }
        highlightedMethod = targetMethodHighlightKey
        rebuildAndRepaint()
    }

    fun zoomIn() = setZoom(zoomFactor + ZOOM_STEP)

    fun zoomOut() = setZoom(zoomFactor - ZOOM_STEP)

    fun resetZoom() = setZoom(1.0)

    fun setZoomPercent(percent: Int) {
        val minPercent = (MIN_ZOOM * 100).roundToInt()
        val maxPercent = (MAX_ZOOM * 100).roundToInt()
        setZoom(percent.coerceIn(minPercent, maxPercent) / 100.0)
    }

    fun toggleTargetMethodHighlight() {
        val targetKey = targetMethodHighlightKey ?: return
        highlightedMethod = if (highlightedMethod == targetKey) null else targetKey
        repaint()
    }

    fun exportToPng(file: File) {
        ImageIO.write(renderToImage(useWhiteBackground = useWhiteBackgroundForCopyExport), "png", file)
    }

    fun copyImageToClipboard() {
        CopyPasteManager.getInstance().setContents(ImageTransferable(renderToImage(useWhiteBackground = useWhiteBackgroundForCopyExport)))
    }

    fun exportToSvg(file: File) {
        file.writeText(renderToSvg(), Charsets.UTF_8)
    }

    fun copySvgToClipboard() {
        copyTextToClipboard(renderToSvg())
    }

    fun exportToMermaid(file: File) {
        file.writeText(renderToMermaid(), Charsets.UTF_8)
    }

    fun copyMermaidToClipboard() {
        copyTextToClipboard(renderToMermaid())
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val translatedPoint = toDiagramPoint(event.point)
        return findMessageAt(translatedPoint)?.let { layout ->
            MyBundle.message(
                "sequence.tooltip.message",
                layout.message.fromClassName,
                layout.message.toClassName,
                layout.message.methodDisplaySignature.ifBlank {
                    when (layout.message.kind) {
                        SequenceMessageKind.RETURN -> MyBundle.message("sequence.returnMessageLabel")
                        SequenceMessageKind.CREATE -> MyBundle.message("sequence.createMessageLabel")
                        SequenceMessageKind.CALL -> ""
                    }
                },
            )
        }
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        paintDiagramContents(g)
    }

    private fun paintDiagramContents(graphics: Graphics2D) {
        val g = graphics.create() as Graphics2D
        g.scale(zoomFactor, zoomFactor)
        if (diagram == null || scenarioLayouts.isEmpty()) {
            paintEmptyState(g)
            g.dispose()
            return
        }

        scenarioLayouts.forEach { scenario ->
            paintScenario(g, scenario)
        }
        g.dispose()
    }

    private fun paintScenario(g: Graphics2D, layout: ScenarioLayout) {
        val graphics = g.create() as Graphics2D
        graphics.color = themeAwareColor(
            colorSettings.scenarioFillColor(),
            fallbackDark = Color(52, 56, 61),
            darkenFactor = 0.28f,
        )
        graphics.fillRoundRect(layout.bounds.x, layout.bounds.y, layout.bounds.width, layout.bounds.height, 18, 18)
        graphics.color = themeAwareColor(
            colorSettings.scenarioBorderColor(),
            fallbackDark = Color(74, 79, 86),
            darkenFactor = 0.45f,
        )
        graphics.drawRoundRect(layout.bounds.x, layout.bounds.y, layout.bounds.width, layout.bounds.height, 18, 18)

        graphics.font = font.deriveFont(Font.BOLD, font.size2D + 0.5f)
        graphics.color = themeAwareTextColor(colorSettings.participantTextColor())
        graphics.drawString(layout.title, layout.bounds.x + INNER_PADDING, layout.titleBaselineY)

        layout.participants.forEach { participant ->
            paintParticipant(graphics, participant, layout)
        }

        if (showActivationBars) {
            layout.activations.forEach { activation ->
                graphics.color = themeAwareColor(
                    colorSettings.activationFillColor(),
                    fallbackDark = Color(214, 183, 117),
                    darkenFactor = 0.62f,
                )
                graphics.fillRoundRect(
                    activation.bounds.x,
                    activation.bounds.y,
                    activation.bounds.width,
                    activation.bounds.height,
                    6,
                    6,
                )
                graphics.color = themeAwareColor(
                    colorSettings.activationBorderColor(),
                    fallbackDark = Color(244, 214, 153),
                    darkenFactor = 0.72f,
                )
                graphics.drawRoundRect(
                    activation.bounds.x,
                    activation.bounds.y,
                    activation.bounds.width,
                    activation.bounds.height,
                    6,
                    6,
                )
            }
        }

        graphics.font = font
        layout.messages.forEach { message ->
            paintMessage(graphics, message)
        }

        graphics.dispose()
    }

    private fun paintParticipant(graphics: Graphics2D, participant: ParticipantLayout, layout: ScenarioLayout) {
        val isDatabaseParticipant = participant.participant.classQualifiedName.startsWith(DATABASE_PARTICIPANT_PREFIX)
        val fillColor = if (isDatabaseParticipant) {
            colorSettings.databaseParticipantFillColor()
        } else {
            themeAwareColor(
                colorSettings.participantFillColor(),
                fallbackDark = Color(67, 80, 98),
                darkenFactor = 0.38f,
            )
        }
        val borderColor = if (isDatabaseParticipant) {
            colorSettings.databaseParticipantBorderColor()
        } else {
            themeAwareColor(
                colorSettings.participantBorderColor(),
                fallbackDark = Color(145, 172, 214),
                darkenFactor = 0.58f,
            )
        }
        val textColor = if (isDatabaseParticipant) {
            colorSettings.databaseParticipantTextColor()
        } else {
            themeAwareTextColor(colorSettings.participantTextColor())
        }

        graphics.color = fillColor
        graphics.fillRoundRect(
            participant.boxBounds.x,
            participant.boxBounds.y,
            participant.boxBounds.width,
            participant.boxBounds.height,
            12,
            12,
        )
        graphics.color = borderColor
        graphics.drawRoundRect(
            participant.boxBounds.x,
            participant.boxBounds.y,
            participant.boxBounds.width,
            participant.boxBounds.height,
            12,
            12,
        )
        graphics.font = font.deriveFont(Font.BOLD, font.size2D)
        graphics.color = textColor
        graphics.drawString(
            fitText(graphics, participant.participant.className, participant.boxBounds.width - 16),
            participant.boxBounds.x + 8,
            participant.boxBounds.y + 19,
        )

        graphics.stroke = LIFELINE_STROKE
        graphics.color = if (isDatabaseParticipant) {
            colorSettings.databaseLifelineColor()
        } else {
            themeAwareColor(
                colorSettings.lifelineColor(),
                fallbackDark = Color(112, 124, 142),
                darkenFactor = 0.55f,
            )
        }
        graphics.drawLine(
            participant.centerX,
            layout.lifelineStartY,
            participant.centerX,
            layout.lifelineEndY,
        )
    }

    private fun paintMessage(g: Graphics2D, layout: MessageLayout) {
        val graphics = g.create() as Graphics2D
        val isHighlighted = highlightedMethod == layout.highlightKey && layout.highlightKey != null
        val isDatabaseCall = layout.message.kind == SequenceMessageKind.CALL &&
            (isDatabaseParticipant(layout.message.fromClassQualifiedName) || isDatabaseParticipant(layout.message.toClassQualifiedName))
        val baseColor = when (layout.message.kind) {
            SequenceMessageKind.RETURN -> colorSettings.returnColor()
            SequenceMessageKind.CREATE -> colorSettings.createColor()
            SequenceMessageKind.CALL -> if (isDatabaseCall) colorSettings.databaseCallColor() else colorSettings.callColor()
        }
        val accentColor = colorSettings.methodHighlightColor()
        val messageColor = themeAwareMessageColor(
            lightColor = if (isHighlighted) accentColor else baseColor,
            highlighted = isHighlighted,
        )
        if (layout.labelBounds.width > 0) {
            graphics.color = messageColor
            graphics.drawString(
                fitText(graphics, layout.message.methodDisplaySignature, layout.labelBounds.width),
                layout.labelBounds.x,
                layout.labelBaselineY,
            )
        }

        graphics.color = messageColor
        graphics.stroke = when {
            layout.message.kind == SequenceMessageKind.RETURN && isHighlighted -> HIGHLIGHT_DASHED_MESSAGE_STROKE
            layout.message.kind == SequenceMessageKind.RETURN -> DASHED_MESSAGE_STROKE
            isHighlighted -> HIGHLIGHT_MESSAGE_STROKE
            else -> MESSAGE_STROKE
        }
        val lineStartX = resolveMessageLineStartX(layout)
        val lineEndX = resolveMessageLineEndX(layout)

        when {
            layout.message.isSelfCall && layout.message.kind == SequenceMessageKind.RETURN -> {
                val anchorX = resolveSelfCallAnchorX(layout)
                graphics.drawLine(anchorX + SELF_RETURN_WIDTH, layout.y, anchorX, layout.y)
                drawArrow(graphics, anchorX.toFloat(), layout.y.toFloat(), ArrowDirection.LEFT)
            }

            layout.message.isSelfCall -> {
                val anchorX = resolveSelfCallAnchorX(layout)
                val path = Path2D.Float().apply {
                    moveTo(anchorX.toDouble(), layout.y.toDouble())
                    lineTo(anchorX + layout.selfCallWidth.toDouble(), layout.y.toDouble())
                    lineTo(anchorX + layout.selfCallWidth.toDouble(), layout.y + layout.selfCallHeight.toDouble())
                    lineTo(anchorX.toDouble(), layout.y + layout.selfCallHeight.toDouble())
                }
                graphics.draw(path)
                drawArrow(graphics, anchorX.toFloat(), (layout.y + layout.selfCallHeight).toFloat(), ArrowDirection.LEFT)
            }

            else -> {
                graphics.drawLine(lineStartX, layout.y, lineEndX, layout.y)
                drawArrow(graphics, lineEndX.toFloat(), layout.y.toFloat(), layout.direction)
            }
        }
        graphics.dispose()
    }

    private fun paintEmptyState(g: Graphics2D) {
        g.color = JBColor.GRAY
        g.font = font.deriveFont(Font.PLAIN, font.size2D + 1)
        g.drawString(MyBundle.message("sequence.emptyState"), PADDING, 40)
    }

    private fun rebuildAndRepaint() {
        rebuildLayout()
        revalidate()
        repaint()
    }

    private fun rebuildLayout() {
        val currentDiagram = diagram
        if (currentDiagram == null || currentDiagram.scenarios.isEmpty()) {
            scenarioLayouts = emptyList()
            basePreferredSize = Dimension(720, 480)
            updateScaledPreferredSize()
            return
        }

        val layouts = mutableListOf<ScenarioLayout>()
        var currentY = PADDING
        var maxWidth = 720

        currentDiagram.scenarios.forEach { scenario ->
            val visibleMessages = scenario.messages.filter(::isMessageVisible)
            val visibleParticipantIds = visibleMessages
                .flatMap { listOf(it.fromClassQualifiedName, it.toClassQualifiedName) }
                .toSet()
            val visibleParticipants = scenario.participants
                .filter { visibleParticipantIds.contains(it.classQualifiedName) }
                .ifEmpty { scenario.participants.take(1) }

            val scenarioWidth = max(
                MIN_SCENARIO_WIDTH,
                PADDING * 2 + max(1, visibleParticipants.size) * PARTICIPANT_COLUMN_WIDTH,
            )
            val scenarioX = PADDING
            val participantLayouts = visibleParticipants.mapIndexed { index, participant ->
                val centerX = scenarioX + INNER_PADDING + index * PARTICIPANT_COLUMN_WIDTH + PARTICIPANT_BOX_WIDTH / 2
                ParticipantLayout(
                    participant = participant,
                    centerX = centerX,
                    boxBounds = Rectangle(
                        centerX - PARTICIPANT_BOX_WIDTH / 2,
                        currentY + SCENARIO_HEADER_HEIGHT,
                        PARTICIPANT_BOX_WIDTH,
                        PARTICIPANT_BOX_HEIGHT,
                    ),
                )
            }

            val participantXByClass = participantLayouts.associate { it.participant.classQualifiedName to it.centerX }
            val nextParticipantCenterXByClass = participantLayouts.mapIndexedNotNull { index, layout ->
                val nextCenterX = participantLayouts.getOrNull(index + 1)?.centerX ?: return@mapIndexedNotNull null
                layout.participant.classQualifiedName to nextCenterX
            }.toMap()
            val activationStateByOrder = computeMessageActivationStates(scenario.messages)

            var visibleRowIndex = 0
            val allMessageLayouts = scenario.messages.map { message ->
                val isVisible = isMessageVisible(message)
                val rowIndex = if (isVisible) visibleRowIndex.also { visibleRowIndex += 1 } else {
                    (visibleRowIndex - 1).coerceAtLeast(0)
                }
                val fromX = participantXByClass[message.fromClassQualifiedName]
                    ?: scenarioX + INNER_PADDING + PARTICIPANT_BOX_WIDTH / 2
                val toX = participantXByClass[message.toClassQualifiedName] ?: fromX
                val y = currentY + SCENARIO_HEADER_HEIGHT + PARTICIPANT_BOX_HEIGHT + MESSAGE_START_GAP +
                    rowIndex * MESSAGE_ROW_HEIGHT +
                    if (!isVisible && message.kind == SequenceMessageKind.RETURN) HIDDEN_RETURN_VERTICAL_OFFSET else 0
                val activationState = activationStateByOrder[message.order] ?: MessageActivationState()
                val selfCallAnchorX = resolveSelfCallAnchorX(fromX, activationState.senderActivationLevel)
                val nextParticipantCenterX = nextParticipantCenterXByClass[message.fromClassQualifiedName]
                val maxSelfCallWidth = if (message.isSelfCall && nextParticipantCenterX != null) {
                    (nextParticipantCenterX - selfCallAnchorX - SELF_CALL_NEXT_PARTICIPANT_GAP)
                        .coerceAtLeast(MIN_SELF_CALL_WIDTH)
                } else {
                    MAX_SELF_CALL_WIDTH
                }
                val desiredSelfCallWidth = message.methodDisplaySignature.length * SELF_CALL_LABEL_CHAR_WIDTH +
                    MESSAGE_LABEL_PADDING * 2
                val selfCallWidth = desiredSelfCallWidth.coerceIn(MIN_SELF_CALL_WIDTH, maxSelfCallWidth)
                val estimatedSelfCallWidth = (selfCallWidth - MESSAGE_LABEL_PADDING * 2).coerceAtLeast(0)
                val selfCallHeight = if (message.kind == SequenceMessageKind.RETURN) SELF_RETURN_HEIGHT else SELF_CALL_HEIGHT
                val labelWidth = if (isVisible && message.methodDisplaySignature.isNotBlank()) {
                    if (message.isSelfCall) {
                        estimatedSelfCallWidth
                    } else {
                        max(minOf(abs(toX - fromX) - MESSAGE_LABEL_PADDING * 2, 220), 90)
                    }
                } else {
                    0
                }
                val labelX = if (message.isSelfCall) {
                    selfCallAnchorX + MESSAGE_LABEL_PADDING
                } else {
                    minOf(fromX, toX) + MESSAGE_LABEL_PADDING
                }
                val labelBounds = Rectangle(labelX, y - 12, labelWidth, 16)
                val highlightKey = message.highlightKey()
                val clickBounds = if (isVisible) {
                    if (message.isSelfCall) {
                        Rectangle(selfCallAnchorX, y - 14, selfCallWidth, selfCallHeight + 18)
                    } else {
                        Rectangle(minOf(fromX, toX), y - 10, abs(toX - fromX).coerceAtLeast(1), 20)
                    }.union(labelBounds)
                } else {
                    Rectangle()
                }

                MessageLayout(
                    message = message,
                    fromX = fromX,
                    toX = toX,
                    y = y,
                    direction = if (fromX <= toX) ArrowDirection.RIGHT else ArrowDirection.LEFT,
                    labelBounds = labelBounds,
                    labelBaselineY = y - 2,
                    clickBounds = clickBounds,
                    selfCallWidth = selfCallWidth,
                    selfCallHeight = selfCallHeight,
                    visible = isVisible,
                    highlightKey = highlightKey,
                    senderActivationLevel = activationState.senderActivationLevel,
                    receiverActivationLevel = activationState.receiverActivationLevel,
                )
            }

            val visibleMessageLayouts = allMessageLayouts.filter { it.visible }
            val activationLayouts = if (showActivationBars) buildActivationLayouts(participantLayouts, allMessageLayouts) else emptyList()
            val lastContentY = listOfNotNull(
                visibleMessageLayouts.maxOfOrNull { it.y + it.selfCallHeight },
                activationLayouts.maxOfOrNull { it.bounds.y + it.bounds.height },
            ).maxOrNull() ?: (currentY + SCENARIO_HEADER_HEIGHT + PARTICIPANT_BOX_HEIGHT + 120)

            val bounds = Rectangle(
                scenarioX,
                currentY,
                scenarioWidth,
                lastContentY - currentY + INNER_PADDING + BOTTOM_PADDING,
            )
            layouts += ScenarioLayout(
                scenario = scenario,
                title = MyBundle.message("sequence.scenarioTitle", scenario.id, scenario.entryMethodDisplayName),
                titleFileName = scenario.entryMethodDisplayName.substringBefore('('),
                bounds = bounds,
                titleBaselineY = currentY + 24,
                lifelineStartY = currentY + SCENARIO_HEADER_HEIGHT + PARTICIPANT_BOX_HEIGHT,
                lifelineEndY = lastContentY + BOTTOM_PADDING / 2,
                participants = participantLayouts,
                messages = visibleMessageLayouts,
                activations = activationLayouts,
            )

            currentY += bounds.height + SCENARIO_GAP
            maxWidth = max(maxWidth, bounds.width + PADDING * 2)
        }

        scenarioLayouts = layouts
        basePreferredSize = Dimension(maxWidth, max(currentY, 480))
        updateScaledPreferredSize()
    }

    private fun buildActivationLayouts(
        participantLayouts: List<ParticipantLayout>,
        allMessageLayouts: List<MessageLayout>,
    ): List<ActivationLayout> {
        val participantXByClass = participantLayouts.associate { it.participant.classQualifiedName to it.centerX }
        val stacks = mutableMapOf<String, ArrayDeque<ActivationStart>>()
        val activations = mutableListOf<ActivationLayout>()

        fun pushActivation(classKey: String, startY: Int) {
            val centerX = participantXByClass[classKey] ?: return
            val stack = stacks.getOrPut(classKey) { ArrayDeque() }
            stack.addLast(ActivationStart(startY, stack.size))
            val current = stack.last()
            activations += ActivationLayout(
                classKey = classKey,
                nestingLevel = current.nestingLevel,
                bounds = Rectangle(
                    centerX - ACTIVATION_BAR_WIDTH / 2 + current.nestingLevel * ACTIVATION_BAR_NEST_OFFSET,
                    current.startY,
                    ACTIVATION_BAR_WIDTH,
                    ACTIVATION_BAR_MIN_HEIGHT,
                ),
            )
        }

        fun popActivation(classKey: String, endY: Int) {
            val stack = stacks[classKey] ?: return
            stack.removeLastOrNull() ?: return
            val activation = activations.lastOrNull { it.classKey == classKey && it.bounds.height == ACTIVATION_BAR_MIN_HEIGHT } ?: return
            activation.bounds.height = (endY - activation.bounds.y).coerceAtLeast(ACTIVATION_BAR_MIN_HEIGHT)
            if (stack.isEmpty()) {
                stacks.remove(classKey)
            }
        }

        allMessageLayouts.forEach { layout ->
            val message = layout.message
            if (message.kind == SequenceMessageKind.CREATE && !showCreateMessages) {
                return@forEach
            }
            val senderIsDatabase = isDatabaseParticipant(message.fromClassQualifiedName)
            val receiverIsDatabase = isDatabaseParticipant(message.toClassQualifiedName)
            if (message.kind != SequenceMessageKind.RETURN) {
                if (!senderIsDatabase && stacks[message.fromClassQualifiedName].isNullOrEmpty()) {
                    pushActivation(message.fromClassQualifiedName, layout.y - ACTIVATION_BAR_TOP_PADDING)
                }
                if (!receiverIsDatabase) {
                    pushActivation(message.toClassQualifiedName, layout.y - ACTIVATION_BAR_TOP_PADDING)
                }
            } else {
                val bottomPadding = if (message.isSelfCall) {
                    SELF_CALL_ACTIVATION_BOTTOM_PADDING
                } else {
                    ACTIVATION_BAR_BOTTOM_PADDING
                }
                if (!senderIsDatabase) {
                    popActivation(message.fromClassQualifiedName, layout.y + bottomPadding)
                }
            }
        }

        val fallbackEndY = allMessageLayouts.maxOfOrNull { it.y + it.selfCallHeight } ?: 0
        activations.filter { it.bounds.height == ACTIVATION_BAR_MIN_HEIGHT }.forEach { activation ->
            activation.bounds.height = (fallbackEndY + ACTIVATION_BAR_BOTTOM_PADDING - activation.bounds.y)
                .coerceAtLeast(ACTIVATION_BAR_MIN_HEIGHT)
        }

        clampNestedActivationBars(activations)

        return activations.map { it.copy(bounds = Rectangle(it.bounds)) }
    }

    private fun isMessageVisible(message: SequenceMessage): Boolean = when (message.kind) {
        SequenceMessageKind.RETURN -> showReturnMessages && !message.isSelfCall && (!message.isCreateReturn || showCreateMessages)
        SequenceMessageKind.CREATE -> showCreateMessages
        SequenceMessageKind.CALL -> true
    }

    private fun resolveSelfCallAnchorX(layout: MessageLayout): Int =
        resolveSelfCallAnchorX(layout.fromX, layout.senderActivationLevel + 1)

    private fun resolveSelfCallAnchorX(centerX: Int, activationLevel: Int): Int =
        if (showActivationBars) {
            centerX + ACTIVATION_BAR_WIDTH / 2 + activationLevel * ACTIVATION_BAR_NEST_OFFSET
        } else {
            centerX
        }

    private fun resolveMessageLineStartX(layout: MessageLayout): Int {
        if (!showActivationBars || layout.message.isSelfCall || isDatabaseParticipant(layout.message.fromClassQualifiedName)) {
            return layout.fromX
        }
        return if (layout.direction == ArrowDirection.RIGHT) {
            layout.fromX + ACTIVATION_BAR_WIDTH / 2 + layout.senderActivationLevel * ACTIVATION_BAR_NEST_OFFSET
        } else {
            layout.fromX - ACTIVATION_BAR_WIDTH / 2 + layout.senderActivationLevel * ACTIVATION_BAR_NEST_OFFSET
        }
    }

    private fun resolveMessageLineEndX(layout: MessageLayout): Int {
        if (!showActivationBars || layout.message.isSelfCall || isDatabaseParticipant(layout.message.toClassQualifiedName)) {
            return layout.toX
        }
        return if (layout.direction == ArrowDirection.RIGHT) {
            layout.toX - ACTIVATION_BAR_WIDTH / 2 + layout.receiverActivationLevel * ACTIVATION_BAR_NEST_OFFSET
        } else {
            layout.toX + ACTIVATION_BAR_WIDTH / 2 + layout.receiverActivationLevel * ACTIVATION_BAR_NEST_OFFSET
        }
    }

    private fun renderToImage(
        scenarioLayout: ScenarioLayout? = null,
        useWhiteBackground: Boolean,
    ): BufferedImage {
        val bounds = scenarioLayout?.bounds ?: Rectangle(0, 0, basePreferredSize.width, basePreferredSize.height)
        val width = max((bounds.width * zoomFactor).roundToInt(), 1)
        val height = max((bounds.height * zoomFactor).roundToInt(), 1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = if (useWhiteBackground) Color.WHITE else background
            graphics.fillRect(0, 0, width, height)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            if (scenarioLayout != null) {
                graphics.scale(zoomFactor, zoomFactor)
                graphics.translate(-bounds.x.toDouble(), -bounds.y.toDouble())
                paintScenario(graphics, scenarioLayout)
            } else {
                paintDiagramContents(graphics)
            }
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun renderToSvg(scenarioLayout: ScenarioLayout? = null): String {
        val image = renderToImage(scenarioLayout, useWhiteBackground = useWhiteBackgroundForCopyExport)
        val title = scenarioLayout?.title ?: MyBundle.message("sequence.title")
        return buildEmbeddedSvg(
            image = image,
            width = image.width,
            height = image.height,
            title = title,
        )
    }

    private fun renderToMermaid(scenarioLayout: ScenarioLayout? = null): String {
        val layouts = scenarioLayout?.let(::listOf) ?: scenarioLayouts
        if (layouts.isEmpty()) {
            return "sequenceDiagram\n"
        }
        return buildString {
            appendLine("sequenceDiagram")
            layouts.forEachIndexed { index, layout ->
                val participantIds = linkedMapOf<String, String>()
                layout.participants.forEach { participant ->
                    val participantId = mermaidNodeId("participant_${participant.participant.classQualifiedName}")
                    participantIds[participant.participant.classQualifiedName] = participantId
                    appendLine("    participant $participantId as \"${mermaidLabel(participant.participant.className)}\"")
                }
                if (index > 0) {
                    appendLine("    %%")
                }
                val firstId = layout.participants.firstOrNull()?.participant?.classQualifiedName?.let(participantIds::get)
                val lastId = layout.participants.lastOrNull()?.participant?.classQualifiedName?.let(participantIds::get)
                if (firstId != null && lastId != null) {
                    appendLine("""    Note over $firstId${if (lastId != firstId) ",$lastId" else ""}: ${mermaidLabel(layout.title)}""")
                }
                layout.messages.forEach { message ->
                    val fromId = participantIds[message.message.fromClassQualifiedName] ?: return@forEach
                    val toId = participantIds[message.message.toClassQualifiedName] ?: return@forEach
                    val arrow = when (message.message.kind) {
                        SequenceMessageKind.CALL -> "->>"
                        SequenceMessageKind.RETURN -> "-->>"
                        SequenceMessageKind.CREATE -> "->>"
                    }
                    val label = when {
                        message.message.methodDisplaySignature.isNotBlank() -> message.message.methodDisplaySignature
                        message.message.kind == SequenceMessageKind.RETURN -> MyBundle.message("sequence.returnMessageLabel")
                        else -> MyBundle.message("sequence.createMessageLabel")
                    }
                    appendLine("""    $fromId$arrow$toId: ${mermaidLabel(label)}""")
                }
            }
        }
    }

    private fun setZoom(newZoomFactor: Double) {
        val clamped = newZoomFactor.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (clamped == zoomFactor) {
            return
        }
        zoomFactor = clamped
        updateScaledPreferredSize()
        revalidate()
        repaint()
        notifyZoomChanged()
    }

    private fun updateScaledPreferredSize() {
        preferredSize = Dimension(
            max((basePreferredSize.width * zoomFactor).roundToInt(), 1),
            max((basePreferredSize.height * zoomFactor).roundToInt(), 1),
        )
    }

    private fun notifyZoomChanged() {
        onZoomChanged?.invoke("${(zoomFactor * 100).roundToInt()}%")
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        SCROLL_UNIT_INCREMENT

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) {
            (visibleRect.height - SCROLL_UNIT_INCREMENT).coerceAtLeast(SCROLL_UNIT_INCREMENT)
        } else {
            (visibleRect.width - SCROLL_UNIT_INCREMENT).coerceAtLeast(SCROLL_UNIT_INCREMENT)
        }

    override fun getScrollableTracksViewportWidth(): Boolean = false

    override fun getScrollableTracksViewportHeight(): Boolean = false

    private fun toDiagramPoint(point: Point): Point =
        Point((point.x / zoomFactor).roundToInt(), (point.y / zoomFactor).roundToInt())

    private fun toggleMethodHighlight(highlightKey: MessageHighlightKey) {
        highlightedMethod = if (highlightedMethod == highlightKey) null else highlightKey
        repaint()
    }

    private fun findMessageAt(point: Point): MessageLayout? =
        scenarioLayouts.asSequence()
            .flatMap { it.messages.asSequence() }
            .firstOrNull { it.clickBounds.contains(point) }

    private fun findScenarioAt(point: Point): ScenarioLayout? =
        scenarioLayouts.firstOrNull { it.bounds.contains(point) }

    private fun viewport(): JViewport? =
        SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport

    private fun scrollViewportByWheel(event: java.awt.event.MouseWheelEvent): Boolean {
        val viewport = viewport() ?: return false
        val delta = event.wheelRotation * event.scrollAmount.coerceAtLeast(1) * SCROLL_UNIT_INCREMENT
        val maxX = maxOf(preferredSize.width - viewport.width, 0)
        val maxY = maxOf(preferredSize.height - viewport.height, 0)
        viewport.viewPosition = if (event.isShiftDown) {
            Point((viewport.viewPosition.x + delta).coerceIn(0, maxX), viewport.viewPosition.y)
        } else {
            Point(viewport.viewPosition.x, (viewport.viewPosition.y + delta).coerceIn(0, maxY))
        }
        return true
    }

    private fun maybeShowScenarioContextMenu(event: MouseEvent) {
        if (!event.isPopupTrigger) {
            return
        }
        val translatedPoint = toDiagramPoint(event.point)
        val messageLayout = findMessageAt(translatedPoint)
        val scenarioLayout = findScenarioAt(translatedPoint) ?: return
        val menu = JPopupMenu()
        val targetMethod = ReadAction.compute<PsiMethod?, RuntimeException> {
            messageLayout?.message?.pointer?.element as? PsiMethod
        }
        if (targetMethod != null) {
            menu.add(JMenuItem(MyBundle.message("toolWindow.menu.sequenceAnalysis")).apply {
                addActionListener { onSequenceAnalysisRequested?.invoke(targetMethod) }
            })
            messageLayout?.highlightKey?.let { highlightKey ->
                val messageKey = if (highlightedMethod == highlightKey) {
                    "sequence.menu.clearMethodHighlight"
                } else {
                    "sequence.menu.highlightMethod"
                }
                menu.add(JMenuItem(MyBundle.message(messageKey)).apply {
                    addActionListener { toggleMethodHighlight(highlightKey) }
                })
            }
            menu.addSeparator()
        }
        menu.add(JMenuItem(MyBundle.message("sequence.menu.openScenarioSequence")).apply {
            addActionListener {
                ReadAction.compute<PsiMethod?, RuntimeException> {
                    scenarioLayout.scenario.entryPointer.element
                }?.let { method ->
                    onSequenceAnalysisRequested?.invoke(method)
                }
            }
        })
        menu.addSeparator()
        addScenarioCopyMenuItems(menu, scenarioLayout)
        addScenarioExportMenuItems(menu, scenarioLayout)
        menu.show(event.component, event.x, event.y)
    }

    private fun addScenarioCopyMenuItems(menu: JPopupMenu, layout: ScenarioLayout) {
        menu.add(JMenuItem(MyBundle.message("sequence.menu.copyScenarioImage")).apply {
            addActionListener { copyScenarioImage(layout) }
        })
        menu.add(JMenuItem(MyBundle.message("sequence.menu.copyScenarioSvg")).apply {
            addActionListener { copyScenarioSvg(layout) }
        })
        menu.add(JMenuItem(MyBundle.message("sequence.menu.copyScenarioMermaid")).apply {
            addActionListener { copyScenarioMermaid(layout) }
        })
    }

    private fun addScenarioExportMenuItems(menu: JPopupMenu, layout: ScenarioLayout) {
        menu.add(JMenuItem(MyBundle.message("sequence.menu.exportScenarioImage")).apply {
            addActionListener { exportScenarioImage(layout) }
        })
        menu.add(JMenuItem(MyBundle.message("sequence.menu.exportScenarioSvg")).apply {
            addActionListener { exportScenarioSvg(layout) }
        })
        menu.add(JMenuItem(MyBundle.message("sequence.menu.exportScenarioMermaid")).apply {
            addActionListener { exportScenarioMermaid(layout) }
        })
    }

    private fun copyScenarioImage(layout: ScenarioLayout) {
        runCatching {
            CopyPasteManager.getInstance().setContents(
                ImageTransferable(renderToImage(layout, useWhiteBackgroundForCopyExport)),
            )
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.copyFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message("sequence.menu.copyScenarioImage"),
            )
        }
    }

    private fun copyScenarioSvg(layout: ScenarioLayout) {
        runCatching {
            copyTextToClipboard(renderToSvg(layout))
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.copyFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message("sequence.menu.copyScenarioSvg"),
            )
        }
    }

    private fun copyScenarioMermaid(layout: ScenarioLayout) {
        runCatching {
            copyTextToClipboard(renderToMermaid(layout))
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.copyFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message("sequence.menu.copyScenarioMermaid"),
            )
        }
    }

    private fun exportScenarioImage(layout: ScenarioLayout) {
        val chooser = JFileChooser().apply {
            dialogTitle = MyBundle.message("sequence.menu.exportScenarioImage")
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter(MyBundle.message("toolWindow.exportFileFilter"), "png")
            selectedFile = File("${sanitizeFileSegment(layout.titleFileName)}${MyBundle.message("sequence.exportScenarioFileSuffix")}.png")
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
            ImageIO.write(renderToImage(layout, useWhiteBackgroundForCopyExport), "png", outputFile)
        }.onSuccess {
            Messages.showInfoMessage(
                this,
                MyBundle.message("toolWindow.exportSuccess", outputFile.absolutePath),
                MyBundle.message("sequence.menu.exportScenarioImage"),
            )
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.exportFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message("sequence.menu.exportScenarioImage"),
            )
        }
    }

    private fun exportScenarioSvg(layout: ScenarioLayout) {
        exportScenarioText(
            titleKey = "sequence.menu.exportScenarioSvg",
            extension = "svg",
            fileFilterKey = "toolWindow.exportSvgFileFilter",
            defaultFileName = "${sanitizeFileSegment(layout.titleFileName)}${MyBundle.message("sequence.exportScenarioFileSuffix")}.svg",
        ) { file ->
            file.writeText(renderToSvg(layout), Charsets.UTF_8)
        }
    }

    private fun exportScenarioMermaid(layout: ScenarioLayout) {
        exportScenarioText(
            titleKey = "sequence.menu.exportScenarioMermaid",
            extension = "mmd",
            fileFilterKey = "toolWindow.exportMermaidFileFilter",
            defaultFileName = "${sanitizeFileSegment(layout.titleFileName)}${MyBundle.message("sequence.exportScenarioFileSuffix")}.mmd",
        ) { file ->
            file.writeText(renderToMermaid(layout), Charsets.UTF_8)
        }
    }

    private fun exportScenarioText(
        titleKey: String,
        extension: String,
        fileFilterKey: String,
        defaultFileName: String,
        writer: (File) -> Unit,
    ) {
        val chooser = JFileChooser().apply {
            dialogTitle = MyBundle.message(titleKey)
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter(MyBundle.message(fileFilterKey), extension)
            selectedFile = File(defaultFileName)
        }

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return
        }

        val selected = chooser.selectedFile ?: return
        val outputFile = if (selected.extension.equals(extension, ignoreCase = true)) {
            selected
        } else {
            File(selected.parentFile ?: File("."), "${selected.name}.$extension")
        }

        runCatching {
            writer(outputFile)
        }.onSuccess {
            Messages.showInfoMessage(
                this,
                MyBundle.message("toolWindow.exportTextSuccess", outputFile.absolutePath),
                MyBundle.message(titleKey),
            )
        }.onFailure { error ->
            Messages.showErrorDialog(
                this,
                MyBundle.message("toolWindow.exportTextFailed", error.message ?: error.javaClass.simpleName),
                MyBundle.message(titleKey),
            )
        }
    }

    private fun drawArrow(graphics: Graphics2D, endX: Float, endY: Float, direction: ArrowDirection) {
        when (direction) {
            ArrowDirection.RIGHT -> {
                graphics.drawLine((endX - 8).toInt(), (endY - 4).toInt(), endX.toInt(), endY.toInt())
                graphics.drawLine((endX - 8).toInt(), (endY + 4).toInt(), endX.toInt(), endY.toInt())
            }

            ArrowDirection.LEFT -> {
                graphics.drawLine((endX + 8).toInt(), (endY - 4).toInt(), endX.toInt(), endY.toInt())
                graphics.drawLine((endX + 8).toInt(), (endY + 4).toInt(), endX.toInt(), endY.toInt())
            }
        }
    }

    private fun fitText(g: Graphics2D, text: String, maxWidth: Int): String {
        if (text.isBlank() || maxWidth <= 0) {
            return ""
        }
        val metrics = g.fontMetrics
        if (metrics.stringWidth(text) <= maxWidth) {
            return text
        }

        val ellipsis = "..."
        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end) + ellipsis
            if (metrics.stringWidth(candidate) <= maxWidth) {
                return candidate
            }
            end--
        }
        return ellipsis
    }

    private fun sanitizeFileSegment(text: String): String =
        text.replace(Regex("[\\\\/:*?\"<>|]"), "-")

    private fun computeMessageActivationStates(messages: List<SequenceMessage>): Map<Int, MessageActivationState> {
        val stacks = mutableMapOf<String, ArrayDeque<Int>>()
        val states = mutableMapOf<Int, MessageActivationState>()

        fun ensureSenderActivation(classKey: String) {
            val stack = stacks.getOrPut(classKey) { ArrayDeque() }
            if (stack.isEmpty()) {
                stack.addLast(0)
            }
        }

        messages.forEach { message ->
            val senderIsDatabase = isDatabaseParticipant(message.fromClassQualifiedName)
            val receiverIsDatabase = isDatabaseParticipant(message.toClassQualifiedName)
            when (message.kind) {
                SequenceMessageKind.CALL, SequenceMessageKind.CREATE -> {
                    val senderLevel = if (senderIsDatabase) {
                        0
                    } else {
                        ensureSenderActivation(message.fromClassQualifiedName)
                        stacks.getValue(message.fromClassQualifiedName).last()
                    }
                    val receiverLevel = if (receiverIsDatabase) {
                        0
                    } else {
                        val receiverStack = stacks.getOrPut(message.toClassQualifiedName) { ArrayDeque() }
                        val level = if (message.isSelfCall) {
                            senderLevel + 1
                        } else {
                            if (receiverStack.isEmpty()) 0 else receiverStack.last() + 1
                        }
                        receiverStack.addLast(level)
                        level
                    }
                    states[message.order] = MessageActivationState(senderLevel, receiverLevel)
                }

                SequenceMessageKind.RETURN -> {
                    val senderLevel = if (senderIsDatabase) {
                        0
                    } else {
                        val senderStack = stacks[message.fromClassQualifiedName]
                        val level = senderStack?.lastOrNull() ?: 0
                        senderStack?.removeLastOrNull()
                        if (senderStack != null && senderStack.isEmpty()) {
                            stacks.remove(message.fromClassQualifiedName)
                        }
                        level
                    }
                    val receiverLevel = stacks[message.toClassQualifiedName]?.lastOrNull() ?: 0
                    states[message.order] = MessageActivationState(senderLevel, receiverLevel)
                }
            }
        }

        return states
    }

    private fun themeAwareColor(
        lightColor: Color,
        fallbackDark: Color,
        darkenFactor: Float = 0.55f,
    ): Color = lightColor

    private fun themeAwareTextColor(lightColor: Color): Color = lightColor

    private fun themeAwareMessageColor(lightColor: Color, highlighted: Boolean): Color = lightColor

    private fun darken(color: Color, factor: Float): Color =
        Color(
            (color.red * factor).roundToInt().coerceIn(0, 255),
            (color.green * factor).roundToInt().coerceIn(0, 255),
            (color.blue * factor).roundToInt().coerceIn(0, 255),
            color.alpha,
        )

    private fun blendColors(first: Color, second: Color, ratio: Float): Color {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        return Color(
            (first.red * inverse + second.red * clamped).roundToInt().coerceIn(0, 255),
            (first.green * inverse + second.green * clamped).roundToInt().coerceIn(0, 255),
            (first.blue * inverse + second.blue * clamped).roundToInt().coerceIn(0, 255),
            (first.alpha * inverse + second.alpha * clamped).roundToInt().coerceIn(0, 255),
        )
    }

    private fun clampNestedActivationBars(activations: List<ActivationLayout>) {
        activations
            .groupBy { it.classKey }
            .values
            .forEach { classActivations ->
                val sorted = classActivations.sortedWith(
                    compareBy<ActivationLayout> { it.bounds.y }
                        .thenBy { it.nestingLevel }
                )
                sorted.forEach { activation ->
                    val parent = sorted
                        .asSequence()
                        .filter { candidate ->
                            candidate !== activation &&
                                candidate.nestingLevel < activation.nestingLevel &&
                                candidate.bounds.y <= activation.bounds.y &&
                                candidate.bounds.y + candidate.bounds.height >= activation.bounds.y
                        }
                        .maxByOrNull { it.nestingLevel }
                        ?: return@forEach
                    val parentBottom = parent.bounds.y + parent.bounds.height
                    val desiredBottom = parentBottom - NESTED_ACTIVATION_BOTTOM_GAP
                    val currentBottom = activation.bounds.y + activation.bounds.height
                    if (currentBottom > desiredBottom) {
                        activation.bounds.height = (desiredBottom - activation.bounds.y).coerceAtLeast(0)
                    }
                }
            }
    }

    private fun SequenceMessage.highlightKey(): MessageHighlightKey? =
        if (kind == SequenceMessageKind.RETURN || methodDisplaySignature.isBlank()) {
            null
        } else {
            MessageHighlightKey(
                toClassQualifiedName = toClassQualifiedName,
                methodDisplaySignature = methodDisplaySignature,
                kind = kind,
            )
        }

    private fun isDatabaseParticipant(classQualifiedName: String): Boolean =
        classQualifiedName.startsWith(DATABASE_PARTICIPANT_PREFIX)

    private data class ScenarioLayout(
        val scenario: SequenceScenario,
        val title: String,
        val titleFileName: String,
        val bounds: Rectangle,
        val titleBaselineY: Int,
        val lifelineStartY: Int,
        val lifelineEndY: Int,
        val participants: List<ParticipantLayout>,
        val messages: List<MessageLayout>,
        val activations: List<ActivationLayout>,
    )

    private data class ParticipantLayout(
        val participant: SequenceParticipant,
        val centerX: Int,
        val boxBounds: Rectangle,
    )

    private data class MessageLayout(
        val message: SequenceMessage,
        val fromX: Int,
        val toX: Int,
        val y: Int,
        val direction: ArrowDirection,
        val labelBounds: Rectangle,
        val labelBaselineY: Int,
        val clickBounds: Rectangle,
        val selfCallWidth: Int,
        val selfCallHeight: Int,
        val visible: Boolean,
        val highlightKey: MessageHighlightKey?,
        val senderActivationLevel: Int,
        val receiverActivationLevel: Int,
    )

    private data class ActivationStart(
        val startY: Int,
        val nestingLevel: Int,
    )

    private data class ActivationLayout(
        val classKey: String,
        val nestingLevel: Int,
        val bounds: Rectangle,
    )

    private data class MessageHighlightKey(
        val toClassQualifiedName: String,
        val methodDisplaySignature: String,
        val kind: SequenceMessageKind,
    )

    private data class MessageActivationState(
        val senderActivationLevel: Int = 0,
        val receiverActivationLevel: Int = 0,
    )

    private enum class ArrowDirection {
        LEFT,
        RIGHT,
    }

    private companion object {
        const val DATABASE_PARTICIPANT_PREFIX = "db:"
        val MESSAGE_STROKE = BasicStroke(1.6f)
        val HIGHLIGHT_MESSAGE_STROKE = BasicStroke(2.4f)
        val DASHED_MESSAGE_STROKE = BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(6f, 4f), 0f)
        val HIGHLIGHT_DASHED_MESSAGE_STROKE = BasicStroke(2.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(7f, 4f), 0f)
        val LIFELINE_STROKE = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(5f, 5f), 0f)
        const val PADDING = 20
        const val INNER_PADDING = 16
        const val PARTICIPANT_BOX_WIDTH = 140
        const val PARTICIPANT_BOX_HEIGHT = 28
        const val PARTICIPANT_COLUMN_WIDTH = 180
        const val SCENARIO_HEADER_HEIGHT = 34
        const val MESSAGE_START_GAP = 34
        const val MESSAGE_ROW_HEIGHT = 38
        const val SCENARIO_GAP = 24
        const val MIN_SCENARIO_WIDTH = 560
        const val BOTTOM_PADDING = 36
        const val MESSAGE_LABEL_PADDING = 8
        const val SELF_CALL_HEIGHT = 18
        const val SELF_RETURN_HEIGHT = 10
        const val SELF_RETURN_WIDTH = 42
        const val SELF_CALL_LABEL_CHAR_WIDTH = 8
        const val MIN_SELF_CALL_WIDTH = 96
        const val MAX_SELF_CALL_WIDTH = 240
        const val SELF_CALL_NEXT_PARTICIPANT_GAP = 28
        const val HIDDEN_RETURN_VERTICAL_OFFSET = 12
        const val ACTIVATION_BAR_WIDTH = 12
        const val ACTIVATION_BAR_NEST_OFFSET = 5
        const val ACTIVATION_BAR_TOP_PADDING = 7
        const val ACTIVATION_BAR_BOTTOM_PADDING = 7
        const val ACTIVATION_BAR_MIN_HEIGHT = 18
        const val SELF_CALL_ACTIVATION_BOTTOM_PADDING = 20
        const val NESTED_ACTIVATION_BOTTOM_GAP = 4
        const val ZOOM_STEP = 0.15
        const val MIN_ZOOM = 0.5
        const val MAX_ZOOM = 2.5
        const val DRAG_THRESHOLD = 4
        const val SCROLL_UNIT_INCREMENT = 32
    }
}
