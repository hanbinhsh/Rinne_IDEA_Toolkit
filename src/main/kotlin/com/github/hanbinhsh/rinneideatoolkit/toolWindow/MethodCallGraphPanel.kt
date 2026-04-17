package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.github.hanbinhsh.rinneideatoolkit.MyBundle
import com.github.hanbinhsh.rinneideatoolkit.model.GraphEdge
import com.github.hanbinhsh.rinneideatoolkit.model.GraphColorSettings
import com.github.hanbinhsh.rinneideatoolkit.model.GraphMethodVisibility
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNode
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNodeKind
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNodeType
import com.github.hanbinhsh.rinneideatoolkit.model.MethodCallGraph
import com.github.hanbinhsh.rinneideatoolkit.services.GraphDataService
import com.github.hanbinhsh.rinneideatoolkit.services.MethodCallAnalyzer
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
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
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.Scrollable
import javax.swing.ToolTipManager
import javax.swing.JViewport
import javax.swing.SwingUtilities
import javax.swing.SwingConstants
import javax.swing.event.MouseInputAdapter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class MethodCallGraphPanel(
    private val project: Project,
    private val analyzer: MethodCallAnalyzer,
) : JPanel(), Scrollable {

    private var graph: MethodCallGraph? = null
    private var groupLayouts: List<ClassGroupLayout> = emptyList()
    private val collapsedGroups = mutableSetOf<String>()
    private val graphDataService = project.service<GraphDataService>()
    private var basePreferredSize = Dimension(720, 480)
    private var zoomFactor = 1.0
    private var pathFocus: PathFocus? = null
    private var suspendedPathFocus: PathFocus? = null
    private var selectionFocus: SelectionFocus? = null
    private val expandedDatabaseFields = mutableSetOf<String>()
    private val focusRequestId = AtomicInteger()
    var useWhiteBackgroundForExport = false
    var colorSettings: GraphColorSettings = GraphColorSettings()
        set(value) {
            field = value
            repaint()
        }
    var onZoomChanged: ((String) -> Unit)? = null
    var onSequenceAnalysisRequested: ((com.intellij.psi.PsiMethod) -> Unit)? = null

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

            override fun mousePressed(e: MouseEvent) {
                maybeShowPopup(e)
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return
                }
                dragStartScreenPoint = e.locationOnScreen
                dragStartViewPosition = viewport()?.viewPosition
                didPan = false
                isDragging = false
            }

            override fun mouseMoved(e: MouseEvent) {
                if (isDragging) {
                    return
                }
                val translatedPoint = toGraphPoint(e.point)
                cursor = when {
                    findNodeActionAt(translatedPoint) != null || findGroupActionAt(translatedPoint) != null ->
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

                    findNodeAt(translatedPoint) != null || findHeaderAt(translatedPoint) != null ->
                        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

                    else -> Cursor.getDefaultCursor()
                }
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

            override fun mouseReleased(e: MouseEvent) {
                maybeShowPopup(e)
                dragStartScreenPoint = null
                dragStartViewPosition = null
                isDragging = false
                cursor = Cursor.getDefaultCursor()
            }

            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger) {
                    return
                }
                if (didPan) {
                    didPan = false
                    return
                }
                val translatedPoint = toGraphPoint(e.point)

                findGroupActionAt(translatedPoint)?.let {
                    toggleHighlight(it.focusTarget)
                    return
                }

                findNodeActionAt(translatedPoint)?.let {
                    toggleHighlight(it.focusTarget)
                    return
                }

                val header = findHeaderAt(translatedPoint)
                if (header != null) {
                    when (header.kind) {
                        GraphNodeKind.METHOD -> toggleSelection(FocusTarget.Class(header.classQualifiedName))
                        GraphNodeKind.DATABASE_TABLE -> header.headerNode?.let { toggleSelection(FocusTarget.Method(it.id)) }
                        GraphNodeKind.DATABASE_COLUMN,
                        GraphNodeKind.DATABASE_COLUMN_OPERATION -> {}
                    }
                    return
                }

                val nodeLayout = findNodeAt(translatedPoint)
                if (nodeLayout == null) {
                    clearSelectionFocus()
                    return
                }
                if (e.clickCount > 1) {
                    val method = nodeLayout.node.pointer?.element as? Navigatable ?: return
                    ApplicationManager.getApplication().invokeLater {
                        method.navigate(true)
                    }
                    return
                }
                toggleSelection(FocusTarget.Method(nodeLayout.node.id))
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

            private fun maybeShowPopup(e: MouseEvent) {
                if (!e.isPopupTrigger) {
                    return
                }
                val translatedPoint = toGraphPoint(e.point)
                val target = findInteractionTarget(translatedPoint) ?: return
                showContextMenu(target, e.x, e.y)
                e.consume()
            }
        }

        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
        notifyZoomChanged()
    }

    fun renderGraph(graph: MethodCallGraph?, clearFocus: Boolean = false) {
        focusRequestId.incrementAndGet()
        this.graph = graph
        if (clearFocus) {
            pathFocus = null
            suspendedPathFocus = null
            selectionFocus = null
            expandedDatabaseFields.clear()
        } else {
            val existingFocus = pathFocus
            pathFocus = graph?.let { currentGraph ->
                existingFocus?.let { focus ->
                    if (focus.mode.requiresExpansion()) {
                        focus.target.resolve(currentGraph)
                            .takeIf { it.isNotEmpty() }
                            ?.also { loadExpandedPathFocus(currentGraph, focus.target, focus.mode) }
                        focus
                    } else {
                        focus.target.resolve(currentGraph)
                            .takeIf { it.isNotEmpty() }
                            ?.let { buildPathFocus(currentGraph, focus.target, focus.mode) }
                    }
                }
            }
        }
        refreshSelectionFocus()
        if (graph == null) {
            groupLayouts = emptyList()
            basePreferredSize = Dimension(720, 480)
            updateScaledPreferredSize()
        } else {
            rebuildLayout()
        }
        revalidate()
        repaint()
    }

    private fun toggleSelection(target: FocusTarget) {
        if (!isClickHighlightEnabled()) {
            return
        }
        val currentGraph = displayGraph() ?: return
        selectionFocus = if (selectionFocus?.target == target) {
            null
        } else {
            buildSelectionFocus(currentGraph, target)
        }
        repaint()
    }

    private fun toggleContextHighlight(target: FocusTarget) {
        val currentGraph = displayGraph() ?: return
        selectionFocus = buildSelectionFocus(currentGraph, target)
        revalidate()
        repaint()
    }

    private fun clearSelectionFocus() {
        if (selectionFocus == null) {
            return
        }
        selectionFocus = null
        repaint()
    }

    private fun buildSelectionFocus(
        currentGraph: MethodCallGraph,
        target: FocusTarget,
    ): SelectionFocus? {
        val targetNodeIds = target.resolve(currentGraph)
        if (targetNodeIds.isEmpty()) {
            return null
        }

        val forward = currentGraph.edges.groupBy { it.fromNodeId }
        val reverse = currentGraph.edges.groupBy { it.toNodeId }
        val upstream = traverse(targetNodeIds, reverse, reverseMode = true)
        val downstream = traverse(targetNodeIds, forward, reverseMode = false)
        val highlightedNodeIds = includeDatabaseContainerNodes(currentGraph, upstream + downstream)
        val highlightedEdges = currentGraph.edges
            .filter { edge ->
                (edge.fromNodeId in upstream && edge.toNodeId in upstream) ||
                    (edge.fromNodeId in downstream && edge.toNodeId in downstream)
            }
            .map { it.key() }
            .toSet()
        val highlightedClasses = currentGraph.nodes
            .filter { it.id in highlightedNodeIds }
            .map { it.classQualifiedName }
            .toSet()

        return SelectionFocus(
            target = target,
            highlightedNodeIds = highlightedNodeIds,
            highlightedEdges = highlightedEdges,
            highlightedClasses = highlightedClasses,
        )
    }

    fun zoomIn() = setZoom(zoomFactor + ZOOM_STEP)

    fun zoomOut() = setZoom(zoomFactor - ZOOM_STEP)

    fun resetZoom() = setZoom(1.0)

    fun setZoomPercent(percent: Int) {
        val minPercent = (MIN_ZOOM * 100).roundToInt()
        val maxPercent = (MAX_ZOOM * 100).roundToInt()
        setZoom(percent.coerceIn(minPercent, maxPercent) / 100.0)
    }

    fun exportToPng(file: File) {
        ImageIO.write(renderToImage(), "png", file)
    }

    fun copyImageToClipboard() {
        CopyPasteManager.getInstance().setContents(ImageTransferable(renderToImage()))
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
        val translatedPoint = toGraphPoint(event.point)
        findGroupActionAt(translatedPoint)?.let { return it.tooltip }
        findNodeActionAt(translatedPoint)?.let { return it.tooltip }
        findHeaderAt(translatedPoint)
            ?.takeIf { it.kind == GraphNodeKind.DATABASE_TABLE }
            ?.headerNode
            ?.let(::databaseTooltip)
            ?.let { return it }
        return findNodeAt(translatedPoint)?.node?.let { node ->
            when (node.nodeKind) {
                GraphNodeKind.METHOD -> "${node.className}.${node.displaySignature}"
                GraphNodeKind.DATABASE_TABLE,
                GraphNodeKind.DATABASE_COLUMN,
                GraphNodeKind.DATABASE_COLUMN_OPERATION -> databaseTooltip(node)
            }
        }
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        paintGraphContents(g, includeControls = true)
    }

    private fun paintGraphContents(graphics: Graphics2D, includeControls: Boolean) {
        val g = graphics.create() as Graphics2D
        g.scale(zoomFactor, zoomFactor)
        val currentDisplayGraph = displayGraph()
        if (groupLayouts.isEmpty() || currentDisplayGraph == null) {
            paintEmptyState(g)
            g.dispose()
            return
        }

        val arrowOverlays = mutableListOf<ArrowOverlay>()
        if (currentDisplayGraph.options.drawEdgesOnTop) {
            groupLayouts.forEach { paintGroup(g, it, includeControls) }
            paintEdges(g, arrowOverlays)
        } else {
            paintEdges(g, arrowOverlays)
            groupLayouts.forEach { paintGroup(g, it, includeControls) }
        }
        if (currentDisplayGraph.options.drawArrowheadsOnTop && arrowOverlays.isNotEmpty()) {
            paintArrowOverlays(g, arrowOverlays)
        }
        g.dispose()
    }

    private fun renderToImage(): BufferedImage {
        val width = maxOf((basePreferredSize.width * zoomFactor).roundToInt(), 1)
        val height = maxOf((basePreferredSize.height * zoomFactor).roundToInt(), 1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = if (useWhiteBackgroundForExport) Color.WHITE else background
            graphics.fillRect(0, 0, width, height)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            paintGraphContents(graphics, includeControls = false)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun renderToSvg(): String {
        val image = renderToImage()
        return buildEmbeddedSvg(
            image = image,
            width = image.width,
            height = image.height,
            title = MyBundle.message("toolWindow.graphTitle"),
        )
    }

    private fun renderToMermaid(): String {
        val currentGraph = displayGraph() ?: return "flowchart LR\n"
        val groupedNodes = currentGraph.nodes.groupBy { it.classQualifiedName }
        val nodeIdMap = currentGraph.nodes.associate { it.id to mermaidNodeId(it.id) }
        return buildString {
            appendLine("flowchart LR")
            groupedNodes.entries
                .sortedWith(
                    compareBy<Map.Entry<String, List<GraphNode>>> { entry -> entry.value.minOfOrNull { node -> node.depth } ?: 0 }
                        .thenBy { entry -> entry.value.firstOrNull()?.className ?: entry.key },
                )
                .forEach { (groupId, nodes) ->
                val groupLabel = nodes.firstOrNull()?.tableName ?: nodes.firstOrNull()?.className ?: groupId
                appendLine("""    subgraph ${mermaidNodeId("group_$groupId")}["${mermaidLabel(groupLabel)}"]""")
                nodes.sortedWith(compareBy<GraphNode> { it.depth }.thenBy { it.displaySignature }).forEach { node ->
                    val mermaidId = nodeIdMap.getValue(node.id)
                    appendLine("        $mermaidId${mermaidNodeShape(node)}")
                }
                appendLine("    end")
                }
            currentGraph.edges.forEach { edge ->
                val fromId = nodeIdMap[edge.fromNodeId] ?: return@forEach
                val toId = nodeIdMap[edge.toNodeId] ?: return@forEach
                appendLine("    $fromId --> $toId")
            }
        }
    }

    private fun mermaidNodeShape(node: GraphNode): String {
        val label = when (node.nodeKind) {
            GraphNodeKind.METHOD -> "${node.className}.${node.displaySignature}"
            GraphNodeKind.DATABASE_TABLE -> node.tableName ?: node.className
            GraphNodeKind.DATABASE_COLUMN -> node.columnName ?: node.displaySignature
            GraphNodeKind.DATABASE_COLUMN_OPERATION ->
                "${node.columnName ?: "column"} ${node.displaySignature}"
        }
        return when (node.nodeKind) {
            GraphNodeKind.DATABASE_TABLE -> """["${mermaidLabel(label)}"]"""
            GraphNodeKind.DATABASE_COLUMN -> """["${mermaidLabel(label)}"]"""
            GraphNodeKind.DATABASE_COLUMN_OPERATION -> """(["${mermaidLabel(label)}"])"""
            GraphNodeKind.METHOD -> """["${mermaidLabel(label)}"]"""
        }
    }

    private fun rebuildLayout() {
        val currentGraph = displayGraph() ?: return
        val baseDepthById = graph?.nodes?.associate { it.id to it.depth }.orEmpty()
        val groupedNodes = currentGraph.nodes
            .groupBy { node ->
                node.classQualifiedName
            }
            .map { (groupKey, nodes) ->
                val headerNode = nodes.firstOrNull { it.nodeKind == GraphNodeKind.DATABASE_TABLE }
                val firstNode = headerNode ?: nodes.first()
                val groupKind = if (headerNode != null) GraphNodeKind.DATABASE_TABLE else GraphNodeKind.METHOD
                ClassBucket(
                    groupKey = groupKey,
                    classQualifiedName = firstNode.classQualifiedName,
                    className = firstNode.tableName ?: firstNode.className,
                    depth = nodes.minOf { node -> baseDepthById[node.id] ?: node.depth },
                    kind = groupKind,
                    headerNode = headerNode,
                    nodes = nodes.sortedWith(
                        compareBy<GraphNode> { nodeTypeRank(it.nodeType) }
                            .thenBy { it.displaySignature },
                    ),
                )
            }
            .sortedWith(compareBy<ClassBucket> { it.depth }.thenBy { it.className })

        val columns = groupedNodes.map { it.depth }.distinct().sorted()
        val columnX = columns.associateWith { depth ->
            PADDING + columns.indexOf(depth) * (CARD_WIDTH + COLUMN_GAP)
        }
        val columnY = columns.associateWith { PADDING }.toMutableMap()

        groupLayouts = groupedNodes.map { bucket ->
            val x = columnX.getValue(bucket.depth)
            val y = columnY.getValue(bucket.depth)
            val collapsed = collapsedGroups.contains(bucket.classQualifiedName)
            val headerBounds = Rectangle(x, y, CARD_WIDTH, HEADER_HEIGHT)
            val actionBounds = Rectangle(
                x + CARD_WIDTH - ACTION_BUTTON_SIZE - INNER_PADDING,
                y + (HEADER_HEIGHT - ACTION_BUTTON_SIZE) / 2,
                ACTION_BUTTON_SIZE,
                ACTION_BUTTON_SIZE,
            )
            val nodeLayouts = mutableListOf<NodeLayout>()
            val databaseFieldLayouts = mutableListOf<DatabaseFieldLayout>()

            var nextY = y + HEADER_HEIGHT + INNER_PADDING
            if (!collapsed) {
                if (bucket.kind == GraphNodeKind.DATABASE_TABLE) {
                    val fieldNodes = bucket.nodes
                        .filter { it.nodeKind == GraphNodeKind.DATABASE_COLUMN }
                        .sortedWith(compareBy<GraphNode> { it.displaySignature })
                    val operationNodesByColumn = bucket.nodes
                        .filter { it.nodeKind == GraphNodeKind.DATABASE_COLUMN_OPERATION }
                        .groupBy { it.columnName.orEmpty() }
                        .mapValues { (_, nodes) ->
                            nodes.sortedWith(
                                compareBy<GraphNode> { databaseActionRank(it.databaseAction) }
                                    .thenBy { it.displaySignature },
                            )
                        }
                    fieldNodes.forEach { fieldNode ->
                        val fieldCardBounds = Rectangle(
                            x + INNER_PADDING,
                            nextY,
                            CARD_WIDTH - INNER_PADDING * 2,
                            NODE_HEIGHT,
                        )
                        val fieldLayout = NodeLayout(
                            node = fieldNode,
                            bounds = fieldCardBounds,
                            actionBounds = Rectangle(
                                fieldCardBounds.x + fieldCardBounds.width - ACTION_BUTTON_SIZE - 6,
                                fieldCardBounds.y + (NODE_HEIGHT - ACTION_BUTTON_SIZE) / 2,
                                ACTION_BUTTON_SIZE,
                                ACTION_BUTTON_SIZE,
                            ),
                        )
                        val expanded = expandedDatabaseFields.contains(fieldNode.id)
                        val operationLayouts = mutableListOf<NodeLayout>()
                        var operationY = fieldCardBounds.y + NODE_HEIGHT + FIELD_OPERATION_GAP
                        if (expanded) {
                            val operationWidth = fieldCardBounds.width - FIELD_CARD_INNER_PADDING * 2
                            val operationX = fieldCardBounds.x + FIELD_CARD_INNER_PADDING
                            operationNodesByColumn[fieldNode.columnName.orEmpty()].orEmpty().forEach { operationNode ->
                                val operationBounds = Rectangle(
                                    operationX,
                                    operationY,
                                    operationWidth,
                                    NODE_HEIGHT,
                                )
                                operationLayouts += NodeLayout(
                                    node = operationNode,
                                    bounds = operationBounds,
                                    actionBounds = Rectangle(
                                        operationBounds.x + operationBounds.width - ACTION_BUTTON_SIZE - 6,
                                        operationBounds.y + (NODE_HEIGHT - ACTION_BUTTON_SIZE) / 2,
                                        ACTION_BUTTON_SIZE,
                                        ACTION_BUTTON_SIZE,
                                    ),
                                )
                                operationY += NODE_HEIGHT + NODE_GAP
                            }
                        }
                        val fieldCardHeight = if (operationLayouts.isEmpty()) {
                            NODE_HEIGHT
                        } else {
                            operationLayouts.last().bounds.y + NODE_HEIGHT - fieldCardBounds.y + FIELD_CARD_INNER_PADDING
                        }
                        databaseFieldLayouts += DatabaseFieldLayout(
                            fieldLayout = fieldLayout,
                            cardBounds = Rectangle(
                                fieldCardBounds.x,
                                fieldCardBounds.y,
                                fieldCardBounds.width,
                                fieldCardHeight,
                            ),
                            operationLayouts = operationLayouts,
                            expanded = expanded,
                        )
                        nextY += fieldCardHeight + NODE_GAP
                    }
                } else {
                    bucket.nodes.forEach { node ->
                        val nodeBounds = Rectangle(x + INNER_PADDING, nextY, CARD_WIDTH - INNER_PADDING * 2, NODE_HEIGHT)
                        nodeLayouts += NodeLayout(
                            node = node,
                            bounds = nodeBounds,
                            actionBounds = Rectangle(
                                nodeBounds.x + nodeBounds.width - ACTION_BUTTON_SIZE - 6,
                                nodeBounds.y + (NODE_HEIGHT - ACTION_BUTTON_SIZE) / 2,
                                ACTION_BUTTON_SIZE,
                                ACTION_BUTTON_SIZE,
                            ),
                        )
                        nextY += NODE_HEIGHT + NODE_GAP
                    }
                }
            }

            val cardHeight = if (collapsed) {
                HEADER_HEIGHT
            } else {
                nextY - y + INNER_PADDING - NODE_GAP
            }
            val cardBounds = Rectangle(x, y, CARD_WIDTH, maxOf(cardHeight, HEADER_HEIGHT))
            columnY[bucket.depth] = y + cardBounds.height + ROW_GAP

            ClassGroupLayout(
                groupKey = bucket.groupKey,
                classQualifiedName = bucket.classQualifiedName,
                className = bucket.className,
                depth = bucket.depth,
                kind = bucket.kind,
                headerNode = bucket.headerNode,
                cardBounds = cardBounds,
                headerBounds = headerBounds,
                actionBounds = actionBounds,
                nodeLayouts = nodeLayouts,
                databaseFieldLayouts = databaseFieldLayouts,
                collapsed = collapsed,
            )
        }

        val sameColumnOutsideMargin = if (currentGraph.options.routeSameColumnEdgesOutside) {
            SAME_COLUMN_BASE_OFFSET +
                (SAME_COLUMN_LANE_COUNT - 1) * SAME_COLUMN_LANE_GAP +
                SAME_COLUMN_EXTRA_CANVAS_PADDING
        } else {
            0
        }
        val totalWidth = (columnX.values.maxOrNull() ?: PADDING) + CARD_WIDTH + PADDING + sameColumnOutsideMargin
        val totalHeight = (groupLayouts.maxOfOrNull { it.cardBounds.y + it.cardBounds.height } ?: 0) + PADDING
        basePreferredSize = Dimension(totalWidth, maxOf(totalHeight, 480))
        updateScaledPreferredSize()
    }

    private fun paintEmptyState(g: Graphics2D) {
        g.color = JBColor.GRAY
        g.font = font.deriveFont(Font.PLAIN, font.size2D + 1)
        g.drawString(MyBundle.message("toolWindow.emptyState"), PADDING, 40)
    }

    private fun paintEdges(g: Graphics2D, arrowOverlays: MutableList<ArrowOverlay>) {
        val currentGraph = displayGraph() ?: return
        val visibleNodes = buildMap<String, Rectangle> {
            groupLayouts.forEach { group ->
                group.headerNode?.let { put(it.id, group.headerBounds) }
                allNodeLayouts(group).forEach { layout ->
                    put(layout.node.id, layout.bounds)
                }
            }
        }
        val highlightFocus = pathFocus?.takeIf { it.mode.isHighlightMode() }
        val activeSelectionFocus = selectionFocus
        val selectionColor = colorSettings.selectionHighlightColor()

        if (!currentGraph.options.showDetailedCallEdges) {
            paintModuleEdges(g, currentGraph, highlightFocus, activeSelectionFocus, selectionColor, arrowOverlays)
            return
        }

        fun paintEdge(edge: GraphEdge) {
            val source = visibleNodes[edge.fromNodeId] ?: return
            val target = visibleNodes[edge.toNodeId] ?: return
            val edgeKey = edge.key()
            val highlighted = highlightFocus?.highlightedEdges?.contains(edgeKey) == true
            val selected = activeSelectionFocus?.highlightedEdges?.contains(edgeKey) == true
            val alpha = when {
                activeSelectionFocus != null -> if (selected) 1f else DIMMED_ALPHA
                highlightFocus != null -> if (highlighted) 1f else DIMMED_ALPHA
                else -> 1f
            }
            val strokeWidth = when {
                selected -> 2.8f
                activeSelectionFocus != null -> 1.4f
                highlighted -> 2.2f
                else -> 1.4f
            }
            val edgeColor = when {
                selected && edge.fromNodeId == edge.toNodeId -> blendColors(JBColor.GRAY, selectionColor, 0.55f)
                selected -> selectionColor
                edge.fromNodeId == edge.toNodeId -> JBColor.GRAY
                else -> JBColor(Color(123, 140, 171), Color(126, 153, 193))
            }
            drawEdge(
                g = g,
                edge = edge,
                sourceBounds = source,
                targetBounds = target,
                alpha = alpha,
                strokeWidth = strokeWidth,
                edgeColor = edgeColor,
                routeSameColumnOutside = currentGraph.options.routeSameColumnEdgesOutside,
                drawArrowheadsOnTop = currentGraph.options.drawArrowheadsOnTop,
                emphasize = selected || (activeSelectionFocus == null && highlighted),
                arrowOverlays = arrowOverlays,
            )
        }

        currentGraph.edges
            .filter { edge -> isEdgeVisible(currentGraph, edge) }
            .filterNot { edge ->
                val edgeKey = edge.key()
                activeSelectionFocus?.highlightedEdges?.contains(edgeKey) == true ||
                    highlightFocus?.highlightedEdges?.contains(edgeKey) == true
            }
            .forEach(::paintEdge)

        currentGraph.edges
            .filter { edge -> isEdgeVisible(currentGraph, edge) }
            .filter { edge ->
                val edgeKey = edge.key()
                activeSelectionFocus?.highlightedEdges?.contains(edgeKey) == true ||
                    highlightFocus?.highlightedEdges?.contains(edgeKey) == true
            }
            .forEach(::paintEdge)
    }

    private fun paintModuleEdges(
        g: Graphics2D,
        currentGraph: MethodCallGraph,
        highlightFocus: PathFocus?,
        selectionFocus: SelectionFocus?,
        selectionColor: Color,
        arrowOverlays: MutableList<ArrowOverlay>,
    ) {
        val nodeById = currentGraph.nodes.associateBy { it.id }
        val groupByClass = groupLayouts.associateBy { it.classQualifiedName }
        val moduleEdges = linkedMapOf<Pair<String, String>, ModuleEdgeState>()

        currentGraph.edges
            .filter { edge -> isEdgeVisible(currentGraph, edge) }
            .forEach { edge ->
            val fromClass = nodeById[edge.fromNodeId]?.classQualifiedName ?: return@forEach
            val toClass = nodeById[edge.toNodeId]?.classQualifiedName ?: return@forEach
            if (fromClass == toClass) {
                return@forEach
            }
            val key = fromClass to toClass
            val highlighted = highlightFocus?.highlightedEdges?.contains(edge.key()) == true
            val selected = selectionFocus?.highlightedEdges?.contains(edge.key()) == true
            val existing = moduleEdges[key]
            moduleEdges[key] = ModuleEdgeState(
                highlighted = existing?.highlighted == true || highlighted,
                selected = existing?.selected == true || selected,
            )
        }

        moduleEdges
            .entries
            .sortedBy { if (it.value.selected || it.value.highlighted) 1 else 0 }
            .forEach { (classPair, state) ->
            val fromGroup = groupByClass[classPair.first] ?: return@forEach
            val toGroup = groupByClass[classPair.second] ?: return@forEach
            val alpha = when {
                selectionFocus != null -> if (state.selected) 1f else DIMMED_ALPHA
                highlightFocus != null -> if (state.highlighted) 1f else DIMMED_ALPHA
                else -> 1f
            }
            val strokeWidth = when {
                state.selected -> 3.0f
                selectionFocus != null -> 1.8f
                state.highlighted -> 2.4f
                else -> 1.8f
            }
            drawModuleEdge(
                g = g,
                sourceBounds = fromGroup.cardBounds,
                targetBounds = toGroup.cardBounds,
                alpha = alpha,
                strokeWidth = strokeWidth,
                edgeColor = if (state.selected) {
                    selectionColor
                } else {
                    JBColor(Color(103, 123, 164), Color(140, 168, 214))
                },
                routeSameColumnOutside = currentGraph.options.routeSameColumnEdgesOutside,
                laneSeed = classPair.hashCode(),
                drawArrowheadsOnTop = currentGraph.options.drawArrowheadsOnTop,
                emphasize = state.selected || (selectionFocus == null && state.highlighted),
                arrowOverlays = arrowOverlays,
            )
        }
    }

    private fun drawEdge(
        g: Graphics2D,
        edge: GraphEdge,
        sourceBounds: Rectangle,
        targetBounds: Rectangle,
        alpha: Float,
        strokeWidth: Float,
        edgeColor: Color,
        routeSameColumnOutside: Boolean,
        drawArrowheadsOnTop: Boolean,
        emphasize: Boolean,
        arrowOverlays: MutableList<ArrowOverlay>,
    ) {
        val graphics = g.create() as Graphics2D
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        graphics.stroke = BasicStroke(strokeWidth)
        graphics.color = edgeColor

        if (edge.fromNodeId == edge.toNodeId) {
            drawSelfLoopEdge(
                graphics = graphics,
                bounds = sourceBounds,
                drawArrowheadsOnTop = drawArrowheadsOnTop,
                arrowOverlays = arrowOverlays,
                arrowColor = edgeColor,
                alpha = alpha,
                emphasize = emphasize,
            )
            graphics.dispose()
            return
        }

        if (routeSameColumnOutside && isSameColumn(sourceBounds, targetBounds)) {
            drawSameColumnOutsideEdge(
                graphics = graphics,
                sourceBounds = sourceBounds,
                targetBounds = targetBounds,
                laneSeed = edge.key().hashCode(),
                drawArrowheadsOnTop = drawArrowheadsOnTop,
                arrowOverlays = arrowOverlays,
                arrowColor = edgeColor,
                alpha = alpha,
                emphasize = emphasize,
            )
            graphics.dispose()
            return
        }

        val startX = sourceBounds.maxX.toFloat()
        val startY = sourceBounds.centerY.toFloat()
        val endX = targetBounds.x.toFloat()
        val endY = targetBounds.centerY.toFloat()
        val midX = (startX + endX) / 2

        val path = Path2D.Float().apply {
            moveTo(startX.toDouble(), startY.toDouble())
            curveTo(
                midX.toDouble(),
                startY.toDouble(),
                midX.toDouble(),
                endY.toDouble(),
                endX.toDouble(),
                endY.toDouble(),
            )
        }
        graphics.draw(path)
        queueOrDrawArrow(
            graphics = graphics,
            drawOnTop = drawArrowheadsOnTop,
            arrowOverlays = arrowOverlays,
            color = edgeColor,
            alpha = alpha,
            endX = endX,
            endY = endY,
            direction = ArrowDirection.RIGHT,
            emphasize = emphasize,
        )
        graphics.dispose()
    }

    private fun drawSelfLoopEdge(
        graphics: Graphics2D,
        bounds: Rectangle,
        drawArrowheadsOnTop: Boolean,
        arrowOverlays: MutableList<ArrowOverlay>,
        arrowColor: Color,
        alpha: Float,
        emphasize: Boolean,
    ) {
        val startX = bounds.maxX.toFloat()
        val centerY = bounds.centerY.toFloat()
        val topY = centerY - SELF_LOOP_VERTICAL_SPAN / 2f
        val endY = centerY + SELF_LOOP_VERTICAL_SPAN / 2f
        val loopX = startX + SELF_LOOP_HORIZONTAL_OFFSET
        val cornerRadius = SELF_LOOP_CORNER_RADIUS.coerceAtMost((endY - topY) / 2f)

        val path = Path2D.Float().apply {
            moveTo(startX.toDouble(), topY.toDouble())
            lineTo((loopX - cornerRadius).toDouble(), topY.toDouble())
            quadTo(
                loopX.toDouble(),
                topY.toDouble(),
                loopX.toDouble(),
                (topY + cornerRadius).toDouble(),
            )
            lineTo(loopX.toDouble(), (endY - cornerRadius).toDouble())
            quadTo(
                loopX.toDouble(),
                endY.toDouble(),
                (loopX - cornerRadius).toDouble(),
                endY.toDouble(),
            )
            lineTo(startX.toDouble(), endY.toDouble())
        }
        graphics.draw(path)
        queueOrDrawArrow(
            graphics = graphics,
            drawOnTop = drawArrowheadsOnTop,
            arrowOverlays = arrowOverlays,
            color = arrowColor,
            alpha = alpha,
            endX = startX,
            endY = endY,
            direction = ArrowDirection.LEFT,
            emphasize = emphasize,
        )
    }

    private fun drawModuleEdge(
        g: Graphics2D,
        sourceBounds: Rectangle,
        targetBounds: Rectangle,
        alpha: Float,
        strokeWidth: Float,
        edgeColor: Color,
        routeSameColumnOutside: Boolean,
        laneSeed: Int,
        drawArrowheadsOnTop: Boolean,
        emphasize: Boolean,
        arrowOverlays: MutableList<ArrowOverlay>,
    ) {
        val graphics = g.create() as Graphics2D
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        graphics.stroke = BasicStroke(strokeWidth)
        graphics.color = edgeColor

        if (routeSameColumnOutside && isSameColumn(sourceBounds, targetBounds)) {
            drawSameColumnOutsideEdge(
                graphics = graphics,
                sourceBounds = sourceBounds,
                targetBounds = targetBounds,
                laneSeed = laneSeed,
                drawArrowheadsOnTop = drawArrowheadsOnTop,
                arrowOverlays = arrowOverlays,
                arrowColor = edgeColor,
                alpha = alpha,
                emphasize = emphasize,
            )
            graphics.dispose()
            return
        }

        val startX = sourceBounds.maxX.toFloat()
        val startY = sourceBounds.centerY.toFloat()
        val endX = targetBounds.x.toFloat()
        val endY = targetBounds.centerY.toFloat()
        val midX = (startX + endX) / 2

        val path = Path2D.Float().apply {
            moveTo(startX.toDouble(), startY.toDouble())
            curveTo(
                midX.toDouble(),
                startY.toDouble(),
                midX.toDouble(),
                endY.toDouble(),
                endX.toDouble(),
                endY.toDouble(),
            )
        }
        graphics.draw(path)
        queueOrDrawArrow(
            graphics = graphics,
            drawOnTop = drawArrowheadsOnTop,
            arrowOverlays = arrowOverlays,
            color = edgeColor,
            alpha = alpha,
            endX = endX,
            endY = endY,
            direction = ArrowDirection.RIGHT,
            emphasize = emphasize,
        )
        graphics.dispose()
    }

    private fun drawSameColumnOutsideEdge(
        graphics: Graphics2D,
        sourceBounds: Rectangle,
        targetBounds: Rectangle,
        laneSeed: Int,
        drawArrowheadsOnTop: Boolean,
        arrowOverlays: MutableList<ArrowOverlay>,
        arrowColor: Color,
        alpha: Float,
        emphasize: Boolean,
    ) {
        val startX = sourceBounds.maxX.toFloat()
        val startY = sourceBounds.centerY.toFloat()
        val endX = targetBounds.maxX.toFloat()
        val endY = targetBounds.centerY.toFloat()
        val laneOffset = (SAME_COLUMN_BASE_OFFSET + (abs(laneSeed) % SAME_COLUMN_LANE_COUNT) * SAME_COLUMN_LANE_GAP).toFloat()
        val channelX = maxOf(sourceBounds.maxX, targetBounds.maxX).toFloat() + laneOffset
        val verticalDistance = endY - startY
        val cornerRadius = minOf(
            SAME_COLUMN_CORNER_RADIUS,
            abs(verticalDistance) / 2f,
            abs(channelX - startX) / 2f,
            abs(channelX - endX) / 2f,
        )

        val path = Path2D.Float().apply {
            moveTo(startX.toDouble(), startY.toDouble())

            if (cornerRadius <= 0f || verticalDistance == 0f) {
                lineTo(channelX.toDouble(), startY.toDouble())
                lineTo(channelX.toDouble(), endY.toDouble())
                lineTo(endX.toDouble(), endY.toDouble())
            } else {
                val verticalDirection = if (verticalDistance > 0) 1f else -1f
                val firstCornerStartX = channelX - cornerRadius
                val firstCornerEndY = startY + verticalDirection * cornerRadius
                val secondCornerStartY = endY - verticalDirection * cornerRadius
                val secondCornerEndX = channelX - cornerRadius

                lineTo(firstCornerStartX.toDouble(), startY.toDouble())
                quadTo(
                    channelX.toDouble(),
                    startY.toDouble(),
                    channelX.toDouble(),
                    firstCornerEndY.toDouble(),
                )
                lineTo(channelX.toDouble(), secondCornerStartY.toDouble())
                quadTo(
                    channelX.toDouble(),
                    endY.toDouble(),
                    secondCornerEndX.toDouble(),
                    endY.toDouble(),
                )
                lineTo(endX.toDouble(), endY.toDouble())
            }
        }
        graphics.draw(path)
        queueOrDrawArrow(
            graphics = graphics,
            drawOnTop = drawArrowheadsOnTop,
            arrowOverlays = arrowOverlays,
            color = arrowColor,
            alpha = alpha,
            endX = endX,
            endY = endY,
            direction = ArrowDirection.LEFT,
            emphasize = emphasize,
        )
    }

    private fun queueOrDrawArrow(
        graphics: Graphics2D,
        drawOnTop: Boolean,
        arrowOverlays: MutableList<ArrowOverlay>,
        color: Color,
        alpha: Float,
        endX: Float,
        endY: Float,
        direction: ArrowDirection,
        emphasize: Boolean,
    ) {
        if (drawOnTop) {
            arrowOverlays += ArrowOverlay(endX, endY, direction, color, alpha, emphasize)
            return
        }
        drawArrow(graphics, endX, endY, direction)
    }

    private fun paintArrowOverlays(g: Graphics2D, arrowOverlays: List<ArrowOverlay>) {
        arrowOverlays
            .sortedBy { if (it.emphasize) 1 else 0 }
            .forEach { overlay ->
            val graphics = g.create() as Graphics2D
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlay.alpha)
            graphics.color = overlay.color
            drawArrow(graphics, overlay.endX, overlay.endY, overlay.direction)
            graphics.dispose()
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

            ArrowDirection.DOWN -> {
                graphics.drawLine((endX - 4).toInt(), (endY - 8).toInt(), endX.toInt(), endY.toInt())
                graphics.drawLine((endX + 4).toInt(), (endY - 8).toInt(), endX.toInt(), endY.toInt())
            }

            ArrowDirection.UP -> {
                graphics.drawLine((endX - 4).toInt(), (endY + 8).toInt(), endX.toInt(), endY.toInt())
                graphics.drawLine((endX + 4).toInt(), (endY + 8).toInt(), endX.toInt(), endY.toInt())
            }
        }
    }

    private fun paintGroup(g: Graphics2D, group: ClassGroupLayout, includeControls: Boolean) {
        val graphics = g.create() as Graphics2D
        if (shouldDimClass(group.classQualifiedName)) {
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIMMED_ALPHA)
        }
        val cardBounds = group.cardBounds
        val classSelected = isClassSelected(group.classQualifiedName)
        val selectionAccent = colorSettings.selectionHighlightColor()
        graphics.color = if (group.kind == GraphNodeKind.DATABASE_TABLE) {
            JBColor(
                blendColors(colorSettings.tableFillLightColor(), Color.WHITE, 0.2f),
                blendColors(colorSettings.tableFillDarkColor(), Color(35, 37, 42), 0.18f),
            )
        } else {
            JBColor(Color(237, 241, 247), Color(50, 53, 58))
        }
        graphics.fillRoundRect(cardBounds.x, cardBounds.y, cardBounds.width, cardBounds.height, 18, 18)
        graphics.color = if (group.kind == GraphNodeKind.DATABASE_TABLE) {
            JBColor(colorSettings.tableBorderLightColor(), colorSettings.tableBorderDarkColor())
        } else {
            JBColor(Color(206, 214, 229), Color(72, 77, 84))
        }
        graphics.drawRoundRect(cardBounds.x, cardBounds.y, cardBounds.width, cardBounds.height, 18, 18)

        val groupRole = if (group.kind == GraphNodeKind.DATABASE_TABLE) {
            FocusVisualRole.NONE
        } else {
            focusRoleForClass(group.classQualifiedName)
        }
        val headerStart = groupHeaderStartColor(group.depth, groupRole, group.kind)
        val headerEnd = groupHeaderEndColor(group.depth, groupRole, group.kind)
        graphics.paint = if (group.kind == GraphNodeKind.DATABASE_TABLE) {
            GradientPaint(
                group.headerBounds.x.toFloat(),
                group.headerBounds.y.toFloat(),
                JBColor(colorSettings.tableBorderLightColor(), colorSettings.tableBorderDarkColor()),
                group.headerBounds.maxX.toFloat(),
                group.headerBounds.maxY.toFloat(),
                JBColor(
                    blendColors(colorSettings.tableBorderLightColor(), colorSettings.tableFillLightColor(), 0.18f),
                    blendColors(colorSettings.tableBorderDarkColor(), colorSettings.tableFillDarkColor(), 0.12f),
                ),
            )
        } else {
            GradientPaint(
                group.headerBounds.x.toFloat(),
                group.headerBounds.y.toFloat(),
                JBColor(headerStart, headerStart.darker()),
                group.headerBounds.maxX.toFloat(),
                group.headerBounds.maxY.toFloat(),
                JBColor(headerEnd, headerEnd.darker()),
            )
        }
        graphics.fillRoundRect(
            group.headerBounds.x,
            group.headerBounds.y,
            group.headerBounds.width,
            group.headerBounds.height,
            18,
            18,
        )

        graphics.color = JBColor.WHITE
        graphics.font = font.deriveFont(Font.BOLD, font.size2D + 0.5f)
        val title = if (group.collapsed) {
            group.className + MyBundle.message("toolWindow.classCollapsedSuffix")
        } else {
            group.className
        }
        val reservedWidth = if (includeControls && groupHeaderHasAction(group)) ACTION_BUTTON_SIZE + 14 else 0
        graphics.drawString(
            fitText(graphics, title, group.headerBounds.width - 24 - reservedWidth),
            group.headerBounds.x + 12,
            group.headerBounds.y + 22,
        )
        if (includeControls && groupHeaderHasAction(group)) {
            paintActionButton(graphics, group.actionBounds, isClassRelevant(group.classQualifiedName))
        }

        fun paintSelectionOutline() {
            if (!classSelected || group.kind == GraphNodeKind.DATABASE_TABLE) {
                return
            }
            graphics.stroke = BasicStroke(2.4f)
            graphics.color = JBColor(
                blendColors(selectionAccent, Color.WHITE, 0.08f),
                blendColors(selectionAccent, Color(230, 230, 230), 0.12f),
            )
            graphics.drawRoundRect(cardBounds.x - 1, cardBounds.y - 1, cardBounds.width + 2, cardBounds.height + 2, 20, 20)
        }

        if (group.collapsed) {
            paintSelectionOutline()
            graphics.dispose()
            return
        }

        graphics.font = font
        if (group.kind == GraphNodeKind.DATABASE_TABLE) {
            group.databaseFieldLayouts.forEach { paintDatabaseField(graphics, it, includeControls) }
        } else {
            group.nodeLayouts.forEach { paintNode(graphics, it, includeControls) }
        }
        paintSelectionOutline()
        graphics.dispose()
    }

    private fun paintDatabaseField(g: Graphics2D, field: DatabaseFieldLayout, includeControls: Boolean) {
        val graphics = g.create() as Graphics2D
        if (shouldDimNode(field.fieldLayout.node.id)) {
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIMMED_ALPHA)
        }
        val cardBounds = field.cardBounds
        val headerNode = field.fieldLayout.node
        graphics.color = JBColor(
            blendColors(colorSettings.columnFillLightColor(), Color.WHITE, 0.2f),
            blendColors(colorSettings.columnFillDarkColor(), Color(35, 37, 42), 0.18f),
        )
        graphics.fillRoundRect(cardBounds.x, cardBounds.y, cardBounds.width, cardBounds.height, 12, 12)
        graphics.color = JBColor(colorSettings.columnBorderLightColor(), colorSettings.columnBorderDarkColor())
        graphics.drawRoundRect(cardBounds.x, cardBounds.y, cardBounds.width, cardBounds.height, 12, 12)
        paintNode(graphics, field.fieldLayout, includeControls)
        field.operationLayouts.forEach { paintNode(graphics, it, includeControls) }
        graphics.dispose()
    }

    private fun paintNode(g: Graphics2D, layout: NodeLayout, includeControls: Boolean) {
        val graphics = g.create() as Graphics2D
        if (shouldDimNode(layout.node.id)) {
            graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIMMED_ALPHA)
        }
        val node = layout.node
        val bounds = layout.bounds
        val useVisibilityColors = graph?.options?.showVisibilityColors == true
        val (baseFillColor, baseBorderColor) = nodeColors(node, useVisibilityColors)
        val focusRole = if (node.nodeKind == GraphNodeKind.METHOD) {
            focusRoleForNode(node.id)
        } else {
            FocusVisualRole.NONE
        }
        var (fillColor, borderColor) = when (focusRole) {
            FocusVisualRole.NONE -> baseFillColor to baseBorderColor
            FocusVisualRole.TARGET -> mixFocusColors(baseFillColor, baseBorderColor, colorSettings.targetFocusColor())
            FocusVisualRole.CALLER -> mixFocusColors(baseFillColor, baseBorderColor, colorSettings.callerFocusColor())
            FocusVisualRole.CALLEE -> mixFocusColors(baseFillColor, baseBorderColor, colorSettings.calleeFocusColor())
            FocusVisualRole.MIXED -> mixFocusColors(baseFillColor, baseBorderColor, colorSettings.mixedFocusColor())
        }
        val selected = isNodeSelected(node.id)
        if (isNodeSelected(node.id)) {
            val accent = colorSettings.selectionHighlightColor()
            borderColor = JBColor(
                blendColors(borderColor, accent, 0.78f),
                blendColors(borderColor.darker(), accent.brighter(), 0.68f),
            )
        }

        graphics.color = fillColor
        graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12)
        graphics.stroke = BasicStroke(if (selected) 2.2f else 1f)
        graphics.color = borderColor
        graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 12, 12)

        graphics.color = JBColor(Color(35, 39, 42), Color(230, 230, 230))
        val prefix = if (node.isSupplemental && node.nodeKind == GraphNodeKind.METHOD) {
            MyBundle.message("toolWindow.nodeOptionalPrefix")
        } else {
            ""
        }
        val reservedWidth = if (includeControls) ACTION_BUTTON_SIZE + 14 else 0
        val text = fitText(graphics, "$prefix${node.displaySignature}", bounds.width - 18 - reservedWidth)
        graphics.drawString(text, bounds.x + 10, bounds.y + 19)
        if (includeControls) {
            paintActionButton(graphics, layout.actionBounds, isNodeRelevant(layout.node.id))
        }
        graphics.dispose()
    }

    private fun nodeColors(node: GraphNode, useVisibilityColors: Boolean): Pair<JBColor, JBColor> {
        if (node.nodeKind == GraphNodeKind.DATABASE_TABLE) {
            return themedColorPair(
                lightFill = colorSettings.tableFillLightColor(),
                darkFill = colorSettings.tableFillDarkColor(),
                lightBorder = colorSettings.tableBorderLightColor(),
                darkBorder = colorSettings.tableBorderDarkColor(),
            )
        }
        if (node.nodeKind == GraphNodeKind.DATABASE_COLUMN) {
            return themedColorPair(
                lightFill = colorSettings.columnFillLightColor(),
                darkFill = colorSettings.columnFillDarkColor(),
                lightBorder = colorSettings.columnBorderLightColor(),
                darkBorder = colorSettings.columnBorderDarkColor(),
            )
        }
        if (node.nodeKind == GraphNodeKind.DATABASE_COLUMN_OPERATION) {
            return themedColorPair(
                lightFill = colorSettings.columnActionFillLightColor(),
                darkFill = colorSettings.columnActionFillDarkColor(),
                lightBorder = colorSettings.columnActionBorderLightColor(),
                darkBorder = colorSettings.columnActionBorderDarkColor(),
            )
        }
        if (useVisibilityColors && node.visibility == GraphMethodVisibility.PRIVATE) {
            return themedColorPair(
                lightFill = colorSettings.privateFillLightColor(),
                darkFill = colorSettings.privateFillDarkColor(),
                lightBorder = colorSettings.privateBorderLightColor(),
                darkBorder = colorSettings.privateBorderDarkColor(),
            )
        }

        return defaultNodeColors(node.nodeType)
    }

    private fun defaultNodeColors(nodeType: GraphNodeType): Pair<JBColor, JBColor> = when (nodeType) {
        GraphNodeType.ROOT ->
            themedColorPair(
                lightFill = colorSettings.rootFillLightColor(),
                darkFill = colorSettings.rootFillDarkColor(),
                lightBorder = colorSettings.rootBorderLightColor(),
                darkBorder = colorSettings.rootBorderDarkColor(),
            )

        GraphNodeType.REACHABLE ->
            themedColorPair(
                lightFill = colorSettings.reachableFillLightColor(),
                darkFill = colorSettings.reachableFillDarkColor(),
                lightBorder = colorSettings.reachableBorderLightColor(),
                darkBorder = colorSettings.reachableBorderDarkColor(),
            )

        GraphNodeType.SUPPLEMENTAL ->
            themedColorPair(
                lightFill = colorSettings.supplementalFillLightColor(),
                darkFill = colorSettings.supplementalFillDarkColor(),
                lightBorder = colorSettings.supplementalBorderLightColor(),
                darkBorder = colorSettings.supplementalBorderDarkColor(),
            )

        GraphNodeType.DATABASE_TABLE ->
            themedColorPair(
                lightFill = colorSettings.tableFillLightColor(),
                darkFill = colorSettings.tableFillDarkColor(),
                lightBorder = colorSettings.tableBorderLightColor(),
                darkBorder = colorSettings.tableBorderDarkColor(),
            )

        GraphNodeType.DATABASE_COLUMN ->
            themedColorPair(
                lightFill = colorSettings.columnFillLightColor(),
                darkFill = colorSettings.columnFillDarkColor(),
                lightBorder = colorSettings.columnBorderLightColor(),
                darkBorder = colorSettings.columnBorderDarkColor(),
            )

        GraphNodeType.DATABASE_COLUMN_OPERATION ->
            themedColorPair(
                lightFill = colorSettings.columnActionFillLightColor(),
                darkFill = colorSettings.columnActionFillDarkColor(),
                lightBorder = colorSettings.columnActionBorderLightColor(),
                darkBorder = colorSettings.columnActionBorderDarkColor(),
            )
    }

    private fun themedColorPair(
        lightFill: Color,
        darkFill: Color,
        lightBorder: Color,
        darkBorder: Color,
    ): Pair<JBColor, JBColor> = JBColor(lightFill, darkFill) to JBColor(lightBorder, darkBorder)

    private fun blendColors(primary: Color, secondary: Color, secondaryWeight: Float): Color {
        val primaryWeight = 1f - secondaryWeight
        return Color(
            (primary.red * primaryWeight + secondary.red * secondaryWeight).roundToInt().coerceIn(0, 255),
            (primary.green * primaryWeight + secondary.green * secondaryWeight).roundToInt().coerceIn(0, 255),
            (primary.blue * primaryWeight + secondary.blue * secondaryWeight).roundToInt().coerceIn(0, 255),
        )
    }

    private fun mixFocusColors(
        fillColor: JBColor,
        borderColor: JBColor,
        accent: Color,
    ): Pair<JBColor, JBColor> = JBColor(
        blendColors(fillColor, accent, 0.28f),
        blendColors(fillColor.darker(), accent.darker(), 0.34f),
    ) to JBColor(
        blendColors(borderColor, accent, 0.46f),
        blendColors(borderColor.darker(), accent.brighter(), 0.42f),
    )

    private fun focusRoleForNode(nodeId: String): FocusVisualRole {
        val focus = roleColorFocus() ?: return FocusVisualRole.NONE
        if (!focus.mode.usesRoleColors()) {
            return FocusVisualRole.NONE
        }
        val isTarget = nodeId in focus.targetNodeIds
        val isCaller = nodeId in focus.callerNodeIds
        val isCallee = nodeId in focus.calleeNodeIds

        return when {
            isTarget -> FocusVisualRole.TARGET
            isCaller && isCallee -> FocusVisualRole.MIXED
            isCaller -> FocusVisualRole.CALLER
            isCallee -> FocusVisualRole.CALLEE
            else -> FocusVisualRole.NONE
        }
    }

    private fun focusRoleForClass(classQualifiedName: String): FocusVisualRole {
        val focus = roleColorFocus() ?: return FocusVisualRole.NONE
        if (!focus.mode.usesRoleColors()) {
            return FocusVisualRole.NONE
        }
        val isTarget = classQualifiedName in focus.targetClasses
        val isCaller = classQualifiedName in focus.callerClasses
        val isCallee = classQualifiedName in focus.calleeClasses

        return when {
            isTarget -> FocusVisualRole.TARGET
            isCaller && isCallee -> FocusVisualRole.MIXED
            isCaller -> FocusVisualRole.CALLER
            isCallee -> FocusVisualRole.CALLEE
            else -> FocusVisualRole.NONE
        }
    }

    private fun roleColorFocus(): PathFocus? =
        when {
            pathFocus?.mode?.usesRoleColors() == true -> pathFocus
            pathFocus?.mode == PathFocusMode.HIGHLIGHT && suspendedPathFocus?.mode?.usesRoleColors() == true ->
                suspendedPathFocus

            else -> null
        }

    private fun groupHeaderStartColor(depth: Int, role: FocusVisualRole, kind: GraphNodeKind): Color {
        if (kind == GraphNodeKind.DATABASE_TABLE) {
            return when (role) {
                FocusVisualRole.TARGET -> colorSettings.targetFocusColor().darker()
                FocusVisualRole.CALLER -> colorSettings.callerFocusColor().darker()
                FocusVisualRole.CALLEE -> colorSettings.calleeFocusColor().darker()
                FocusVisualRole.MIXED -> colorSettings.mixedFocusColor().darker()
                FocusVisualRole.NONE -> colorSettings.tableBorderColor().darker()
            }
        }
        val baseColor = when (role) {
            FocusVisualRole.TARGET -> colorSettings.targetFocusColor().darker()
            FocusVisualRole.CALLER -> colorSettings.callerFocusColor().darker()
            FocusVisualRole.CALLEE -> colorSettings.calleeFocusColor().darker()
            FocusVisualRole.MIXED -> colorSettings.mixedFocusColor().darker()
            FocusVisualRole.NONE -> if (depth == 0) Color(73, 121, 230) else Color(88, 141, 116)
        }
        return baseColor
    }

    private fun groupHeaderEndColor(depth: Int, role: FocusVisualRole, kind: GraphNodeKind): Color {
        if (kind == GraphNodeKind.DATABASE_TABLE) {
            return when (role) {
                FocusVisualRole.TARGET -> colorSettings.targetFocusColor()
                FocusVisualRole.CALLER -> colorSettings.callerFocusColor()
                FocusVisualRole.CALLEE -> colorSettings.calleeFocusColor()
                FocusVisualRole.MIXED -> colorSettings.mixedFocusColor()
                FocusVisualRole.NONE -> colorSettings.tableBorderColor()
            }
        }
        val baseColor = when (role) {
            FocusVisualRole.TARGET -> colorSettings.targetFocusColor()
            FocusVisualRole.CALLER -> colorSettings.callerFocusColor()
            FocusVisualRole.CALLEE -> colorSettings.calleeFocusColor()
            FocusVisualRole.MIXED -> colorSettings.mixedFocusColor()
            FocusVisualRole.NONE -> if (depth == 0) Color(105, 150, 248) else Color(110, 171, 138)
        }
        return baseColor
    }

    private fun paintActionButton(g: Graphics2D, bounds: Rectangle, relevant: Boolean) {
        g.color = if (relevant) {
            JBColor(Color(255, 255, 255, 220), Color(255, 255, 255, 70))
        } else {
            JBColor(Color(255, 255, 255, 180), Color(255, 255, 255, 45))
        }
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8)
        g.color = JBColor(Color(86, 109, 148), Color(195, 210, 230))
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8)
        g.font = font.deriveFont(Font.BOLD, font.size2D - 1)
        g.drawString(PATH_BUTTON_TEXT, bounds.x + 4, bounds.y + bounds.height - 4)
    }

    private fun fitText(g: Graphics2D, text: String, maxWidth: Int): String {
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

    private fun toggleHighlight(target: FocusTarget) {
        if (pathFocus?.target == target && pathFocus?.mode == PathFocusMode.HIGHLIGHT) {
            val restoredFocus = suspendedPathFocus
            suspendedPathFocus = null
            if (restoredFocus != null) {
                pathFocus = restoredFocus
                refreshSelectionFocus()
                rebuildLayout()
                revalidate()
                repaint()
            } else {
                clearPathFocus()
            }
            return
        }
        if (pathFocus != null && pathFocus?.mode != PathFocusMode.HIGHLIGHT) {
            suspendedPathFocus = pathFocus
        }
        setPathFocus(target, PathFocusMode.HIGHLIGHT)
    }

    private fun setPathFocus(target: FocusTarget, mode: PathFocusMode) {
        val currentGraph = currentSourceGraph() ?: return
        if (mode != PathFocusMode.HIGHLIGHT) {
            suspendedPathFocus = null
        }
        if (mode.requiresExpansion()) {
            loadExpandedPathFocus(currentGraph, target, mode)
            return
        }

        pathFocus = buildPathFocus(currentGraph, target, mode)?.copy(
            expandedGraph = if (currentGraph !== graph) currentGraph else null,
        )
        refreshSelectionFocus()
        rebuildLayout()
        revalidate()
        repaint()
    }

    private fun clearPathFocus() {
        pathFocus = null
        suspendedPathFocus = null
        refreshSelectionFocus()
        rebuildLayout()
        revalidate()
        repaint()
    }

    private fun toggleGroupCollapse(classQualifiedName: String) {
        if (!collapsedGroups.add(classQualifiedName)) {
            collapsedGroups.remove(classQualifiedName)
        }
        rebuildLayout()
        revalidate()
        repaint()
    }

    private fun currentSourceGraph(): MethodCallGraph? =
        pathFocus?.expandedGraph ?: graph

    private fun toggleDatabaseFieldExpansion(fieldNodeId: String) {
        if (!expandedDatabaseFields.add(fieldNodeId)) {
            expandedDatabaseFields.remove(fieldNodeId)
        }
        rebuildLayout()
        revalidate()
        repaint()
    }

    private fun setTableFieldsExpanded(tableClassQualifiedName: String, expand: Boolean) {
        val currentGraph = displayGraph() ?: return
        val fieldNodeIds = currentGraph.nodes
            .filter {
                it.classQualifiedName == tableClassQualifiedName &&
                    it.nodeKind == GraphNodeKind.DATABASE_COLUMN
            }
            .map { it.id }
        if (expand) {
            expandedDatabaseFields.addAll(fieldNodeIds)
        } else {
            expandedDatabaseFields.removeAll(fieldNodeIds.toSet())
        }
        rebuildLayout()
        revalidate()
        repaint()
    }

    private fun refreshSelectionFocus() {
        if (!isClickHighlightEnabled()) {
            selectionFocus = null
            return
        }
        val currentDisplayGraph = displayGraph()
        selectionFocus = currentDisplayGraph?.let { graphForSelection ->
            selectionFocus?.target
                ?.takeIf { it.resolve(graphForSelection).isNotEmpty() }
                ?.let { buildSelectionFocus(graphForSelection, it) }
        }
    }

    private fun isClickHighlightEnabled(): Boolean =
        graph?.options?.enableClickHighlight ?: true

    private fun buildPathFocus(
        currentGraph: MethodCallGraph,
        target: FocusTarget,
        mode: PathFocusMode,
    ): PathFocus? {
        val targetNodeIds = target.resolve(currentGraph)
        if (targetNodeIds.isEmpty()) {
            return null
        }

        val forward = currentGraph.edges.groupBy { it.fromNodeId }
        val reverse = currentGraph.edges.groupBy { it.toNodeId }
        val upstream = traverse(targetNodeIds, reverse, reverseMode = true)
        val downstream = traverse(targetNodeIds, forward, reverseMode = false)
        val rawHighlightedNodeIds = when (mode) {
            PathFocusMode.HIGHLIGHT, PathFocusMode.COLORED_HIGHLIGHT, PathFocusMode.FILTER, PathFocusMode.FULL_CHAIN_HIGHLIGHT, PathFocusMode.FULL_CHAIN_FILTER ->
                upstream + downstream

            PathFocusMode.CALLERS_FILTER, PathFocusMode.CALLERS_HIGHLIGHT -> upstream
            PathFocusMode.CALLEES_FILTER, PathFocusMode.CALLEES_HIGHLIGHT -> downstream
        }
        val highlightedNodeIds = includeDatabaseContainerNodes(currentGraph, rawHighlightedNodeIds)
        val highlightedEdges = currentGraph.edges
            .filter { edge ->
                when (mode) {
                    PathFocusMode.CALLERS_FILTER, PathFocusMode.CALLERS_HIGHLIGHT ->
                        edge.fromNodeId in upstream && edge.toNodeId in upstream

                    PathFocusMode.CALLEES_FILTER, PathFocusMode.CALLEES_HIGHLIGHT ->
                        edge.fromNodeId in downstream && edge.toNodeId in downstream

                    PathFocusMode.HIGHLIGHT, PathFocusMode.COLORED_HIGHLIGHT, PathFocusMode.FILTER, PathFocusMode.FULL_CHAIN_HIGHLIGHT, PathFocusMode.FULL_CHAIN_FILTER ->
                        (edge.fromNodeId in upstream && edge.toNodeId in upstream) ||
                            (edge.fromNodeId in downstream && edge.toNodeId in downstream)
                }
            }
            .map { it.key() }
            .toSet()
        val highlightedClasses = currentGraph.nodes
            .filter { it.id in highlightedNodeIds }
            .map { it.classQualifiedName }
            .toSet()
        val callerNodeIds = upstream - targetNodeIds
        val calleeNodeIds = when (mode) {
            PathFocusMode.CALLERS_FILTER, PathFocusMode.CALLERS_HIGHLIGHT -> emptySet()
            PathFocusMode.CALLEES_FILTER, PathFocusMode.CALLEES_HIGHLIGHT -> downstream - targetNodeIds
            PathFocusMode.HIGHLIGHT, PathFocusMode.COLORED_HIGHLIGHT, PathFocusMode.FILTER, PathFocusMode.FULL_CHAIN_HIGHLIGHT, PathFocusMode.FULL_CHAIN_FILTER ->
                downstream - targetNodeIds
        }
        val effectiveCallerNodeIds = when (mode) {
            PathFocusMode.CALLEES_FILTER, PathFocusMode.CALLEES_HIGHLIGHT -> emptySet()
            else -> callerNodeIds
        }
        val targetClasses = currentGraph.nodes
            .filter { it.id in targetNodeIds }
            .map { it.classQualifiedName }
            .toSet()
        val callerClasses = currentGraph.nodes
            .filter { it.id in effectiveCallerNodeIds }
            .map { it.classQualifiedName }
            .toSet()
        val calleeClasses = currentGraph.nodes
            .filter { it.id in calleeNodeIds }
            .map { it.classQualifiedName }
            .toSet()

        return PathFocus(
            target = target,
            mode = mode,
            highlightedNodeIds = highlightedNodeIds,
            highlightedEdges = highlightedEdges,
            highlightedClasses = highlightedClasses,
            targetNodeIds = targetNodeIds,
            callerNodeIds = effectiveCallerNodeIds,
            calleeNodeIds = calleeNodeIds,
            targetClasses = targetClasses,
            callerClasses = callerClasses,
            calleeClasses = calleeClasses,
            expandedGraph = null,
        )
    }

    private fun traverse(
        startIds: Set<String>,
        adjacency: Map<String, List<GraphEdge>>,
        reverseMode: Boolean,
    ): Set<String> {
        val visited = linkedSetOf<String>()
        val queue = ArrayDeque(startIds)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) {
                continue
            }

            adjacency[current].orEmpty().forEach { edge ->
                val next = if (reverseMode) edge.fromNodeId else edge.toNodeId
                if (!visited.contains(next)) {
                    queue.addLast(next)
                }
            }
        }
        return visited
    }

    private fun includeDatabaseContainerNodes(
        currentGraph: MethodCallGraph,
        nodeIds: Set<String>,
    ): Set<String> {
        if (nodeIds.isEmpty()) {
            return nodeIds
        }

        val nodesById = currentGraph.nodes.associateBy { it.id }
        val expandedNodeIds = linkedSetOf<String>().apply { addAll(nodeIds) }
        nodeIds
            .asSequence()
            .mapNotNull(nodesById::get)
            .forEach { node ->
                when (node.nodeKind) {
                    GraphNodeKind.DATABASE_TABLE -> Unit

                    GraphNodeKind.DATABASE_COLUMN,
                    GraphNodeKind.DATABASE_COLUMN_OPERATION -> {
                        currentGraph.nodes
                            .firstOrNull {
                                it.nodeKind == GraphNodeKind.DATABASE_TABLE &&
                                    it.classQualifiedName == node.classQualifiedName
                            }
                            ?.id
                            ?.let(expandedNodeIds::add)
                    }

                    GraphNodeKind.METHOD -> Unit
                }
            }
        return expandedNodeIds
    }

    private fun displayGraph(): MethodCallGraph? {
        val currentGraph = graph ?: return null
        val focus = pathFocus ?: return currentGraph
        val focusGraph = focus.expandedGraph ?: currentGraph
        if (focus.mode.isHighlightMode()) {
            return focusGraph
        }

        return focusGraph.copy(
            nodes = focusGraph.nodes.filter { it.id in focus.highlightedNodeIds },
            edges = focusGraph.edges.filter { it.key() in focus.highlightedEdges },
        )
    }

    private fun showContextMenu(target: InteractionTarget, x: Int, y: Int) {
        val menu = JPopupMenu()
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.highlightPath")).apply {
            addActionListener { toggleContextHighlight(target.focusTarget) }
        })
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.showOnlyPath")).apply {
            addActionListener { setPathFocus(target.focusTarget, PathFocusMode.FILTER) }
        })
        if (target is InteractionTarget.DatabaseTarget) {
            menu.add(JMenuItem(MyBundle.message("toolWindow.menu.highlightFullChain")).apply {
                addActionListener { setPathFocus(target.focusTarget, PathFocusMode.FULL_CHAIN_HIGHLIGHT) }
            })
            menu.add(JMenuItem(MyBundle.message("toolWindow.menu.showOnlyFullChain")).apply {
                addActionListener { setPathFocus(target.focusTarget, PathFocusMode.FULL_CHAIN_FILTER) }
            })
            if (target.node.nodeKind == GraphNodeKind.DATABASE_COLUMN) {
                menu.addSeparator()
                val expanded = expandedDatabaseFields.contains(target.node.id)
                val messageKey = if (expanded) {
                    "toolWindow.menu.collapseFieldActions"
                } else {
                    "toolWindow.menu.expandFieldActions"
                }
                menu.add(JMenuItem(MyBundle.message(messageKey)).apply {
                    addActionListener { toggleDatabaseFieldExpansion(target.node.id) }
                })
            }
            if (target.node.nodeKind == GraphNodeKind.DATABASE_TABLE) {
                menu.addSeparator()
                val expandedFields = displayGraph()
                    ?.nodes
                    .orEmpty()
                    .filter {
                        it.classQualifiedName == target.node.classQualifiedName &&
                            it.nodeKind == GraphNodeKind.DATABASE_COLUMN
                    }
                    .map { it.id }
                val allExpanded = expandedFields.isNotEmpty() && expandedFields.all(expandedDatabaseFields::contains)
                val fieldsMessageKey = if (allExpanded) {
                    "toolWindow.menu.collapseAllFieldActions"
                } else {
                    "toolWindow.menu.expandAllFieldActions"
                }
                menu.add(JMenuItem(MyBundle.message(fieldsMessageKey)).apply {
                    addActionListener { setTableFieldsExpanded(target.node.classQualifiedName, !allExpanded) }
                })
                val collapseMessageKey = if (collapsedGroups.contains(target.node.classQualifiedName)) {
                    "toolWindow.menu.expandTable"
                } else {
                    "toolWindow.menu.collapseTable"
                }
                menu.add(JMenuItem(MyBundle.message(collapseMessageKey)).apply {
                    addActionListener { toggleGroupCollapse(target.node.classQualifiedName) }
                })
            }
            if (pathFocus != null) {
                menu.addSeparator()
                menu.add(JMenuItem(MyBundle.message("toolWindow.menu.clearPathFocus")).apply {
                    addActionListener { clearPathFocus() }
                })
            }
            menu.show(this, x, y)
            return
        }
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.highlightFullChain")).apply {
            addActionListener { setPathFocus(target.focusTarget, PathFocusMode.FULL_CHAIN_HIGHLIGHT) }
        })
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.showOnlyFullChain")).apply {
            addActionListener { setPathFocus(target.focusTarget, PathFocusMode.FULL_CHAIN_FILTER) }
        })
        menu.addSeparator()
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.highlightCallersPath")).apply {
            addActionListener { setPathFocus(target.focusTarget, PathFocusMode.CALLERS_HIGHLIGHT) }
        })
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.showCallersPath")).apply {
            addActionListener { setPathFocus(target.focusTarget, PathFocusMode.CALLERS_FILTER) }
        })
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.highlightCalleesPath")).apply {
            addActionListener { setPathFocus(target.focusTarget, PathFocusMode.CALLEES_HIGHLIGHT) }
        })
        menu.add(JMenuItem(MyBundle.message("toolWindow.menu.showCalleesPath")).apply {
            addActionListener { setPathFocus(target.focusTarget, PathFocusMode.CALLEES_FILTER) }
        })
        menu.addSeparator()
        if (target is InteractionTarget.MethodTarget) {
            menu.add(JMenuItem(MyBundle.message("toolWindow.menu.sequenceAnalysis")).apply {
                addActionListener {
                    val method = target.node.pointer?.element as? com.intellij.psi.PsiMethod ?: return@addActionListener
                    onSequenceAnalysisRequested?.invoke(method)
                }
            })
            menu.addSeparator()
        }
        if (target is InteractionTarget.ClassTarget) {
            menu.add(JMenuItem(MyBundle.message("toolWindow.menu.focusOnThisClass")).apply {
                addActionListener { focusOnClass(target.classQualifiedName) }
            })
            val collapseMessageKey = if (collapsedGroups.contains(target.classQualifiedName)) {
                "toolWindow.menu.expandClass"
            } else {
                "toolWindow.menu.collapseClass"
            }
            menu.add(JMenuItem(MyBundle.message(collapseMessageKey)).apply {
                addActionListener { toggleGroupCollapse(target.classQualifiedName) }
            })
        }
        if (pathFocus != null) {
            menu.addSeparator()
            menu.add(JMenuItem(MyBundle.message("toolWindow.menu.clearPathFocus")).apply {
                addActionListener { clearPathFocus() }
            })
        }
        menu.show(this, x, y)
    }

    private fun focusOnClass(classQualifiedName: String) {
        val psiClass = ReadAction.compute<com.intellij.psi.PsiClass?, RuntimeException> {
            currentSourceGraph()
                ?.nodes
                ?.firstOrNull { it.classQualifiedName == classQualifiedName }
                ?.pointer
                ?.element
                ?.let { it as? com.intellij.psi.PsiMethod }
                ?.containingClass
        } ?: return
        graphDataService.analyzeClass(psiClass)
    }

    private fun loadExpandedPathFocus(
        currentGraph: MethodCallGraph,
        target: FocusTarget,
        mode: PathFocusMode,
    ) {
        val requestId = focusRequestId.incrementAndGet()
        cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)

        ReadAction
            .nonBlocking<PathFocus?> {
                val focusGraph = buildFocusGraph(currentGraph, target, mode)
                buildPathFocus(focusGraph, target, mode)?.copy(
                    expandedGraph = if (focusGraph !== currentGraph) focusGraph else null,
                )
            }
            .expireWith(project)
            .coalesceBy(this, currentGraph.rootClassQualifiedName, target, mode)
            .finishOnUiThread(ModalityState.any()) { nextFocus ->
                if (requestId != focusRequestId.get()) {
                    return@finishOnUiThread
                }
                cursor = Cursor.getDefaultCursor()
                pathFocus = nextFocus
                refreshSelectionFocus()
                rebuildLayout()
                revalidate()
                repaint()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun buildFocusGraph(
        currentGraph: MethodCallGraph,
        target: FocusTarget,
        mode: PathFocusMode,
    ): MethodCallGraph {
        if (!mode.requiresExpansion()) {
            return currentGraph
        }

        val targetMethods = resolveExpansionMethods(currentGraph, target)
        if (targetMethods.isEmpty()) {
            return currentGraph
        }

        return analyzer.expandGraph(
            baseGraph = currentGraph,
            targetMethods = targetMethods,
            options = currentGraph.options,
            direction = mode.toExpansionDirection(),
        )
    }

    private fun resolveExpansionMethods(
        currentGraph: MethodCallGraph,
        target: FocusTarget,
    ): List<com.intellij.psi.PsiMethod> {
        val targetNodeIds = target.resolve(currentGraph)
        if (targetNodeIds.isEmpty()) {
            return emptyList()
        }

        return when (target) {
            is FocusTarget.Method -> {
                val targetNode = currentGraph.nodes.firstOrNull { it.id == target.nodeId } ?: return emptyList()
                (targetNode.pointer?.element as? com.intellij.psi.PsiMethod)?.let(::listOf)
                    ?: if (targetNode.nodeKind == GraphNodeKind.METHOD) {
                        emptyList()
                    } else {
                        resolveDatabaseExpansionMethods(currentGraph, targetNodeIds)
                    }
            }

            is FocusTarget.Class -> {
                val targetNodes = currentGraph.nodes.filter { it.id in targetNodeIds }
                val containingClass = targetNodes
                    .firstNotNullOfOrNull { node ->
                        (node.pointer?.element as? com.intellij.psi.PsiMethod)?.containingClass
                    }
                when {
                    containingClass != null -> analyzer.collectDisplayableMethods(containingClass, currentGraph.options)
                    targetNodes.any { it.nodeKind != GraphNodeKind.METHOD } ->
                        resolveDatabaseExpansionMethods(currentGraph, targetNodeIds)
                    else -> emptyList()
                }
            }
        }
    }

    private fun resolveDatabaseExpansionMethods(
        currentGraph: MethodCallGraph,
        targetNodeIds: Set<String>,
    ): List<com.intellij.psi.PsiMethod> {
        val nodesById = currentGraph.nodes.associateBy { it.id }
        return currentGraph.edges
            .asSequence()
            .filter { it.toNodeId in targetNodeIds }
            .mapNotNull { edge -> nodesById[edge.fromNodeId] }
            .filter { it.nodeKind == GraphNodeKind.METHOD }
            .distinctBy { it.id }
            .mapNotNull { it.pointer?.element as? com.intellij.psi.PsiMethod }
            .toList()
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
            maxOf((basePreferredSize.width * zoomFactor).roundToInt(), 1),
            maxOf((basePreferredSize.height * zoomFactor).roundToInt(), 1),
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

    private fun toGraphPoint(point: Point): Point =
        Point(
            (point.x / zoomFactor).roundToInt(),
            (point.y / zoomFactor).roundToInt(),
        )

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

    private fun viewport(): JViewport? =
        SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport

    private fun isNodeRelevant(nodeId: String): Boolean =
        when {
            selectionFocus != null -> selectionFocus?.highlightedNodeIds?.contains(nodeId) == true
            pathFocus?.mode?.isHighlightMode() == true -> pathFocus?.highlightedNodeIds?.contains(nodeId) == true
            else -> true
        }

    private fun isClassRelevant(classQualifiedName: String): Boolean =
        when {
            selectionFocus != null -> selectionFocus?.highlightedClasses?.contains(classQualifiedName) == true
            pathFocus?.mode?.isHighlightMode() == true -> pathFocus?.highlightedClasses?.contains(classQualifiedName) == true
            else -> true
        }

    private fun shouldDimNode(nodeId: String): Boolean =
        (pathFocus?.mode?.isHighlightMode() == true || selectionFocus != null) && !isNodeRelevant(nodeId)

    private fun shouldDimClass(classQualifiedName: String): Boolean =
        (pathFocus?.mode?.isHighlightMode() == true || selectionFocus != null) && !isClassRelevant(classQualifiedName)

    private fun isNodeSelected(nodeId: String): Boolean =
        selectionFocus?.highlightedNodeIds?.contains(nodeId) == true

    private fun isClassSelected(classQualifiedName: String): Boolean =
        selectionFocus?.highlightedClasses?.contains(classQualifiedName) == true

    private fun groupHeaderHasAction(group: ClassGroupLayout): Boolean =
        group.kind == GraphNodeKind.METHOD || group.headerNode != null

    private fun allNodeLayouts(group: ClassGroupLayout): Sequence<NodeLayout> =
        if (group.kind == GraphNodeKind.DATABASE_TABLE) {
            group.databaseFieldLayouts.asSequence().flatMap { field ->
                sequence {
                    yield(field.fieldLayout)
                    yieldAll(field.operationLayouts)
                }
            }
        } else {
            group.nodeLayouts.asSequence()
        }

    private fun isEdgeVisible(currentGraph: MethodCallGraph, edge: GraphEdge): Boolean {
        val nodeById = currentGraph.nodes.associateBy { it.id }
        val targetNode = nodeById[edge.toNodeId] ?: return false
        return when (targetNode.nodeKind) {
            GraphNodeKind.DATABASE_COLUMN ->
                !expandedDatabaseFields.contains(targetNode.id) ||
                    currentGraph.nodes.none {
                        it.nodeKind == GraphNodeKind.DATABASE_COLUMN_OPERATION &&
                            it.classQualifiedName == targetNode.classQualifiedName &&
                            it.columnName == targetNode.columnName
                    }

            GraphNodeKind.DATABASE_COLUMN_OPERATION ->
                currentGraph.nodes
                    .firstOrNull {
                        it.nodeKind == GraphNodeKind.DATABASE_COLUMN &&
                            it.classQualifiedName == targetNode.classQualifiedName &&
                            it.columnName == targetNode.columnName
                    }
                    ?.id
                    ?.let { fieldNodeId -> expandedDatabaseFields.contains(fieldNodeId) }
                    ?: false

            GraphNodeKind.METHOD,
            GraphNodeKind.DATABASE_TABLE -> true
        }
    }

    private fun databaseTooltip(node: GraphNode): String = when (node.nodeKind) {
        GraphNodeKind.DATABASE_TABLE ->
            MyBundle.message("toolWindow.tooltip.tableNode", node.tableName ?: node.displaySignature, node.sourceCount)

        GraphNodeKind.DATABASE_COLUMN ->
            MyBundle.message(
                "toolWindow.tooltip.columnNode",
                node.tableName ?: node.className,
                node.columnName ?: node.displaySignature,
                node.sourceCount,
            )

        GraphNodeKind.DATABASE_COLUMN_OPERATION ->
            "${node.tableName ?: node.className}.${node.columnName ?: "unknown"}.${node.displaySignature}"

        GraphNodeKind.METHOD -> "${node.className}.${node.displaySignature}"
    }

    private fun nodeInteractionTarget(node: GraphNode): InteractionTarget = when (node.nodeKind) {
        GraphNodeKind.METHOD -> InteractionTarget.MethodTarget(node)
        GraphNodeKind.DATABASE_TABLE,
        GraphNodeKind.DATABASE_COLUMN,
        GraphNodeKind.DATABASE_COLUMN_OPERATION -> InteractionTarget.DatabaseTarget(node)
    }

    private fun findInteractionTarget(point: Point): InteractionTarget? =
        findNodeActionAt(point)
            ?: findGroupActionAt(point)
            ?: findNodeAt(point)?.node?.let(::nodeInteractionTarget)
            ?: findHeaderAt(point)?.let {
                when (it.kind) {
                    GraphNodeKind.METHOD -> InteractionTarget.ClassTarget(it.classQualifiedName, it.className)
                    GraphNodeKind.DATABASE_TABLE -> it.headerNode?.let(::nodeInteractionTarget)
                    GraphNodeKind.DATABASE_COLUMN,
                    GraphNodeKind.DATABASE_COLUMN_OPERATION -> null
                }
            }

    private fun findHeaderAt(point: Point): ClassGroupLayout? =
        groupLayouts.firstOrNull { it.headerBounds.contains(point) }

    private fun findGroupActionAt(point: Point): InteractionTarget? =
        groupLayouts.firstOrNull { groupHeaderHasAction(it) && it.actionBounds.contains(point) }
            ?.let { group ->
                when (group.kind) {
                    GraphNodeKind.METHOD -> InteractionTarget.ClassTarget(group.classQualifiedName, group.className)
                    GraphNodeKind.DATABASE_TABLE -> group.headerNode?.let(::nodeInteractionTarget)
                    GraphNodeKind.DATABASE_COLUMN,
                    GraphNodeKind.DATABASE_COLUMN_OPERATION -> null
                }
            }

    private fun findNodeAt(point: Point): NodeLayout? =
        groupLayouts.asSequence()
            .flatMap(::allNodeLayouts)
            .firstOrNull { it.bounds.contains(point) }

    private fun findNodeActionAt(point: Point): InteractionTarget? =
        groupLayouts.asSequence()
            .flatMap(::allNodeLayouts)
            .firstOrNull { it.actionBounds.contains(point) }
            ?.let { nodeInteractionTarget(it.node) }

    private fun nodeTypeRank(nodeType: GraphNodeType): Int = when (nodeType) {
        GraphNodeType.ROOT -> 0
        GraphNodeType.REACHABLE -> 1
        GraphNodeType.SUPPLEMENTAL -> 2
        GraphNodeType.DATABASE_TABLE -> 3
        GraphNodeType.DATABASE_COLUMN -> 4
        GraphNodeType.DATABASE_COLUMN_OPERATION -> 5
    }

    private fun databaseActionRank(action: String?): Int = when (action) {
        "select" -> 0
        "insert" -> 1
        "update" -> 2
        "where" -> 3
        "join" -> 4
        "group_by" -> 5
        "order_by" -> 6
        "having" -> 7
        else -> 99
    }

    private fun isSameColumn(sourceBounds: Rectangle, targetBounds: Rectangle): Boolean =
        sourceBounds.x == targetBounds.x

    private fun GraphEdge.key(): EdgeKey = EdgeKey(fromNodeId, toNodeId)

    private data class ClassBucket(
        val groupKey: String,
        val classQualifiedName: String,
        val className: String,
        val depth: Int,
        val kind: GraphNodeKind,
        val headerNode: GraphNode?,
        val nodes: List<GraphNode>,
    )

    private data class ClassGroupLayout(
        val groupKey: String,
        val classQualifiedName: String,
        val className: String,
        val depth: Int,
        val kind: GraphNodeKind,
        val headerNode: GraphNode?,
        val cardBounds: Rectangle,
        val headerBounds: Rectangle,
        val actionBounds: Rectangle,
        val nodeLayouts: List<NodeLayout>,
        val databaseFieldLayouts: List<DatabaseFieldLayout>,
        val collapsed: Boolean,
    )

    private data class DatabaseFieldLayout(
        val fieldLayout: NodeLayout,
        val cardBounds: Rectangle,
        val operationLayouts: List<NodeLayout>,
        val expanded: Boolean,
    )

    private data class NodeLayout(
        val node: GraphNode,
        val bounds: Rectangle,
        val actionBounds: Rectangle,
    )

    private data class EdgeKey(
        val from: String,
        val to: String,
    )

    private data class PathFocus(
        val target: FocusTarget,
        val mode: PathFocusMode,
        val highlightedNodeIds: Set<String>,
        val highlightedEdges: Set<EdgeKey>,
        val highlightedClasses: Set<String>,
        val targetNodeIds: Set<String>,
        val callerNodeIds: Set<String>,
        val calleeNodeIds: Set<String>,
        val targetClasses: Set<String>,
        val callerClasses: Set<String>,
        val calleeClasses: Set<String>,
        val expandedGraph: MethodCallGraph?,
    )

    private data class SelectionFocus(
        val target: FocusTarget,
        val highlightedNodeIds: Set<String>,
        val highlightedEdges: Set<EdgeKey>,
        val highlightedClasses: Set<String>,
    )

    private data class ModuleEdgeState(
        val highlighted: Boolean,
        val selected: Boolean,
    )

    private enum class PathFocusMode {
        HIGHLIGHT,
        COLORED_HIGHLIGHT,
        FILTER,
        FULL_CHAIN_HIGHLIGHT,
        FULL_CHAIN_FILTER,
        CALLERS_HIGHLIGHT,
        CALLERS_FILTER,
        CALLEES_HIGHLIGHT,
        CALLEES_FILTER,
        ;

        fun isHighlightMode(): Boolean = this == HIGHLIGHT ||
            this == COLORED_HIGHLIGHT ||
            this == FULL_CHAIN_HIGHLIGHT ||
            this == CALLERS_HIGHLIGHT ||
            this == CALLEES_HIGHLIGHT

        fun usesRoleColors(): Boolean = this == COLORED_HIGHLIGHT ||
            this == FULL_CHAIN_HIGHLIGHT ||
            this == FULL_CHAIN_FILTER ||
            this == CALLERS_HIGHLIGHT ||
            this == CALLERS_FILTER ||
            this == CALLEES_HIGHLIGHT ||
            this == CALLEES_FILTER

        fun requiresExpansion(): Boolean = this == FULL_CHAIN_HIGHLIGHT ||
            this == FULL_CHAIN_FILTER

        fun toExpansionDirection(): MethodCallAnalyzer.ExpansionDirection = when (this) {
            FULL_CHAIN_HIGHLIGHT, FULL_CHAIN_FILTER -> MethodCallAnalyzer.ExpansionDirection.BOTH
            CALLERS_HIGHLIGHT, CALLERS_FILTER -> MethodCallAnalyzer.ExpansionDirection.CALLERS
            CALLEES_HIGHLIGHT, CALLEES_FILTER -> MethodCallAnalyzer.ExpansionDirection.CALLEES
            HIGHLIGHT, COLORED_HIGHLIGHT, FILTER -> MethodCallAnalyzer.ExpansionDirection.BOTH
        }
    }

    private enum class FocusVisualRole {
        NONE,
        TARGET,
        CALLER,
        CALLEE,
        MIXED,
    }

    private sealed class FocusTarget {
        abstract fun resolve(graph: MethodCallGraph): Set<String>

        data class Method(val nodeId: String) : FocusTarget() {
            override fun resolve(graph: MethodCallGraph): Set<String> =
                graph.nodes
                    .firstOrNull { it.id == nodeId }
                    ?.let { node ->
                        when (node.nodeKind) {
                            GraphNodeKind.DATABASE_COLUMN ->
                                graph.nodes
                                    .filter {
                                        it.id == nodeId ||
                                            (
                                                it.nodeKind == GraphNodeKind.DATABASE_COLUMN_OPERATION &&
                                                    it.classQualifiedName == node.classQualifiedName &&
                                                    it.columnName == node.columnName
                                                )
                                    }
                                    .map { it.id }
                                    .toSet()

                            GraphNodeKind.DATABASE_COLUMN_OPERATION ->
                                graph.nodes
                                    .filter {
                                        it.id == nodeId ||
                                            (
                                                it.nodeKind == GraphNodeKind.DATABASE_COLUMN &&
                                                    it.classQualifiedName == node.classQualifiedName &&
                                                    it.columnName == node.columnName
                                                )
                                    }
                                    .map { it.id }
                                    .toSet()

                            GraphNodeKind.METHOD,
                            GraphNodeKind.DATABASE_TABLE -> setOf(node.id)
                        }
                    }
                    .orEmpty()
        }

        data class Class(val classQualifiedName: String) : FocusTarget() {
            override fun resolve(graph: MethodCallGraph): Set<String> =
                graph.nodes
                    .filter { it.classQualifiedName == classQualifiedName }
                    .map { it.id }
                    .toSet()
        }
    }

    private sealed class InteractionTarget(
        val focusTarget: FocusTarget,
        val tooltip: String,
    ) {
        class MethodTarget(node: GraphNode) : InteractionTarget(
            focusTarget = FocusTarget.Method(node.id),
            tooltip = MyBundle.message("toolWindow.tooltip.highlightMethod", node.className, node.displaySignature),
        ) {
            val node: GraphNode = node
        }

        class DatabaseTarget(node: GraphNode) : InteractionTarget(
            focusTarget = if (node.nodeKind == GraphNodeKind.DATABASE_TABLE) {
                FocusTarget.Class(node.classQualifiedName)
            } else {
                FocusTarget.Method(node.id)
            },
            tooltip = when (node.nodeKind) {
                GraphNodeKind.DATABASE_TABLE ->
                    MyBundle.message("toolWindow.tooltip.tableNode", node.tableName ?: node.displaySignature, node.sourceCount)

                GraphNodeKind.DATABASE_COLUMN ->
                    MyBundle.message(
                        "toolWindow.tooltip.columnNode",
                        node.tableName ?: node.className,
                        node.columnName ?: node.displaySignature,
                        node.sourceCount,
                    )

                GraphNodeKind.DATABASE_COLUMN_OPERATION ->
                    "${node.tableName ?: node.className}.${node.columnName ?: "unknown"}.${node.displaySignature}"

                GraphNodeKind.METHOD ->
                    MyBundle.message("toolWindow.tooltip.highlightMethod", node.className, node.displaySignature)
            },
        ) {
            val node: GraphNode = node
        }

        class ClassTarget(classQualifiedName: String, className: String) : InteractionTarget(
            focusTarget = FocusTarget.Class(classQualifiedName),
            tooltip = MyBundle.message("toolWindow.tooltip.highlightClass", className),
        ) {
            val classQualifiedName: String = classQualifiedName
        }
    }

    private data class ArrowOverlay(
        val endX: Float,
        val endY: Float,
        val direction: ArrowDirection,
        val color: Color,
        val alpha: Float,
        val emphasize: Boolean,
    )

    private enum class ArrowDirection {
        LEFT,
        RIGHT,
        UP,
        DOWN,
    }

    private companion object {
        const val PADDING = 20
        const val CARD_WIDTH = 280
        const val COLUMN_GAP = 56
        const val ROW_GAP = 18
        const val HEADER_HEIGHT = 34
        const val NODE_HEIGHT = 28
        const val NODE_GAP = 8
        const val INNER_PADDING = 12
        const val FIELD_CARD_INNER_PADDING = 8
        const val FIELD_OPERATION_GAP = 6
        const val ACTION_BUTTON_SIZE = 16
        const val ZOOM_STEP = 0.15
        const val MIN_ZOOM = 0.5
        const val MAX_ZOOM = 2.5
        const val DRAG_THRESHOLD = 4
        const val DIMMED_ALPHA = 0.18f
        const val PATH_BUTTON_TEXT = "*"
        const val SAME_COLUMN_BASE_OFFSET = 26
        const val SAME_COLUMN_LANE_GAP = 12
        const val SAME_COLUMN_LANE_COUNT = 3
        const val SAME_COLUMN_CORNER_RADIUS = 10f
        const val SAME_COLUMN_TARGET_APPROACH = 10f
        const val SAME_COLUMN_EXTRA_CANVAS_PADDING = 24
        const val SELF_LOOP_HORIZONTAL_OFFSET = 34f
        const val SELF_LOOP_VERTICAL_SPAN = 18f
        const val SELF_LOOP_CORNER_RADIUS = 8f
        const val SCROLL_UNIT_INCREMENT = 32
    }
}
