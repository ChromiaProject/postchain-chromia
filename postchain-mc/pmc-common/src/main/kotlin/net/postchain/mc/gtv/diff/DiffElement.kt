package net.postchain.mc.gtv.diff

interface DiffElement {
    val equals: Boolean
    val diff: String
}