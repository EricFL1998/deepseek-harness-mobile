package com.deepseek.dsh

/**
 * 最小 ANSI 终端屏幕模型。
 *
 * 官方 `terminal-controller` 的 `output` 帧是原始终端字节流（含 ANSI 控制
 * 序列），`snapshot.screen` 是 Host 已渲染好的一屏文本。这里实现一个够用的
 * 屏幕模型：光标定位、清屏/清行、换行滚动、CR/BS/TAB、保存/恢复光标；
 * 忽略颜色等 SGR（纯文本渲染），能正确显示 vim/top 这类全屏 TUI 的布局。
 *
 * 只支持单色文本，宽字符（CJK）按 2 列占位处理。
 */
class TerminalBuffer(
    var cols: Int = 80,
    var rows: Int = 24,
    private val maxScrollback: Int = 1000,
) {
    private val screen = mutableListOf<CharArray>()
    private val scrollback = ArrayDeque<String>()

    private var cursorRow = 0
    private var cursorCol = 0
    private var savedRow = 0
    private var savedCol = 0

    init {
        resetScreen()
    }

    private fun blankRow(): CharArray = CharArray(cols) { ' ' }

    private fun resetScreen() {
        screen.clear()
        repeat(rows) { screen.add(blankRow()) }
        cursorRow = 0
        cursorCol = 0
        savedRow = 0
        savedCol = 0
    }

    /** 用 Host 渲染好的整屏文本替换当前屏幕。 */
    fun setScreen(text: String) {
        scrollback.clear()
        resetScreen()
        val lines = text.split('\n')
        val visible = lines.takeLast(rows)
        val above = lines.dropLast(visible.size)
        above.forEach { scrollback.addLast(it.trimEnd()) }
        while (scrollback.size > maxScrollback) scrollback.removeFirst()
        visible.forEachIndexed { index, line ->
            writePlainLine(index, line)
        }
        val last = visible.lastIndex.coerceAtLeast(0)
        cursorRow = last
        cursorCol = (visible.lastOrNull()?.length ?: 0).coerceIn(0, cols - 1)
    }

    /** 追加原始终端输出（含 ANSI 序列）。 */
    fun feed(data: String) {
        var i = 0
        while (i < data.length) {
            val ch = data[i]
            when {
                ch == '\u001b' -> {
                    i = handleEscape(data, i)
                    continue
                }
                ch == '\r' -> cursorCol = 0
                ch == '\n' -> newline()
                ch == '\b' -> if (cursorCol > 0) cursorCol--
                ch == '\t' -> cursorCol = ((cursorCol / 8) + 1) * 8
                ch.code < 0x20 -> Unit // 其它控制字符忽略
                else -> {
                    val cp = data.codePointAt(i)
                    val wide = isWide(cp)
                    writeCodePoint(cp, wide)
                    i += Character.charCount(cp)
                    continue
                }
            }
            i += 1
        }
    }

    /** 渲染成可展示文本：滚动历史 + 当前屏幕。 */
    fun render(): String {
        val builder = StringBuilder()
        scrollback.forEach { line ->
            if (line.isNotEmpty()) {
                builder.append(line).append('\n')
            }
        }
        screen.forEach { row ->
            builder.append(renderRow(row)).append('\n')
        }
        return builder.toString().trimEnd('\n')
    }

    private fun renderRow(row: CharArray): String {
        val builder = StringBuilder(row.size)
        row.forEach { cell ->
            // '\u0000' 是宽字符的占位格，不输出。
            if (cell != '\u0000') builder.append(cell)
        }
        return builder.toString().trimEnd()
    }

    private fun writePlainLine(rowIndex: Int, line: String) {
        if (rowIndex !in screen.indices) return
        val target = screen[rowIndex]
        var col = 0
        var i = 0
        while (i < line.length && col < cols) {
            val cp = line.codePointAt(i)
            val wide = isWide(cp)
            target[col] = cp.toChar()
            if (wide && col + 1 < cols) target[col + 1] = '\u0000'
            col += if (wide) 2 else 1
            i += Character.charCount(cp)
        }
    }

    private fun writeCodePoint(codePoint: Int, wide: Boolean) {
        val width = if (wide) 2 else 1
        if (cursorCol + width > cols) {
            newline()
        }
        val row = screen[cursorRow]
        row[cursorCol] = codePoint.toChar()
        if (wide && cursorCol + 1 < cols) {
            row[cursorCol + 1] = '\u0000'
        }
        cursorCol += width
    }

    private fun newline() {
        cursorRow += 1
        if (cursorRow >= rows) {
            // 滚出屏幕的一行进入历史。
            val line = renderRow(screen.first())
            screen.removeAt(0)
            screen.add(blankRow())
            cursorRow = rows - 1
            if (line.isNotBlank()) {
                scrollback.addLast(line.trimEnd())
                while (scrollback.size > maxScrollback) scrollback.removeFirst()
            }
        }
    }

    /** 解析 ESC 开头的控制序列，返回下一个未消费下标。 */
    private fun handleEscape(data: String, start: Int): Int {
        if (start + 1 >= data.length) return data.length
        return when (val kind = data[start + 1]) {
            '[' -> handleCsi(data, start + 2)
            ']' -> {
                // OSC：到 BEL 或 ESC \ 结束。
                var i = start + 2
                while (i < data.length) {
                    if (data[i] == '\u0007') return i + 1
                    if (data[i] == '\u001b' && i + 1 < data.length && data[i + 1] == '\\') {
                        return i + 2
                    }
                    i += 1
                }
                data.length
            }
            '(', ')', '*', '+' -> minOf(data.length, start + 3)
            '7' -> {
                savedRow = cursorRow
                savedCol = cursorCol
                start + 2
            }
            '8' -> {
                cursorRow = savedRow.coerceIn(0, rows - 1)
                cursorCol = savedCol.coerceIn(0, cols - 1)
                start + 2
            }
            else -> start + 2
        }
    }

    private fun handleCsi(data: String, start: Int): Int {
        var i = start
        val params = StringBuilder()
        while (i < data.length) {
            val c = data[i]
            if (c in '0'..'9' || c == ';' || c == '?' || c == '>' || c == '<' || c == '=') {
                params.append(c)
                i += 1
            } else {
                break
            }
        }
        if (i >= data.length) return data.length
        // 中间字节（空格、! 等）跳过。
        while (i < data.length && data[i] in ' '..'/') i += 1
        if (i >= data.length) return data.length
        val final = data[i]
        val args = params.toString()
            .removePrefix("?")
            .removePrefix(">")
            .removePrefix("<")
            .removePrefix("=")
            .split(';')
            .map { it.toIntOrNull() ?: 0 }
        applyCsi(final, args)
        return i + 1
    }

    private fun applyCsi(final: Char, args: List<Int>) {
        fun n(index: Int, default: Int): Int {
            val value = args.getOrNull(index) ?: 0
            return if (value <= 0) default else value
        }
        when (final) {
            'H', 'f' -> {
                cursorRow = (n(0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (n(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'A' -> cursorRow = (cursorRow - n(0, 1)).coerceIn(0, rows - 1)
            'B' -> cursorRow = (cursorRow + n(0, 1)).coerceIn(0, rows - 1)
            'C' -> cursorCol = (cursorCol + n(0, 1)).coerceIn(0, cols - 1)
            'D' -> cursorCol = (cursorCol - n(0, 1)).coerceIn(0, cols - 1)
            'E' -> {
                cursorRow = (cursorRow + n(0, 1)).coerceIn(0, rows - 1)
                cursorCol = 0
            }
            'F' -> {
                cursorRow = (cursorRow - n(0, 1)).coerceIn(0, rows - 1)
                cursorCol = 0
            }
            'G' -> cursorCol = (n(0, 1) - 1).coerceIn(0, cols - 1)
            'd' -> cursorRow = (n(0, 1) - 1).coerceIn(0, rows - 1)
            'J' -> eraseDisplay(args.getOrNull(0) ?: 0)
            'K' -> eraseLine(args.getOrNull(0) ?: 0)
            'L' -> {
                val count = n(0, 1)
                repeat(count) {
                    screen.add(cursorRow, blankRow())
                    screen.removeAt(screen.lastIndex)
                }
            }
            'M' -> {
                val count = n(0, 1)
                repeat(count) {
                    screen.removeAt(cursorRow)
                    screen.add(blankRow())
                }
            }
            'P' -> {
                val count = n(0, 1)
                val row = screen[cursorRow]
                for (offset in 0 until count) {
                    if (cursorCol + offset < cols) row[cursorCol + offset] = ' '
                }
            }
            'X' -> {
                val count = n(0, 1)
                val row = screen[cursorRow]
                for (offset in 0 until count) {
                    if (cursorCol + offset < cols) row[cursorCol + offset] = ' '
                }
            }
            'S' -> repeat(n(0, 1)) {
                screen.removeAt(0)
                screen.add(blankRow())
            }
            'T' -> repeat(n(0, 1)) {
                screen.removeAt(screen.lastIndex)
                screen.add(0, blankRow())
            }
            's' -> {
                savedRow = cursorRow
                savedCol = cursorCol
            }
            'u' -> {
                cursorRow = savedRow.coerceIn(0, rows - 1)
                cursorCol = savedCol.coerceIn(0, cols - 1)
            }
            else -> Unit // SGR 'm'、模式设置等忽略
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (row in cursorRow + 1 until rows) screen[row] = blankRow()
            }
            1 -> {
                eraseLine(1)
                for (row in 0 until cursorRow) screen[row] = blankRow()
            }
            else -> {
                for (row in 0 until rows) screen[row] = blankRow()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        val row = screen[cursorRow]
        when (mode) {
            1 -> for (col in 0..cursorCol.coerceAtMost(cols - 1)) row[col] = ' '
            2 -> for (col in 0 until cols) row[col] = ' '
            else -> for (col in cursorCol until cols) row[col] = ' '
        }
    }

    /** 粗略的宽字符判断（CJK / 全角标点）。 */
    private fun isWide(codePoint: Int): Boolean {
        return codePoint in 0x1100..0x115F ||
            codePoint in 0x2E80..0xA4CF ||
            codePoint in 0xAC00..0xD7A3 ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0xFE30..0xFE4F ||
            codePoint in 0xFF00..0xFF60 ||
            codePoint in 0xFFE0..0xFFE6 ||
            codePoint in 0x1F300..0x1F64F ||
            codePoint in 0x1F900..0x1F9FF ||
            codePoint in 0x20000..0x3FFFD
    }
}
