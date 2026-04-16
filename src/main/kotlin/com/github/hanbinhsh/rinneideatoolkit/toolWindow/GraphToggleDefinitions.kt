package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.github.hanbinhsh.rinneideatoolkit.model.ToolbarToggleId

internal data class ToolbarToggleDefinition(
    val id: ToolbarToggleId,
    val messageKey: String,
)

internal val TOOLBAR_TOGGLE_DEFINITIONS = listOf(
    ToolbarToggleDefinition(ToolbarToggleId.SHOW_UNREACHED_METHODS, "toolWindow.showUnreachedMethods"),
    ToolbarToggleDefinition(ToolbarToggleId.SHOW_UNREACHED_CALLS, "toolWindow.showCallsFromUnreachedMethods"),
    ToolbarToggleDefinition(ToolbarToggleId.SHOW_ACCESSOR_METHODS, "toolWindow.showAccessorMethods"),
    ToolbarToggleDefinition(ToolbarToggleId.SHOW_VISIBILITY_COLORS, "toolWindow.showVisibilityColors"),
    ToolbarToggleDefinition(ToolbarToggleId.SHOW_PRIVATE_METHODS, "toolWindow.showPrivateMethods"),
    ToolbarToggleDefinition(ToolbarToggleId.ENABLE_CLICK_HIGHLIGHT, "toolWindow.enableClickHighlight"),
    ToolbarToggleDefinition(ToolbarToggleId.SHOW_DETAILED_CALL_EDGES, "toolWindow.showDetailedCallEdges"),
    ToolbarToggleDefinition(ToolbarToggleId.SHOW_MAPPER_TABLES, "toolWindow.showMapperTables"),
    ToolbarToggleDefinition(ToolbarToggleId.ROUTE_SAME_COLUMN_EDGES_OUTSIDE, "toolWindow.routeSameColumnEdgesOutside"),
    ToolbarToggleDefinition(ToolbarToggleId.DRAW_EDGES_ON_TOP, "toolWindow.drawEdgesOnTop"),
    ToolbarToggleDefinition(ToolbarToggleId.DRAW_ARROWHEADS_ON_TOP, "toolWindow.drawArrowheadsOnTop"),
    ToolbarToggleDefinition(ToolbarToggleId.WHITE_BACKGROUND_FOR_EXPORT, "toolWindow.whiteBackground"),
)
