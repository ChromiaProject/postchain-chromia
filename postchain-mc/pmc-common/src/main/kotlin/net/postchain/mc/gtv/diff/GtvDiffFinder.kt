package net.postchain.mc.gtv.diff

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType
import net.postchain.common.wrap
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvType

object GtvDiffFinder {

    fun diff(first: Gtv, second: Gtv, prefix: String = ""): DiffElement {
        if (first.type != second.type) return GtvDiffElement.diff("Type changed from ${first.type} to ${second.type} with value $second")
        return when (first.type) {
            GtvType.NULL -> GtvDiffElement.equal()
            GtvType.BYTEARRAY -> objectDiff(first.asByteArray().wrap(), second.asByteArray().wrap())
            GtvType.INTEGER -> objectDiff(first.asInteger(), second.asInteger())
            GtvType.BIGINTEGER -> objectDiff(first.asBigInteger(), second.asBigInteger())
            GtvType.STRING -> StringDiffFinder.diff(first.asString(), second.asString())
            GtvType.ARRAY -> arrayDiff(first as GtvArray, second as GtvArray)
            GtvType.DICT -> findDictDiff(first as GtvDictionary, second as GtvDictionary, prefix)
        }
    }

    private fun objectDiff(first: Any, second: Any) = when (first) {
        second -> GtvDiffElement.equal()
        else -> GtvDiffElement.diff("Value changed from $first to $second")
    }

    private fun arrayDiff(first: GtvArray, second: GtvArray): DiffElement {
        val res = DiffUtils.diff(first.array.toList(), second.array.toList())
                .deltas
                .map {
                    when (it.type) {
                        DeltaType.CHANGE -> "Index ${it.source.position} was changed from ${it.source.lines} to ${it.target.lines}"
                        DeltaType.DELETE -> "Element(s) ${it.source.lines} was deleted from index ${it.source.position}"
                        DeltaType.INSERT -> "Element(s) ${it.target.lines} was added to index ${it.source.position}"
                        else -> ""
                    }
                }
        return ArrayDiffResult(res.map { StringDiffElement.diff(it) })
    }

    private fun findDictDiff(first: GtvDictionary, second: GtvDictionary, path: String): DiffElement {

        val removedElements = first.dict.filter { !second.dict.containsKey(it.key) }
                .map { it.key to StringDiffElement.diff("${it.key} was removed") }

        val addedElements = second.dict.filter { !first.dict.containsKey(it.key) }
                .map { it.key to StringDiffElement.diff("${it.key} was added: ${it.value}") }

        val changedElements = first.dict.filter { second.dict.containsKey(it.key) }
                .map { it.key to diff(it.value, second.dict[it.key]!!, it.key) }
                .filter { !it.second.equals }

        val result = (removedElements + addedElements + changedElements).toMap()
        return DictDiffResult(path, result)
    }

    data class GtvDiffElement(override val equals: Boolean, override val diff: String) : DiffElement {
        companion object {
            fun equal() = GtvDiffElement(true, "")
            fun diff(str: String) = GtvDiffElement(false, str)
        }
    }
}
