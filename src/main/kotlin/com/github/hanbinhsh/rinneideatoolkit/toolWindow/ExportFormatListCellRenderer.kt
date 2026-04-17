package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.github.hanbinhsh.rinneideatoolkit.MyBundle
import com.github.hanbinhsh.rinneideatoolkit.model.ClipboardExportFormat
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class ExportFormatListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        text = when (value as? ClipboardExportFormat) {
            ClipboardExportFormat.IMAGE -> MyBundle.message("settings.copyButtonFormat.image")
            ClipboardExportFormat.SVG -> MyBundle.message("settings.copyButtonFormat.svg")
            ClipboardExportFormat.MERMAID -> MyBundle.message("settings.copyButtonFormat.mermaid")
            null -> text
        }
        return this
    }
}
