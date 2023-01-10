package net.postchain.mc.gtv.diff

data class DiffResult(val res: Map<String, DiffElement>): DiffElement {
    companion object {
        fun filterDiff(unfiltered: Map<String, DiffElement>) = DiffResult(unfiltered.filter { !it.value.equals })
    }

    override val equals: Boolean
        get() = false
    override val diff: String
        get() = res.entries.joinToString("\n") { """
            Path: ${it.key}
            ${it.value.diff}
        """.trimIndent() }
}
