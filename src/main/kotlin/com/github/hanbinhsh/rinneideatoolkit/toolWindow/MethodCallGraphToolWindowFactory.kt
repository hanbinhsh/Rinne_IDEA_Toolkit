package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MethodCallGraphToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MethodCallGraphToolWindowPanel(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(
            panel,
            com.github.hanbinhsh.rinneideatoolkit.MyBundle.message("toolWindow.mainTabTitle"),
            false,
        )
        content.isCloseable = false
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
