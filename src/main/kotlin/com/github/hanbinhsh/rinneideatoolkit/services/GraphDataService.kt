package com.github.hanbinhsh.rinneideatoolkit.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.AppExecutorUtil
import com.github.hanbinhsh.rinneideatoolkit.model.GraphOptions
import com.github.hanbinhsh.rinneideatoolkit.model.GraphViewState
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
class GraphDataService(private val project: Project) {

    private val analyzer = project.service<MethodCallAnalyzer>()
    private val preferencesService = project.service<GraphPreferencesService>()
    private val pointerManager = SmartPointerManager.getInstance(project)

    @Volatile
    private var selectedClassPointer: SmartPsiElementPointer<PsiClass>? = null

    @Volatile
    private var state: GraphViewState = GraphViewState(options = preferencesService.getPreferences().graphOptions)

    private val rebuildRequestId = AtomicInteger()

    fun getState(): GraphViewState = state

    fun analyzeClass(psiClass: PsiClass) {
        selectedClassPointer = ReadAction.compute<SmartPsiElementPointer<PsiClass>, RuntimeException> {
            pointerManager.createSmartPsiElementPointer(psiClass)
        }
        rebuildAndPublish(clearFocus = true)
    }

    fun refresh() {
        rebuildAndPublish()
    }

    fun reanalyzeSelectedClass() {
        rebuildAndPublish(clearFocus = true)
    }

    fun updateOptions(options: GraphOptions) {
        preferencesService.updateGraphOptions(options)
        state = state.copy(options = options)
        rebuildAndPublish()
    }

    private fun rebuildAndPublish(clearFocus: Boolean = false) {
        val requestId = rebuildRequestId.incrementAndGet()
        val currentOptions = state.options
        if (ApplicationManager.getApplication().isUnitTestMode) {
            val nextState = ReadAction.compute<GraphViewState, RuntimeException> {
                val psiClass = selectedClassPointer?.element
                if (psiClass == null) {
                    GraphViewState(options = currentOptions, clearFocus = clearFocus)
                } else {
                    val graph = analyzer.analyze(psiClass, currentOptions)
                    GraphViewState(
                        rootClassQualifiedName = graph.rootClassQualifiedName,
                        rootClassDisplayName = graph.rootClassName,
                        options = currentOptions,
                        graph = graph,
                        clearFocus = clearFocus,
                    )
                }
            }
            if (requestId != rebuildRequestId.get()) {
                return
            }
            state = nextState
            project.messageBus.syncPublisher(GraphUpdateTopic.TOPIC).graphUpdated(nextState)
            return
        }
        ReadAction
            .nonBlocking<GraphViewState> {
                val psiClass = selectedClassPointer?.element
                if (psiClass == null) {
                    GraphViewState(options = currentOptions, clearFocus = clearFocus)
                } else {
                    val graph = analyzer.analyze(psiClass, currentOptions)
                    GraphViewState(
                        rootClassQualifiedName = graph.rootClassQualifiedName,
                        rootClassDisplayName = graph.rootClassName,
                        options = currentOptions,
                        graph = graph,
                        clearFocus = clearFocus,
                    )
                }
            }
            .expireWith(project)
            .coalesceBy(this, selectedClassPointer ?: this, currentOptions)
            .finishOnUiThread(ModalityState.any()) { nextState ->
                if (requestId != rebuildRequestId.get()) {
                    return@finishOnUiThread
                }
                state = nextState
                project.messageBus.syncPublisher(GraphUpdateTopic.TOPIC).graphUpdated(nextState)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
