package net.postchain.mc.gtv.diff

data class DictDiffResult(val path: String, val res: Map<String, DiffElement>): DiffElement {
    companion object {
    }

    override val equals: Boolean
        get() = false
    override val diff: String
        get() = res.entries.joinToString("\n") { if (it.value is DictDiffResult) it.value.diff else
            "Path: ${if (path.isBlank()) it.key else "$path/${it.key}"}\n${it.value.diff}" }
}
