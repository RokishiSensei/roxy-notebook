package com.lexi.wordbook.util

/** Minimal, dependency-free RFC 4180 CSV reader/writer used for import & export. */
object Csv {

    fun encode(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { escape(it) }).append('\n')
        rows.forEach { row ->
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    /** Parses CSV text (tolerating CRLF and a UTF-8 BOM) into a table of cells. */
    fun decode(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < clean.length && clean[i + 1] == '"' -> {
                        cell.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> cell.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row.add(cell.toString()); cell.clear()
                }
                c == '\n' -> {
                    row.add(cell.toString()); cell.clear()
                    rows.add(row); row = mutableListOf()
                }
                c == '\r' -> Unit
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    private fun escape(value: String): String {
        val v = value.replace("\r\n", "\n").replace('\r', '\n')
        return if (v.any { it == ',' || it == '"' || it == '\n' }) {
            '"' + v.replace("\"", "\"\"") + '"'
        } else {
            v
        }
    }
}
