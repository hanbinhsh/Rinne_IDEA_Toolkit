package com.github.hanbinhsh.rinneideatoolkit.services

import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceDiagram
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceMessage
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceMessageKind
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceParticipant
import com.github.hanbinhsh.rinneideatoolkit.model.SequenceScenario
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiUtil

@Service(Service.Level.PROJECT)
class SequenceDiagramAnalyzer(private val project: Project) {

    private val methodCallAnalyzer = project.service<MethodCallAnalyzer>()
    private val mapperTableAnalyzer = project.service<MapperTableAnalyzer>()
    private val pointerManager = SmartPointerManager.getInstance(project)

    fun analyze(targetMethod: PsiMethod, options: GraphOptions): SequenceDiagram {
        val callerChains = collectVisibleCallerChains(targetMethod, options)
        val scenarios = callerChains.mapIndexed { index, chain ->
            buildScenario(
                scenarioId = index + 1,
                callerChain = chain,
                targetMethod = targetMethod,
                options = options,
            )
        }
        val targetClassQualifiedName = targetMethod.containingClass?.let(methodCallAnalyzer::classId).orEmpty()

        return SequenceDiagram(
            targetMethodDisplayName = displayMethodLabel(targetMethod),
            targetClassQualifiedName = targetClassQualifiedName,
            targetMethodSignature = methodCallAnalyzer.buildDisplaySignature(targetMethod),
            scenarios = scenarios,
        )
    }

    private fun buildScenario(
        scenarioId: Int,
        callerChain: List<PsiMethod>,
        targetMethod: PsiMethod,
        options: GraphOptions,
    ): SequenceScenario {
        val participants = linkedMapOf<String, SequenceParticipant>()
        val messages = mutableListOf<SequenceMessage>()
        var nextOrder = 1

        fun registerParticipant(participantName: String, participantQualifiedName: String) {
            participants.putIfAbsent(
                participantQualifiedName,
                SequenceParticipant(
                    className = participantName,
                    classQualifiedName = participantQualifiedName,
                ),
            )
        }

        fun registerParticipant(psiClass: PsiClass) {
            val classQualifiedName = methodCallAnalyzer.classId(psiClass)
            registerParticipant(psiClass.name ?: classQualifiedName, classQualifiedName)
        }

        fun registerParticipant(method: PsiMethod) {
            method.containingClass?.let(::registerParticipant)
        }

        fun addMessage(
            fromParticipantName: String,
            fromParticipantQualifiedName: String,
            toParticipantName: String,
            toParticipantQualifiedName: String,
            depth: Int,
            kind: SequenceMessageKind,
            displaySignature: String,
            pointerElement: PsiElement,
            isCreateReturn: Boolean = false,
        ) {
            registerParticipant(fromParticipantName, fromParticipantQualifiedName)
            registerParticipant(toParticipantName, toParticipantQualifiedName)
            messages += SequenceMessage(
                scenarioId = scenarioId,
                order = nextOrder++,
                depth = depth,
                fromClassName = fromParticipantName,
                fromClassQualifiedName = fromParticipantQualifiedName,
                toClassName = toParticipantName,
                toClassQualifiedName = toParticipantQualifiedName,
                methodDisplaySignature = displaySignature,
                kind = kind,
                isCreateReturn = isCreateReturn,
                isSelfCall = fromParticipantQualifiedName == toParticipantQualifiedName,
                pointer = pointerManager.createSmartPsiElementPointer(pointerElement),
            )
        }

        fun addMethodMessage(
            fromMethod: PsiMethod,
            targetMethod: PsiMethod,
            depth: Int,
            kind: SequenceMessageKind,
            displaySignature: String = methodCallAnalyzer.buildDisplaySignature(targetMethod),
            pointerElement: PsiElement = targetMethod,
        ) {
            val fromClass = fromMethod.containingClass ?: return
            val toClass = targetMethod.containingClass ?: return
            addMessage(
                fromParticipantName = fromClass.name ?: methodCallAnalyzer.classId(fromClass),
                fromParticipantQualifiedName = methodCallAnalyzer.classId(fromClass),
                toParticipantName = toClass.name ?: methodCallAnalyzer.classId(toClass),
                toParticipantQualifiedName = methodCallAnalyzer.classId(toClass),
                depth = depth,
                kind = kind,
                displaySignature = displaySignature,
                pointerElement = pointerElement,
            )
        }

        fun addCreateMessage(
            fromMethod: PsiMethod,
            targetClass: PsiClass,
            depth: Int,
            pointerElement: PsiElement,
        ) {
            val fromClass = fromMethod.containingClass ?: return
            addMessage(
                fromParticipantName = fromClass.name ?: methodCallAnalyzer.classId(fromClass),
                fromParticipantQualifiedName = methodCallAnalyzer.classId(fromClass),
                toParticipantName = targetClass.name ?: methodCallAnalyzer.classId(targetClass),
                toParticipantQualifiedName = methodCallAnalyzer.classId(targetClass),
                depth = depth,
                kind = SequenceMessageKind.CREATE,
                displaySignature = CREATE_MESSAGE_LABEL,
                pointerElement = pointerElement,
            )
        }

        fun addClassReturnMessage(
            fromClass: PsiClass,
            toMethod: PsiMethod,
            depth: Int,
            pointerElement: PsiElement,
        ) {
            val toClass = toMethod.containingClass ?: return
            addMessage(
                fromParticipantName = fromClass.name ?: methodCallAnalyzer.classId(fromClass),
                fromParticipantQualifiedName = methodCallAnalyzer.classId(fromClass),
                toParticipantName = toClass.name ?: methodCallAnalyzer.classId(toClass),
                toParticipantQualifiedName = methodCallAnalyzer.classId(toClass),
                depth = depth,
                kind = SequenceMessageKind.RETURN,
                displaySignature = "",
                pointerElement = pointerElement,
                isCreateReturn = true,
            )
        }

        fun addMapperTableMessages(sourceMethod: PsiMethod, depth: Int) {
            if (!options.showMapperTables) {
                return
            }
            val fromClass = sourceMethod.containingClass ?: return
            val fromClassName = fromClass.name ?: methodCallAnalyzer.classId(fromClass)
            val fromClassQualifiedName = methodCallAnalyzer.classId(fromClass)

            mapperTableAnalyzer.collectTableMappings(sourceMethod).forEach { mapping ->
                val tableQualifiedName = "db:${mapping.tableName}"
                if (mapping.columns.isEmpty()) {
                    addMessage(
                        fromParticipantName = fromClassName,
                        fromParticipantQualifiedName = fromClassQualifiedName,
                        toParticipantName = mapping.tableName,
                        toParticipantQualifiedName = tableQualifiedName,
                        depth = depth,
                        kind = SequenceMessageKind.CALL,
                        displaySignature = mapping.tableName,
                        pointerElement = sourceMethod,
                    )
                } else {
                    mapping.columns.sorted().forEach { columnName ->
                        addMessage(
                            fromParticipantName = fromClassName,
                            fromParticipantQualifiedName = fromClassQualifiedName,
                            toParticipantName = mapping.tableName,
                            toParticipantQualifiedName = tableQualifiedName,
                            depth = depth,
                            kind = SequenceMessageKind.CALL,
                            displaySignature = columnName,
                            pointerElement = sourceMethod,
                        )
                    }
                }
            }
        }

        callerChain.forEach(::registerParticipant)
        callerChain.zipWithNext().forEach { (caller, callee) ->
            addMethodMessage(caller, callee, 0, SequenceMessageKind.CALL)
        }

        appendDownstreamMessages(
            visibleSourceMethod = targetMethod,
            inspectedMethod = targetMethod,
            options = options,
            currentDepth = 1,
            activePath = linkedSetOf(methodCallAnalyzer.methodId(targetMethod)),
            addMethodMessage = ::addMethodMessage,
            addCreateMessage = ::addCreateMessage,
            addClassReturnMessage = ::addClassReturnMessage,
            addMapperTableMessages = ::addMapperTableMessages,
        )

        registerParticipant(targetMethod)

        callerChain.zipWithNext()
            .asReversed()
            .forEach { (caller, callee) ->
                addMethodMessage(
                    fromMethod = callee,
                    targetMethod = caller,
                    depth = 0,
                    kind = SequenceMessageKind.RETURN,
                    displaySignature = "",
                    pointerElement = callee,
                )
            }

        return SequenceScenario(
            id = scenarioId,
            entryMethodDisplayName = displayMethodLabel(callerChain.firstOrNull() ?: targetMethod),
            entryPointer = pointerManager.createSmartPsiElementPointer(callerChain.firstOrNull() ?: targetMethod),
            participants = participants.values.toList(),
            messages = messages,
        )
    }

    private fun appendDownstreamMessages(
        visibleSourceMethod: PsiMethod,
        inspectedMethod: PsiMethod,
        options: GraphOptions,
        currentDepth: Int,
        activePath: LinkedHashSet<String>,
        addMethodMessage: (PsiMethod, PsiMethod, Int, SequenceMessageKind, String, PsiElement) -> Unit,
        addCreateMessage: (PsiMethod, PsiClass, Int, PsiElement) -> Unit,
        addClassReturnMessage: (PsiClass, PsiMethod, Int, PsiElement) -> Unit,
        addMapperTableMessages: (PsiMethod, Int) -> Unit,
    ) {
        addMapperTableMessages(inspectedMethod, currentDepth)
        val methodBody = inspectedMethod.body ?: return

        collectInvocationStepsInEvaluationOrder(methodBody).forEach { step ->
            when (step) {
                is InvocationStep.Call -> {
                    methodCallAnalyzer.resolveCallTargets(step.expression).forEach { target ->
                        if (target.isConstructor || !methodCallAnalyzer.isProjectMethod(target)) {
                            return@forEach
                        }

                        val targetId = methodCallAnalyzer.methodId(target)
                        val shouldDisplayTarget = methodCallAnalyzer.shouldDisplayMethod(target, options)
                        if (shouldDisplayTarget) {
                            addMethodMessage(
                                visibleSourceMethod,
                                target,
                                currentDepth,
                                SequenceMessageKind.CALL,
                                methodCallAnalyzer.buildDisplaySignature(target),
                                target,
                            )
                            if (!activePath.contains(targetId)) {
                                activePath += targetId
                                try {
                                    appendDownstreamMessages(
                                        visibleSourceMethod = target,
                                        inspectedMethod = target,
                                        options = options,
                                        currentDepth = currentDepth + 1,
                                        activePath = activePath,
                                        addMethodMessage = addMethodMessage,
                                        addCreateMessage = addCreateMessage,
                                        addClassReturnMessage = addClassReturnMessage,
                                        addMapperTableMessages = addMapperTableMessages,
                                    )
                                } finally {
                                    activePath.remove(targetId)
                                }
                            }
                            addMethodMessage(
                                target,
                                visibleSourceMethod,
                                currentDepth,
                                SequenceMessageKind.RETURN,
                                "",
                                target,
                            )
                        } else if (!activePath.contains(targetId)) {
                            activePath += targetId
                            try {
                                appendDownstreamMessages(
                                    visibleSourceMethod = visibleSourceMethod,
                                    inspectedMethod = target,
                                    options = options,
                                    currentDepth = currentDepth,
                                    activePath = activePath,
                                    addMethodMessage = addMethodMessage,
                                    addCreateMessage = addCreateMessage,
                                    addClassReturnMessage = addClassReturnMessage,
                                    addMapperTableMessages = addMapperTableMessages,
                                )
                            } finally {
                                activePath.remove(targetId)
                            }
                        }
                    }
                }

                is InvocationStep.Create -> {
                    val targetClass = resolveCreatedClass(step.expression) ?: return@forEach
                    if (!methodCallAnalyzer.isProjectClass(targetClass)) {
                        return@forEach
                    }
                    addCreateMessage(
                        visibleSourceMethod,
                        targetClass,
                        currentDepth,
                        step.expression.resolveConstructor() ?: targetClass,
                    )
                    addClassReturnMessage(
                        targetClass,
                        visibleSourceMethod,
                        currentDepth,
                        step.expression.resolveConstructor() ?: targetClass,
                    )
                }
            }
        }
    }

    private fun resolveCreatedClass(expression: PsiNewExpression): PsiClass? {
        (expression.classOrAnonymousClassReference?.resolve() as? PsiClass)?.let { return it }

        val classType = expression.type as? PsiClassType ?: return null
        PsiUtil.resolveClassInType(classType)?.let { return it }
        classType.resolve()?.let { return it }
        PsiUtil.resolveClassInClassTypeOnly(classType)?.let { return it }

        val shortName = classType.className?.takeIf { it.isNotBlank() } ?: return null
        val contextPackage = (expression.containingFile as? PsiJavaFile)?.packageName
        return PsiShortNamesCache.getInstance(project)
            .getClassesByName(shortName, GlobalSearchScope.projectScope(project))
            .asSequence()
            .filter(methodCallAnalyzer::isProjectClass)
            .sortedByDescending { psiClass ->
                val candidatePackage = (psiClass.containingFile as? PsiJavaFile)?.packageName
                candidatePackage == contextPackage
            }
            .firstOrNull()
    }

    private fun collectInvocationStepsInEvaluationOrder(root: PsiElement): List<InvocationStep> {
        val steps = mutableListOf<InvocationStep>()

        fun visit(element: PsiElement) {
            when (element) {
                is PsiMethodCallExpression -> {
                    val qualifierExpression = element.methodExpression.qualifierExpression
                    if (qualifierExpression is PsiNewExpression) {
                        qualifierExpression.qualifier?.let(::visit)
                        qualifierExpression.arrayDimensions.forEach(::visit)
                        qualifierExpression.arrayInitializer?.let(::visit)
                        qualifierExpression.argumentList?.expressions?.forEach(::visit)
                    } else {
                        qualifierExpression?.let(::visit)
                    }
                    element.argumentList.expressions.forEach(::visit)
                    steps += InvocationStep.Call(element)
                }

                is PsiNewExpression -> {
                    element.qualifier?.let(::visit)
                    element.arrayDimensions.forEach(::visit)
                    element.arrayInitializer?.let(::visit)
                    element.argumentList?.expressions?.forEach(::visit)
                    steps += InvocationStep.Create(element)
                }

                else -> element.children.forEach(::visit)
            }
        }

        visit(root)
        return steps
    }

    private fun collectVisibleCallerChains(targetMethod: PsiMethod, options: GraphOptions): List<List<PsiMethod>> {
        if (!methodCallAnalyzer.shouldDisplayMethod(targetMethod, options)) {
            return listOf(listOf(targetMethod))
        }

        val rawChains = collectVisibleCallerChains(
            currentMethod = targetMethod,
            options = options,
            activeVisible = linkedSetOf(methodCallAnalyzer.methodId(targetMethod)),
        )

        return rawChains
            .ifEmpty { listOf(listOf(targetMethod)) }
            .distinctBy { chain -> chain.joinToString(" -> ") { methodCallAnalyzer.methodId(it) } }
    }

    private fun collectVisibleCallerChains(
        currentMethod: PsiMethod,
        options: GraphOptions,
        activeVisible: LinkedHashSet<String>,
    ): List<List<PsiMethod>> {
        val visibleCallers = findVisibleCallers(
            currentMethod = currentMethod,
            options = options,
            activeMethods = linkedSetOf(methodCallAnalyzer.methodId(currentMethod)),
        )

        if (visibleCallers.isEmpty()) {
            return listOf(listOf(currentMethod))
        }

        val chains = mutableListOf<List<PsiMethod>>()
        visibleCallers.forEach { caller ->
            val callerId = methodCallAnalyzer.methodId(caller)
            if (activeVisible.contains(callerId)) {
                chains += listOf(currentMethod)
                return@forEach
            }

            val nextActive = LinkedHashSet(activeVisible).apply { add(callerId) }
            collectVisibleCallerChains(caller, options, nextActive)
                .forEach { chain -> chains += chain + currentMethod }
        }
        return chains
    }

    private fun findVisibleCallers(
        currentMethod: PsiMethod,
        options: GraphOptions,
        activeMethods: LinkedHashSet<String>,
    ): List<PsiMethod> {
        val callers = mutableListOf<PsiMethod>()
        methodCallAnalyzer.findProjectCallers(currentMethod).forEach { caller ->
            if (caller.isConstructor || !methodCallAnalyzer.isProjectMethod(caller)) {
                return@forEach
            }

            val callerId = methodCallAnalyzer.methodId(caller)
            if (activeMethods.contains(callerId)) {
                return@forEach
            }

            if (methodCallAnalyzer.shouldDisplayMethod(caller, options)) {
                callers += caller
            } else {
                val nextActive = LinkedHashSet(activeMethods).apply { add(callerId) }
                callers += findVisibleCallers(caller, options, nextActive)
            }
        }

        return callers.distinctBy(methodCallAnalyzer::methodId)
    }

    private fun displayMethodLabel(method: PsiMethod): String {
        val className = method.containingClass?.name ?: methodCallAnalyzer.methodId(method)
        return "$className.${methodCallAnalyzer.buildDisplaySignature(method)}"
    }

    private sealed interface InvocationStep {
        data class Call(val expression: PsiMethodCallExpression) : InvocationStep

        data class Create(val expression: PsiNewExpression) : InvocationStep
    }

    private companion object {
        const val CREATE_MESSAGE_LABEL = "<<create>>"
    }
}
