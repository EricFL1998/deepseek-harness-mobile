package com.deepseek.dsh.tooling.frontendpatch

import java.io.File

/**
 * v4.0 设计文档 §7.3:
 * 前端补丁自动化注入器：在 dist/index.html 的启动脚本前插入 native-surface.js
 */
object FrontendPatcher {

    fun injectPatch(
        htmlFile: File,
        patchScriptContent: String,
        outputHtmlFile: File = htmlFile
    ): Result<Unit> {
        if (!htmlFile.exists()) {
            return Result.failure(IllegalArgumentException("Target HTML file not found: ${htmlFile.absolutePath}"))
        }

        try {
            val originalHtml = htmlFile.readText(Charsets.UTF_8)
            if (originalHtml.contains("__DSH_NATIVE_SURFACE__")) {
                // 已注入，直接返回
                return Result.success(Unit)
            }

            // 在 </head> 之前注入内联补丁
            val patchTag = "<script>\n$patchScriptContent\n</script>\n</head>"
            val patchedHtml = if (originalHtml.contains("</head>", ignoreCase = true)) {
                originalHtml.replace("</head>", patchTag)
            } else {
                "<script>\n$patchScriptContent\n</script>\n$originalHtml"
            }

            outputHtmlFile.writeText(patchedHtml, Charsets.UTF_8)
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
