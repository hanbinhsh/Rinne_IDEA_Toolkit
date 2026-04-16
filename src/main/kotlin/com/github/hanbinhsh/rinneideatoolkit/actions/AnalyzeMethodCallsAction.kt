package com.github.hanbinhsh.rinneideatoolkit.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import com.github.hanbinhsh.rinneideatoolkit.MethodCallGraphConstants
import com.github.hanbinhsh.rinneideatoolkit.MyBundle
import com.github.hanbinhsh.rinneideatoolkit.services.GraphDataService

class AnalyzeMethodCallsAction : DumbAwareAction(
    MyBundle.message("action.analyzeMethodCalls.text"),
    MyBundle.message("action.analyzeMethodCalls.description"),
    null,
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val targetClass = ReadAction.compute<PsiClass?, RuntimeException> {
            findTargetClass(
                project = e.project,
                psiElement = e.getData(CommonDataKeys.PSI_ELEMENT),
                psiFile = e.getData(CommonDataKeys.PSI_FILE),
                editor = e.getData(CommonDataKeys.EDITOR),
            )
        }
        e.presentation.isEnabledAndVisible = targetClass != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targetClass = ReadAction.compute<PsiClass?, RuntimeException> {
            findTargetClass(
                project = project,
                psiElement = e.getData(CommonDataKeys.PSI_ELEMENT),
                psiFile = e.getData(CommonDataKeys.PSI_FILE),
                editor = e.getData(CommonDataKeys.EDITOR),
            )
        } ?: return

        project.service<GraphDataService>().analyzeClass(targetClass)
        ToolWindowManager.getInstance(project)
            .getToolWindow(MethodCallGraphConstants.TOOL_WINDOW_ID)
            ?.show(null)
    }

    companion object {
        internal fun findTargetClass(
            project: Project?,
            psiElement: PsiElement?,
            psiFile: PsiFile?,
            editor: Editor?,
        ): PsiClass? {
            project ?: return null

            val fromElement = when (psiElement) {
                is PsiClass -> psiElement
                null -> null
                else -> PsiTreeUtil.getParentOfType(psiElement, PsiClass::class.java, false)
            }
            if (fromElement != null) {
                return fromElement
            }

            val javaFile = psiFile as? PsiJavaFile
            if (editor != null && javaFile != null) {
                val caretElement = psiFile.findElementAt(editor.caretModel.offset)
                val caretClass = PsiTreeUtil.getParentOfType(caretElement, PsiClass::class.java, false)
                if (caretClass != null) {
                    return caretClass
                }
            }

            return javaFile?.classes?.firstOrNull()
        }
    }
}
