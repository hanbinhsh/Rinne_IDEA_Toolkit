package com.github.hanbinhsh.rinneideatoolkit.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.github.hanbinhsh.rinneideatoolkit.model.GraphEdge
import com.github.hanbinhsh.rinneideatoolkit.model.GraphMethodVisibility
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNode
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNodeKind
import com.github.hanbinhsh.rinneideatoolkit.model.GraphNodeType
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.MethodCallGraph

@Service(Service.Level.PROJECT)
class MethodCallAnalyzer(private val project: Project) {

    private val fileIndex = ProjectFileIndex.getInstance(project)
    private val pointerManager = SmartPointerManager.getInstance(project)
    private val mapperTableAnalyzer = project.service<MapperTableAnalyzer>()
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)

    fun analyze(rootClass: PsiClass, options: GraphOptions): MethodCallGraph {
        val nodeMap = linkedMapOf<String, MutableGraphNode>()
        val edges = linkedSetOf<GraphEdge>()
        val expandedNodeIds = mutableSetOf<String>()
        val activePath = ArrayDeque<String>()
        val reachableNodeIds = linkedSetOf<String>()
        val reachableClasses = linkedMapOf<String, PsiClass>()
        val classDepths = mutableMapOf<String, Int>()
        val rootClassId = classId(rootClass)

        collectDeclaredMethods(rootClass, options).forEach { method ->
            visitMethod(
                method = method,
                rootClassId = rootClassId,
                visibleDepth = 0,
                nodeMap = nodeMap,
                edges = edges,
                expandedNodeIds = expandedNodeIds,
                activePath = activePath,
                reachableNodeIds = reachableNodeIds,
                reachableClasses = reachableClasses,
                classDepths = classDepths,
                options = options,
                visibleSourceId = null,
            )
        }

        if (options.showUnreachedMethods) {
            reachableClasses.forEach { (classKey, psiClass) ->
                if (classKey == rootClassId) {
                    return@forEach
                }

                val classDepth = classDepths[classKey] ?: 0
                collectDeclaredMethods(psiClass, options).forEach { method ->
                    val methodId = methodId(method)
                    if (reachableNodeIds.contains(methodId)) {
                        return@forEach
                    }
                    val supplementalNodeId = addOrUpdateNode(
                        nodeMap = nodeMap,
                        method = method,
                        depth = classDepth,
                        nodeType = GraphNodeType.SUPPLEMENTAL,
                        isReachable = false,
                        isSupplemental = true,
                    )
                    updateClassDepth(method, classDepth, classDepths)

                    if (options.showCallsFromUnreachedMethods) {
                        visitSupplementalMethod(
                            method = method,
                            visibleDepth = classDepth,
                            nodeMap = nodeMap,
                            edges = edges,
                            expandedNodeIds = expandedNodeIds,
                            activePath = activePath,
                            classDepths = classDepths,
                            options = options,
                            visibleSourceId = supplementalNodeId,
                        )
                    }
                }
            }
        }

        appendMapperTables(nodeMap, edges, options)

        return MethodCallGraph(
            rootClassName = rootClass.name ?: rootClassId,
            rootClassQualifiedName = rootClass.qualifiedName ?: rootClassId,
            options = options,
            nodes = nodeMap.values
                .map { it.toGraphNode() }
                .sortedWith(
                    compareBy<GraphNode> { it.depth }
                        .thenBy { it.classQualifiedName }
                        .thenBy { nodeTypeRank(it.nodeType) }
                        .thenBy { it.displaySignature },
                ),
            edges = edges.toList()
                .sortedWith(compareBy<GraphEdge> { it.fromNodeId }.thenBy { it.toNodeId }),
        )
    }

    fun collectDisplayableMethods(psiClass: PsiClass, options: GraphOptions): List<PsiMethod> =
        collectDeclaredMethods(psiClass, options)

    fun expandGraph(
        baseGraph: MethodCallGraph,
        targetMethods: List<PsiMethod>,
        options: GraphOptions,
        direction: ExpansionDirection,
    ): MethodCallGraph {
        if (targetMethods.isEmpty()) {
            return baseGraph
        }

        val nodeMap = linkedMapOf<String, MutableGraphNode>()
        baseGraph.nodes.forEach { node ->
            nodeMap[node.id] = MutableGraphNode(
                id = node.id,
                className = node.className,
                classQualifiedName = node.classQualifiedName,
                methodName = node.methodName,
                displaySignature = node.displaySignature,
                depth = node.depth,
                nodeType = node.nodeType,
                visibility = node.visibility,
                isReachable = node.isReachable,
                isSupplemental = node.isSupplemental,
                pointer = node.pointer,
                nodeKind = node.nodeKind,
                tableName = node.tableName,
                columnName = node.columnName,
                databaseAction = node.databaseAction,
                sourceCount = node.sourceCount,
            )
        }

        val edges = linkedSetOf<GraphEdge>().apply { addAll(baseGraph.edges) }
        val classDepths = baseGraph.nodes
            .groupBy { it.classQualifiedName }
            .mapValues { (_, nodes) -> nodes.minOf { it.depth } }
            .toMutableMap()
        val expandedCallers = mutableSetOf<String>()
        val expandedCallees = mutableSetOf<String>()
        val activePath = ArrayDeque<String>()

        targetMethods
            .distinctBy(::methodId)
            .forEach { method ->
                val methodKey = methodId(method)
                val classKey = method.containingClass?.let(::classId).orEmpty()
                val targetDepth = nodeMap[methodKey]?.depth
                    ?: classDepths[classKey]
                    ?: 0

                addExpansionNode(
                    nodeMap = nodeMap,
                    method = method,
                    depth = targetDepth,
                    rootClassQualifiedName = baseGraph.rootClassQualifiedName,
                    classDepths = classDepths,
                )

                if (direction.includesCallers) {
                    expandCallers(
                        method = method,
                        visibleDepth = targetDepth,
                        nodeMap = nodeMap,
                        edges = edges,
                        expandedNodeIds = expandedCallers,
                        activePath = activePath,
                        classDepths = classDepths,
                        options = options,
                        visibleTargetId = methodKey,
                        rootClassQualifiedName = baseGraph.rootClassQualifiedName,
                    )
                }
                if (direction.includesCallees) {
                    expandCallees(
                        method = method,
                        visibleDepth = targetDepth,
                        nodeMap = nodeMap,
                        edges = edges,
                        expandedNodeIds = expandedCallees,
                        activePath = activePath,
                        classDepths = classDepths,
                        options = options,
                        visibleSourceId = methodKey,
                        rootClassQualifiedName = baseGraph.rootClassQualifiedName,
                    )
                }
            }

        appendMapperTables(nodeMap, edges, options)

        return baseGraph.copy(
            nodes = nodeMap.values
                .map { it.toGraphNode() }
                .sortedWith(
                    compareBy<GraphNode> { it.depth }
                        .thenBy { it.classQualifiedName }
                        .thenBy { nodeTypeRank(it.nodeType) }
                        .thenBy { it.displaySignature },
                ),
            edges = edges.toList()
                .sortedWith(compareBy<GraphEdge> { it.fromNodeId }.thenBy { it.toNodeId }),
        )
    }

    private fun visitMethod(
        method: PsiMethod,
        rootClassId: String,
        visibleDepth: Int,
        nodeMap: MutableMap<String, MutableGraphNode>,
        edges: MutableSet<GraphEdge>,
        expandedNodeIds: MutableSet<String>,
        activePath: ArrayDeque<String>,
        reachableNodeIds: MutableSet<String>,
        reachableClasses: MutableMap<String, PsiClass>,
        classDepths: MutableMap<String, Int>,
        options: GraphOptions,
        visibleSourceId: String?,
    ) {
        val methodKey = methodId(method)
        val shouldDisplayCurrentMethod = shouldDisplayMethod(method, options)
        val currentVisibleSourceId = if (shouldDisplayCurrentMethod) {
            addReachableNode(
                nodeMap = nodeMap,
                method = method,
                depth = visibleDepth,
                rootClassId = rootClassId,
                reachableNodeIds = reachableNodeIds,
                reachableClasses = reachableClasses,
                classDepths = classDepths,
            )
        } else {
            visibleSourceId
        }

        if (!expandedNodeIds.add(methodKey)) {
            return
        }

        val methodBody = method.body ?: return
        activePath.addLast(methodKey)
        try {
            methodBody.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)

                    resolveCallTargets(expression).forEach { target ->
                        if (target.isConstructor || !isProjectMethod(target)) {
                            return@forEach
                        }

                        val targetMethodId = methodId(target)
                        val targetShouldDisplay = shouldDisplayMethod(target, options)
                        val nextVisibleDepth = if (targetShouldDisplay) visibleDepth + 1 else visibleDepth
                        val edgeSourceId = resolveVisibleInvocationSourceId(
                            expression = expression,
                            currentVisibleSourceId = currentVisibleSourceId,
                            options = options,
                        )
                        val nextVisibleSourceId = if (targetShouldDisplay) {
                            val targetId = addReachableNode(
                                nodeMap = nodeMap,
                                method = target,
                                depth = nextVisibleDepth,
                                rootClassId = rootClassId,
                                reachableNodeIds = reachableNodeIds,
                                reachableClasses = reachableClasses,
                                classDepths = classDepths,
                            )
                            if (edgeSourceId != null) {
                                edges += GraphEdge(edgeSourceId, targetId)
                            }
                            targetId
                        } else {
                            edgeSourceId
                        }

                        if (!activePath.contains(targetMethodId)) {
                            visitMethod(
                                method = target,
                                rootClassId = rootClassId,
                                visibleDepth = nextVisibleDepth,
                                nodeMap = nodeMap,
                                edges = edges,
                                expandedNodeIds = expandedNodeIds,
                                activePath = activePath,
                                reachableNodeIds = reachableNodeIds,
                                reachableClasses = reachableClasses,
                                classDepths = classDepths,
                                options = options,
                                visibleSourceId = nextVisibleSourceId,
                            )
                        }
                    }
                }
            })
        } finally {
            activePath.removeLast()
        }
    }

    private fun addReachableNode(
        nodeMap: MutableMap<String, MutableGraphNode>,
        method: PsiMethod,
        depth: Int,
        rootClassId: String,
        reachableNodeIds: MutableSet<String>,
        reachableClasses: MutableMap<String, PsiClass>,
        classDepths: MutableMap<String, Int>,
    ): String {
        val classKey = method.containingClass?.let(::classId) ?: "<no-class>"
        val nodeType = if (classKey == rootClassId) GraphNodeType.ROOT else GraphNodeType.REACHABLE

        updateClassDepth(method, depth, classDepths)
        method.containingClass?.let { reachableClasses.putIfAbsent(classKey, it) }

        val nodeId = addOrUpdateNode(
            nodeMap = nodeMap,
            method = method,
            depth = depth,
            nodeType = nodeType,
            isReachable = true,
            isSupplemental = false,
        )
        reachableNodeIds += nodeId
        return nodeId
    }

    private fun addSupplementalNode(
        nodeMap: MutableMap<String, MutableGraphNode>,
        method: PsiMethod,
        depth: Int,
        classDepths: MutableMap<String, Int>,
    ): String {
        updateClassDepth(method, depth, classDepths)
        return addOrUpdateNode(
            nodeMap = nodeMap,
            method = method,
            depth = depth,
            nodeType = GraphNodeType.SUPPLEMENTAL,
            isReachable = false,
            isSupplemental = true,
        )
    }

    private fun addExpansionNode(
        nodeMap: MutableMap<String, MutableGraphNode>,
        method: PsiMethod,
        depth: Int,
        rootClassQualifiedName: String,
        classDepths: MutableMap<String, Int>,
    ): String {
        updateClassDepth(method, depth, classDepths)
        val nodeType = if ((method.containingClass?.qualifiedName ?: "") == rootClassQualifiedName) {
            GraphNodeType.ROOT
        } else {
            GraphNodeType.REACHABLE
        }
        return addOrUpdateNode(
            nodeMap = nodeMap,
            method = method,
            depth = depth,
            nodeType = nodeType,
            isReachable = true,
            isSupplemental = false,
        )
    }

    private fun visitSupplementalMethod(
        method: PsiMethod,
        visibleDepth: Int,
        nodeMap: MutableMap<String, MutableGraphNode>,
        edges: MutableSet<GraphEdge>,
        expandedNodeIds: MutableSet<String>,
        activePath: ArrayDeque<String>,
        classDepths: MutableMap<String, Int>,
        options: GraphOptions,
        visibleSourceId: String?,
    ) {
        val methodKey = methodId(method)
        val shouldDisplayCurrentMethod = shouldDisplayMethod(method, options)
        val currentVisibleSourceId = if (shouldDisplayCurrentMethod) {
            addSupplementalNode(
                nodeMap = nodeMap,
                method = method,
                depth = visibleDepth,
                classDepths = classDepths,
            )
        } else {
            visibleSourceId
        }

        if (!expandedNodeIds.add(methodKey)) {
            return
        }

        val methodBody = method.body ?: return
        activePath.addLast(methodKey)
        try {
            methodBody.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)

                    resolveCallTargets(expression).forEach { target ->
                        if (target.isConstructor || !isProjectMethod(target)) {
                            return@forEach
                        }

                        val targetMethodId = methodId(target)
                        val targetShouldDisplay = shouldDisplayMethod(target, options)
                        val nextVisibleDepth = if (targetShouldDisplay) visibleDepth + 1 else visibleDepth
                        val edgeSourceId = resolveVisibleInvocationSourceId(
                            expression = expression,
                            currentVisibleSourceId = currentVisibleSourceId,
                            options = options,
                        )
                        val nextVisibleSourceId = if (targetShouldDisplay) {
                            val targetId = addSupplementalNode(
                                nodeMap = nodeMap,
                                method = target,
                                depth = nextVisibleDepth,
                                classDepths = classDepths,
                            )
                            if (edgeSourceId != null) {
                                edges += GraphEdge(edgeSourceId, targetId)
                            }
                            targetId
                        } else {
                            edgeSourceId
                        }

                        if (!activePath.contains(targetMethodId)) {
                            visitSupplementalMethod(
                                method = target,
                                visibleDepth = nextVisibleDepth,
                                nodeMap = nodeMap,
                                edges = edges,
                                expandedNodeIds = expandedNodeIds,
                                activePath = activePath,
                                classDepths = classDepths,
                                options = options,
                                visibleSourceId = nextVisibleSourceId,
                            )
                        }
                    }
                }
            })
        } finally {
            activePath.removeLast()
        }
    }

    private fun expandCallees(
        method: PsiMethod,
        visibleDepth: Int,
        nodeMap: MutableMap<String, MutableGraphNode>,
        edges: MutableSet<GraphEdge>,
        expandedNodeIds: MutableSet<String>,
        activePath: ArrayDeque<String>,
        classDepths: MutableMap<String, Int>,
        options: GraphOptions,
        visibleSourceId: String?,
        rootClassQualifiedName: String,
    ) {
        val methodKey = methodId(method)
        val shouldDisplayCurrentMethod = shouldDisplayMethod(method, options)
        val currentVisibleSourceId = if (shouldDisplayCurrentMethod) {
            addExpansionNode(
                nodeMap = nodeMap,
                method = method,
                depth = visibleDepth,
                rootClassQualifiedName = rootClassQualifiedName,
                classDepths = classDepths,
            )
        } else {
            visibleSourceId
        }

        if (!expandedNodeIds.add(methodKey)) {
            return
        }

        val methodBody = method.body ?: return
        activePath.addLast(methodKey)
        try {
            methodBody.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)

                    resolveCallTargets(expression).forEach { target ->
                        if (target.isConstructor || !isProjectMethod(target)) {
                            return@forEach
                        }

                        val targetMethodId = methodId(target)
                        val targetShouldDisplay = shouldDisplayMethod(target, options)
                        val nextVisibleDepth = if (targetShouldDisplay) visibleDepth + 1 else visibleDepth
                        val edgeSourceId = resolveVisibleInvocationSourceId(
                            expression = expression,
                            currentVisibleSourceId = currentVisibleSourceId,
                            options = options,
                        )
                        val nextVisibleSourceId = if (targetShouldDisplay) {
                            val targetId = addExpansionNode(
                                nodeMap = nodeMap,
                                method = target,
                                depth = nextVisibleDepth,
                                rootClassQualifiedName = rootClassQualifiedName,
                                classDepths = classDepths,
                            )
                            if (edgeSourceId != null) {
                                edges += GraphEdge(edgeSourceId, targetId)
                            }
                            targetId
                        } else {
                            edgeSourceId
                        }

                        if (!activePath.contains(targetMethodId)) {
                            expandCallees(
                                method = target,
                                visibleDepth = nextVisibleDepth,
                                nodeMap = nodeMap,
                                edges = edges,
                                expandedNodeIds = expandedNodeIds,
                                activePath = activePath,
                                classDepths = classDepths,
                                options = options,
                                visibleSourceId = nextVisibleSourceId,
                                rootClassQualifiedName = rootClassQualifiedName,
                            )
                        }
                    }
                }
            })
        } finally {
            activePath.removeLast()
        }
    }

    private fun expandCallers(
        method: PsiMethod,
        visibleDepth: Int,
        nodeMap: MutableMap<String, MutableGraphNode>,
        edges: MutableSet<GraphEdge>,
        expandedNodeIds: MutableSet<String>,
        activePath: ArrayDeque<String>,
        classDepths: MutableMap<String, Int>,
        options: GraphOptions,
        visibleTargetId: String?,
        rootClassQualifiedName: String,
    ) {
        val methodKey = methodId(method)
        val shouldDisplayCurrentMethod = shouldDisplayMethod(method, options)
        val currentVisibleTargetId = if (shouldDisplayCurrentMethod) {
            addExpansionNode(
                nodeMap = nodeMap,
                method = method,
                depth = visibleDepth,
                rootClassQualifiedName = rootClassQualifiedName,
                classDepths = classDepths,
            )
        } else {
            visibleTargetId
        }

        if (!expandedNodeIds.add(methodKey)) {
            return
        }

        activePath.addLast(methodKey)
        try {
            findProjectCallers(method).forEach { caller ->
                if (caller.isConstructor || !isProjectMethod(caller)) {
                    return@forEach
                }

                val callerMethodId = methodId(caller)
                val callerShouldDisplay = shouldDisplayMethod(caller, options)
                val nextVisibleDepth = if (callerShouldDisplay) visibleDepth - 1 else visibleDepth
                val nextVisibleTargetId = if (callerShouldDisplay) {
                    val callerId = addExpansionNode(
                        nodeMap = nodeMap,
                        method = caller,
                        depth = nextVisibleDepth,
                        rootClassQualifiedName = rootClassQualifiedName,
                        classDepths = classDepths,
                    )
                    if (currentVisibleTargetId != null) {
                        edges += GraphEdge(callerId, currentVisibleTargetId)
                    }
                    callerId
                } else {
                    currentVisibleTargetId
                }

                if (!activePath.contains(callerMethodId)) {
                    expandCallers(
                        method = caller,
                        visibleDepth = nextVisibleDepth,
                        nodeMap = nodeMap,
                        edges = edges,
                        expandedNodeIds = expandedNodeIds,
                        activePath = activePath,
                        classDepths = classDepths,
                        options = options,
                        visibleTargetId = nextVisibleTargetId,
                        rootClassQualifiedName = rootClassQualifiedName,
                    )
                }
            }
        } finally {
            activePath.removeLast()
        }
    }

    internal fun findProjectCallers(method: PsiMethod): List<PsiMethod> {
        val searchTargets = (sequenceOf(method) + method.findSuperMethods().asSequence())
            .distinctBy(::methodId)
            .toList()

        val indexedCallers = searchTargets
            .asSequence()
            .flatMap { searchTarget ->
                val scope = GlobalSearchScope.projectScope(project)
                MethodReferencesSearch.search(searchTarget, scope, false).findAll().asSequence() +
                    ReferencesSearch.search(searchTarget, scope).findAll().asSequence()
            }
            .mapNotNull { reference ->
                PsiTreeUtil.getParentOfType(reference.element, PsiMethod::class.java)
            }
            .filter(::isProjectMethod)
            .distinctBy(::methodId)
            .toList()

        return if (indexedCallers.isNotEmpty()) {
            indexedCallers
        } else {
            findProjectCallersByScanning(searchTargets)
        }
    }

    private fun findProjectCallersByScanning(searchTargets: List<PsiMethod>): List<PsiMethod> {
        val targetIds = searchTargets.mapTo(linkedSetOf(), ::methodId)
        val psiManager = PsiManager.getInstance(project)
        val callers = linkedMapOf<String, PsiMethod>()

        fileIndex.iterateContent { virtualFile ->
            if (virtualFile.isDirectory || virtualFile.extension != "java" || !isProjectVirtualFile(virtualFile)) {
                return@iterateContent true
            }

            val javaFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: return@iterateContent true
            javaFile.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitMethod(method: PsiMethod) {
                    if (!isProjectMethod(method) || method.body == null) {
                        return
                    }

                    var foundCaller = false
                    method.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
                        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                            if (foundCaller) {
                                return
                            }
                            val matchesTarget = resolveCallTargets(expression)
                                .any { candidate -> targetIds.contains(methodId(candidate)) }
                            if (matchesTarget) {
                                callers.putIfAbsent(methodId(method), method)
                                foundCaller = true
                                return
                            }
                            super.visitMethodCallExpression(expression)
                        }
                    })
                }
            })
            true
        }

        return callers.values.toList()
    }

    private fun addOrUpdateNode(
        nodeMap: MutableMap<String, MutableGraphNode>,
        method: PsiMethod,
        depth: Int,
        nodeType: GraphNodeType,
        isReachable: Boolean,
        isSupplemental: Boolean,
    ): String {
        val containingClass = method.containingClass ?: return methodId(method)
        val classQualifiedName = containingClass.qualifiedName ?: containingClass.name ?: "<anonymous>"
        val nodeId = methodId(method)
        val existing = nodeMap[nodeId]

        if (existing == null) {
            nodeMap[nodeId] = MutableGraphNode(
                id = nodeId,
                className = containingClass.name ?: classQualifiedName,
                classQualifiedName = classQualifiedName,
                methodName = method.name,
                displaySignature = buildDisplaySignature(method),
                depth = depth,
                nodeType = nodeType,
                visibility = resolveVisibility(method),
                isReachable = isReachable,
                isSupplemental = isSupplemental,
                pointer = pointerManager.createSmartPsiElementPointer(method),
                nodeKind = GraphNodeKind.METHOD,
                tableName = null,
                columnName = null,
                databaseAction = null,
                sourceCount = 0,
            )
        } else {
            existing.depth = minOf(existing.depth, depth)
            existing.nodeType = mergeNodeType(existing.nodeType, nodeType)
            existing.isReachable = existing.isReachable || isReachable
            existing.isSupplemental = existing.isSupplemental || isSupplemental
        }
        return nodeId
    }

    internal fun isProjectMethod(method: PsiMethod): Boolean {
        val virtualFile = method.containingFile?.virtualFile ?: return false
        return isProjectVirtualFile(virtualFile)
    }

    internal fun isProjectClass(psiClass: PsiClass): Boolean {
        val virtualFile = psiClass.containingFile?.virtualFile ?: return false
        return isProjectVirtualFile(virtualFile)
    }

    private fun isProjectVirtualFile(virtualFile: com.intellij.openapi.vfs.VirtualFile): Boolean =
        fileIndex.isInContent(virtualFile) ||
            (!fileIndex.isInLibrary(virtualFile) && !fileIndex.isExcluded(virtualFile)) ||
            isUnderProjectBase(virtualFile.path)

    internal fun resolveTraversalTargets(method: PsiMethod): List<PsiMethod> {
        if (method.body != null && !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return listOf(method)
        }

        val implementations = OverridingMethodsSearch.search(
            method,
            GlobalSearchScope.projectScope(project),
            true,
        )
            .findAll()
            .filter(::isProjectMethod)
            .distinctBy(::methodId)

        return implementations.ifEmpty { listOf(method) }
    }

    internal fun resolveCallTargets(expression: PsiMethodCallExpression): List<PsiMethod> {
        expression.resolveMethod()?.let(::resolveTraversalTargets)?.let { targets ->
            if (targets.isNotEmpty()) {
                return targets
            }
        }

        val methodName = expression.methodExpression.referenceName ?: return emptyList()
        val argumentCount = expression.argumentList.expressions.size
        val qualifierClass = resolveQualifierClass(
            expression.methodExpression.qualifierExpression as? PsiReferenceExpression,
            expression,
        ) ?: resolvePsiClassType(
            expression.methodExpression.qualifierExpression?.type as? PsiClassType,
            expression,
        )
        val candidateOwners = buildList {
            qualifierClass?.let(::add)
            if (qualifierClass == null) {
                PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java)
                    ?.containingClass
                    ?.let(::add)
            }
        }

        return candidateOwners
            .asSequence()
            .flatMap { owner ->
                owner.findMethodsByName(methodName, true).asSequence()
            }
            .filter { candidate ->
                candidate.parameterList.parametersCount == argumentCount
            }
            .flatMap { candidate ->
                resolveTraversalTargets(candidate).asSequence()
            }
            .distinctBy(::methodId)
            .toList()
    }

    private fun resolveQualifierClass(
        referenceExpression: PsiReferenceExpression?,
        context: PsiElement,
    ): PsiClass? {
        val variable = referenceExpression?.resolve() as? PsiVariable ?: return null
        val classType = variable.type as? PsiClassType ?: return null
        return resolvePsiClassType(classType, context)
    }

    private fun resolvePsiClassType(classType: PsiClassType?, context: PsiElement): PsiClass? {
        classType ?: return null
        PsiUtil.resolveClassInType(classType)?.let { return it }
        classType.resolve()?.let { return it }
        PsiUtil.resolveClassInClassTypeOnly(classType)?.let { return it }

        val shortName = classType.className?.takeIf { it.isNotBlank() } ?: return null
        val contextPackage = (context.containingFile as? PsiJavaFile)?.packageName
        return PsiShortNamesCache.getInstance(project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(project))
            .asSequence()
            .filter(::isProjectClass)
            .sortedByDescending { psiClass ->
                val candidatePackage = (psiClass.containingFile as? PsiJavaFile)?.packageName
                candidatePackage == contextPackage
            }
            .firstOrNull()
    }

    private fun resolveVisibleInvocationSourceId(
        expression: PsiMethodCallExpression,
        currentVisibleSourceId: String?,
        options: GraphOptions,
    ): String? {
        val qualifierCall = expression.methodExpression.qualifierExpression as? PsiMethodCallExpression
            ?: return currentVisibleSourceId
        val qualifierTarget = resolveCallTargets(qualifierCall)
            .firstOrNull { shouldDisplayMethod(it, options) }
            ?: return currentVisibleSourceId
        return methodId(qualifierTarget)
    }

    private fun updateClassDepth(
        method: PsiMethod,
        depth: Int,
        classDepths: MutableMap<String, Int>,
    ) {
        val classKey = method.containingClass?.let(::classId) ?: return
        classDepths[classKey] = minOf(classDepths[classKey] ?: depth, depth)
    }

    private fun collectDeclaredMethods(psiClass: PsiClass, options: GraphOptions): List<PsiMethod> =
        psiClass.methods
            .filter { !it.isConstructor && it.containingClass == psiClass && shouldDisplayMethod(it, options) }
            .sortedBy(::buildDisplaySignature)

    internal fun shouldDisplayMethod(method: PsiMethod, options: GraphOptions): Boolean =
        (options.showAccessorMethods || !isAccessorMethod(method)) &&
            (options.showPrivateMethods || !method.hasModifierProperty(PsiModifier.PRIVATE))

    private fun resolveVisibility(method: PsiMethod): GraphMethodVisibility = when {
        method.hasModifierProperty(PsiModifier.PUBLIC) -> GraphMethodVisibility.PUBLIC
        method.hasModifierProperty(PsiModifier.PRIVATE) -> GraphMethodVisibility.PRIVATE
        else -> GraphMethodVisibility.OTHER
    }

    private fun isAccessorMethod(method: PsiMethod): Boolean {
        if (method.isConstructor) {
            return false
        }

        val name = method.name
        val parameterCount = method.parameterList.parametersCount
        val returnType = method.returnType

        return isGetterName(name) && parameterCount == 0 && returnType != null && !isVoidType(returnType) ||
            isBooleanGetterName(name) && parameterCount == 0 && isBooleanLikeType(returnType) ||
            isSetterName(name) && parameterCount == 1 && (returnType == null || isVoidType(returnType))
    }

    private fun isVoidType(type: PsiType): Boolean =
        type == PsiTypes.voidType()

    private fun isBooleanLikeType(type: PsiType?): Boolean {
        type ?: return false
        if (type == PsiTypes.booleanType()) {
            return true
        }
        return if (DumbService.isDumb(project)) {
            type.presentableText == "Boolean"
        } else {
            type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)
        }
    }

    private fun isGetterName(name: String): Boolean =
        name.startsWith("get") && name.length > 3 && name[3].isUpperCase()

    private fun isBooleanGetterName(name: String): Boolean =
        name.startsWith("is") && name.length > 2 && name[2].isUpperCase()

    private fun isSetterName(name: String): Boolean =
        name.startsWith("set") && name.length > 3 && name[3].isUpperCase()

    internal fun classId(psiClass: PsiClass): String =
        psiClass.qualifiedName ?: psiClass.name ?: "<anonymous:${psiClass.hashCode()}>"

    internal fun methodId(method: PsiMethod): String {
        val owningClass = method.containingClass?.let(::classId) ?: "<no-class>"
        val parameterTypes = method.parameterList.parameters.joinToString(",") { parameter ->
            safeParameterTypeKey(parameter)
        }
        return "$owningClass#${method.name}($parameterTypes)"
    }

    internal fun buildDisplaySignature(method: PsiMethod): String {
        val parameterTypes = method.parameterList.parameters.joinToString(", ") { parameter ->
            parameter.type.presentableText
        }
        return "${method.name}($parameterTypes)"
    }

    private fun safeParameterTypeKey(parameter: com.intellij.psi.PsiParameter): String =
        parameter.type.presentableText.ifBlank { parameter.text }

    private fun isUnderProjectBase(path: String): Boolean =
        project.basePath?.let { basePath ->
            path.replace('\\', '/').startsWith(basePath.replace('\\', '/'))
        } == true

    private fun nodeTypeRank(nodeType: GraphNodeType): Int = when (nodeType) {
        GraphNodeType.ROOT -> 0
        GraphNodeType.REACHABLE -> 1
        GraphNodeType.SUPPLEMENTAL -> 2
        GraphNodeType.DATABASE_TABLE -> 3
        GraphNodeType.DATABASE_COLUMN -> 4
        GraphNodeType.DATABASE_COLUMN_OPERATION -> 5
    }

    private fun mergeNodeType(left: GraphNodeType, right: GraphNodeType): GraphNodeType {
        if (left == GraphNodeType.DATABASE_TABLE || right == GraphNodeType.DATABASE_TABLE) {
            return GraphNodeType.DATABASE_TABLE
        }
        if (left == GraphNodeType.DATABASE_COLUMN || right == GraphNodeType.DATABASE_COLUMN) {
            return GraphNodeType.DATABASE_COLUMN
        }
        if (left == GraphNodeType.DATABASE_COLUMN_OPERATION || right == GraphNodeType.DATABASE_COLUMN_OPERATION) {
            return GraphNodeType.DATABASE_COLUMN_OPERATION
        }
        if (left == GraphNodeType.ROOT || right == GraphNodeType.ROOT) {
            return GraphNodeType.ROOT
        }
        if (left == GraphNodeType.REACHABLE || right == GraphNodeType.REACHABLE) {
            return GraphNodeType.REACHABLE
        }
        return GraphNodeType.SUPPLEMENTAL
    }

    enum class ExpansionDirection(
        val includesCallers: Boolean,
        val includesCallees: Boolean,
    ) {
        CALLERS(true, false),
        CALLEES(false, true),
        BOTH(true, true),
    }

    private data class MutableGraphNode(
        val id: String,
        val className: String,
        val classQualifiedName: String,
        val methodName: String,
        val displaySignature: String,
        var depth: Int,
        var nodeType: GraphNodeType,
        val visibility: GraphMethodVisibility,
        var isReachable: Boolean,
        var isSupplemental: Boolean,
        val pointer: SmartPsiElementPointer<out PsiElement>?,
        val nodeKind: GraphNodeKind,
        val tableName: String?,
        val columnName: String?,
        val databaseAction: String?,
        var sourceCount: Int,
    ) {
        fun toGraphNode(): GraphNode = GraphNode(
            id = id,
            className = className,
            classQualifiedName = classQualifiedName,
            methodName = methodName,
            displaySignature = displaySignature,
            depth = depth,
            nodeType = nodeType,
            visibility = visibility,
            isReachable = isReachable,
            isSupplemental = isSupplemental,
            pointer = pointer,
            nodeKind = nodeKind,
            tableName = tableName,
            columnName = columnName,
            databaseAction = databaseAction,
            sourceCount = sourceCount,
        )
    }

    private fun appendMapperTables(
        nodeMap: MutableMap<String, MutableGraphNode>,
        edges: MutableSet<GraphEdge>,
        options: GraphOptions,
    ) {
        if (!options.showMapperTables) {
            return
        }

        val tableSourceIds = mutableMapOf<String, MutableSet<String>>()
        val columnSourceIds = mutableMapOf<String, MutableSet<String>>()
        val operationSourceIds = mutableMapOf<String, MutableSet<String>>()
        val methodNodes = nodeMap.values
            .filter { it.nodeKind == GraphNodeKind.METHOD }
            .toList()

        methodNodes.forEach { methodNode ->
            val method = methodNode.pointer?.element as? PsiMethod ?: return@forEach
            val tableMappings = mapperTableAnalyzer.collectTableMappings(method)
            if (tableMappings.isEmpty()) {
                return@forEach
            }

            tableMappings.forEach { mapping ->
                val tableNodeId = tableNodeId(mapping.tableName)
                val tableDepth = methodNode.depth + 1
                val tableNode = nodeMap[tableNodeId]
                if (tableNode == null) {
                    nodeMap[tableNodeId] = MutableGraphNode(
                        id = tableNodeId,
                        className = mapping.tableName,
                        classQualifiedName = tableNodeId,
                        methodName = mapping.tableName,
                        displaySignature = mapping.tableName,
                        depth = tableDepth,
                        nodeType = GraphNodeType.DATABASE_TABLE,
                        visibility = GraphMethodVisibility.OTHER,
                        isReachable = true,
                        isSupplemental = false,
                        pointer = null,
                        nodeKind = GraphNodeKind.DATABASE_TABLE,
                        tableName = mapping.tableName,
                        columnName = null,
                        databaseAction = null,
                        sourceCount = 0,
                    )
                } else {
                    tableNode.depth = minOf(tableNode.depth, tableDepth)
                }
                tableSourceIds.getOrPut(tableNodeId) { linkedSetOf() }.add(methodNode.id)
                if (mapping.columns.isEmpty()) {
                    edges += GraphEdge(methodNode.id, tableNodeId)
                }

                mapping.columns.forEach { columnName ->
                    val columnNodeId = columnNodeId(mapping.tableName, columnName)
                    val columnNode = nodeMap[columnNodeId]
                    if (columnNode == null) {
                        nodeMap[columnNodeId] = MutableGraphNode(
                            id = columnNodeId,
                            className = mapping.tableName,
                            classQualifiedName = tableNodeId,
                            methodName = columnName,
                            displaySignature = columnName,
                            depth = tableDepth,
                            nodeType = GraphNodeType.DATABASE_COLUMN,
                            visibility = GraphMethodVisibility.OTHER,
                            isReachable = true,
                            isSupplemental = false,
                            pointer = null,
                            nodeKind = GraphNodeKind.DATABASE_COLUMN,
                            tableName = mapping.tableName,
                            columnName = columnName,
                            databaseAction = null,
                            sourceCount = 0,
                        )
                    } else {
                        columnNode.depth = minOf(columnNode.depth, tableDepth)
                    }
                    columnSourceIds.getOrPut(columnNodeId) { linkedSetOf() }.add(methodNode.id)
                    edges += GraphEdge(methodNode.id, columnNodeId)

                    mapping.columnActions[columnName].orEmpty().forEach { action ->
                        val operationNodeId = columnOperationNodeId(mapping.tableName, columnName, action.id)
                        val operationNode = nodeMap[operationNodeId]
                        if (operationNode == null) {
                            nodeMap[operationNodeId] = MutableGraphNode(
                                id = operationNodeId,
                                className = mapping.tableName,
                                classQualifiedName = tableNodeId,
                                methodName = action.id,
                                displaySignature = action.displayName,
                                depth = tableDepth,
                                nodeType = GraphNodeType.DATABASE_COLUMN_OPERATION,
                                visibility = GraphMethodVisibility.OTHER,
                                isReachable = true,
                                isSupplemental = false,
                                pointer = null,
                                nodeKind = GraphNodeKind.DATABASE_COLUMN_OPERATION,
                                tableName = mapping.tableName,
                                columnName = columnName,
                                databaseAction = action.id,
                                sourceCount = 0,
                            )
                        } else {
                            operationNode.depth = minOf(operationNode.depth, tableDepth)
                        }
                        operationSourceIds.getOrPut(operationNodeId) { linkedSetOf() }.add(methodNode.id)
                        edges += GraphEdge(methodNode.id, operationNodeId)
                    }
                }
            }
        }

        tableSourceIds.forEach { (tableNodeId, sources) ->
            nodeMap[tableNodeId]?.sourceCount = sources.size
        }
        columnSourceIds.forEach { (columnNodeId, sources) ->
            nodeMap[columnNodeId]?.sourceCount = sources.size
        }
        operationSourceIds.forEach { (operationNodeId, sources) ->
            nodeMap[operationNodeId]?.sourceCount = sources.size
        }
    }

    private fun tableNodeId(tableName: String): String = "db:$tableName"

    private fun columnNodeId(tableName: String, columnName: String): String = "${tableNodeId(tableName)}#$columnName"

    private fun columnOperationNodeId(tableName: String, columnName: String, actionId: String): String =
        "${columnNodeId(tableName, columnName)}@$actionId"
}
