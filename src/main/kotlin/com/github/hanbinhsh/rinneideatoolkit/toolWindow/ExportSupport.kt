package com.github.hanbinhsh.rinneideatoolkit.toolWindow

import com.github.hanbinhsh.rinneideatoolkit.model.ClipboardExportFormat
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

internal fun copyTextToClipboard(text: String) {
    CopyPasteManager.getInstance().setContents(StringSelection(text))
}

internal fun buildEmbeddedSvg(
    image: BufferedImage,
    width: Int,
    height: Int,
    title: String,
): String {
    val imageBytes = ByteArrayOutputStream().use { output ->
        ImageIO.write(image, "png", output)
        output.toByteArray()
    }
    val encoded = Base64.getEncoder().encodeToString(imageBytes)
    return buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append(
            """
            <svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="$width" height="$height" viewBox="0 0 $width $height">
              <title>${escapeXml(title)}</title>
              <image width="$width" height="$height" href="data:image/png;base64,$encoded"/>
            </svg>
            """.trimIndent(),
        )
        append('\n')
    }
}

internal fun mermaidNodeId(seed: String): String {
    val sanitized = seed.replace(Regex("[^A-Za-z0-9_]"), "_")
    return if (sanitized.firstOrNull()?.isDigit() == true) "n_$sanitized" else sanitized
}

internal fun mermaidLabel(label: String): String =
    label
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

internal fun escapeXml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

internal fun clipboardButtonLabel(format: ClipboardExportFormat): String = when (format) {
    ClipboardExportFormat.IMAGE -> "toolWindow.copyImage"
    ClipboardExportFormat.SVG -> "toolWindow.copySvg"
    ClipboardExportFormat.MERMAID -> "toolWindow.copyMermaid"
}

internal class ImageTransferable(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) {
            throw UnsupportedFlavorException(flavor)
        }
        return image
    }
}
