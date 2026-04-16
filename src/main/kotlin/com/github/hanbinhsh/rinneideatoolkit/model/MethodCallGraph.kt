package com.github.hanbinhsh.rinneideatoolkit.model

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

data class GraphOptions(
    val showUnreachedMethods: Boolean = false,
    val showAccessorMethods: Boolean = false,
    val showVisibilityColors: Boolean = true,
    val showPrivateMethods: Boolean = true,
    val enableClickHighlight: Boolean = true,
    val showDetailedCallEdges: Boolean = true,
    val showCallsFromUnreachedMethods: Boolean = false,
    val routeSameColumnEdgesOutside: Boolean = true,
    val drawEdgesOnTop: Boolean = false,
    val drawArrowheadsOnTop: Boolean = false,
    val showMapperTables: Boolean = false,
)

enum class GraphNodeType {
    ROOT,
    REACHABLE,
    SUPPLEMENTAL,
    DATABASE_TABLE,
    DATABASE_COLUMN,
    DATABASE_COLUMN_OPERATION,
}

enum class GraphMethodVisibility {
    PUBLIC,
    PRIVATE,
    OTHER,
}

data class GraphNode(
    val id: String,
    val className: String,
    val classQualifiedName: String,
    val methodName: String,
    val displaySignature: String,
    val depth: Int,
    val nodeType: GraphNodeType,
    val visibility: GraphMethodVisibility,
    val isReachable: Boolean,
    val isSupplemental: Boolean,
    val pointer: SmartPsiElementPointer<out PsiElement>?,
    val nodeKind: GraphNodeKind = GraphNodeKind.METHOD,
    val tableName: String? = null,
    val columnName: String? = null,
    val databaseAction: String? = null,
    val sourceCount: Int = 0,
)

enum class GraphNodeKind {
    METHOD,
    DATABASE_TABLE,
    DATABASE_COLUMN,
    DATABASE_COLUMN_OPERATION,
}

data class GraphEdge(
    val fromNodeId: String,
    val toNodeId: String,
)

data class MethodCallGraph(
    val rootClassName: String,
    val rootClassQualifiedName: String,
    val options: GraphOptions,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
)

data class GraphViewState(
    val rootClassQualifiedName: String? = null,
    val rootClassDisplayName: String? = null,
    val options: GraphOptions = GraphOptions(),
    val graph: MethodCallGraph? = null,
    val clearFocus: Boolean = false,
)
