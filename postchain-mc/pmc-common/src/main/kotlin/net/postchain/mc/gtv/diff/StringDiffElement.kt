package net.postchain.mc.gtv.diff

data class StringDiffElement(override val equals: Boolean, override val diff: String): DiffElement {
    companion object {
        fun equal() = StringDiffElement(true, "")
        fun diff(str: String) = StringDiffElement(false, str)
    }
}
