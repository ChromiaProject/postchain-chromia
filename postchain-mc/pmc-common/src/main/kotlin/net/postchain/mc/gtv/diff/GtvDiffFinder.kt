package net.postchain.mc.gtv.diff

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvByteArray
import net.postchain.gtv.GtvDictionary
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvType

object GtvDiffFinder {

    fun findDictDiff(first: GtvDictionary, second: GtvDictionary): DiffResult {

        val removedElements = first.dict.filter { !second.dict.containsKey(it.key) }
                .map { it.key to StringDiffElement.diff("${it.key} was removed") }

        val addedElements = second.dict.filter { !first.dict.containsKey(it.key) }
                .map { it.key to StringDiffElement.diff("${it.key} was added: ${it.value}") }

        val changedElements = first.dict.filter { second.dict.containsKey(it.key) }
                .map { it.key to diff(it.value, second.dict[it.key]!!) }
                .filter { !it.second.equals }

        val result = (removedElements + addedElements + changedElements).toMap()
        return DiffResult(result)
    }

    fun diff(first: Gtv, second: Gtv): DiffElement {
        if (first.type != second.type) return GtvDiffElement.diff("Type changed from ${first.type} to ${second.type}")
        return when (first.type) {
            GtvType.NULL -> GtvDiffElement.equal()
            GtvType.BYTEARRAY -> byteArrayDiff(first as GtvByteArray, second as GtvByteArray)
            GtvType.INTEGER -> integerDiff(first as GtvInteger, second as GtvInteger)
            GtvType.BIGINTEGER -> bigIntegerDiff(first as GtvBigInteger, second as GtvBigInteger)
            GtvType.STRING -> StringDiffFinder.diff(first.asString(), second.asString())
            GtvType.ARRAY -> arrayDiff(first as GtvArray, second as GtvArray)
            GtvType.DICT -> dictDiff(first as GtvDictionary, second as GtvDictionary)
        }
    }

    private fun integerDiff(first: GtvInteger, second: GtvInteger): GtvDiffElement {
        return when {
            first.asInteger() == second.asInteger() -> GtvDiffElement.equal()
            else -> GtvDiffElement.diff("Value changed from $first to $second")
        }
    }

    private fun bigIntegerDiff(first: GtvBigInteger, second: GtvBigInteger): DiffElement {
        return when {
            first.asBigInteger() == second.asBigInteger() -> GtvDiffElement.equal()
            else -> GtvDiffElement.diff("Value changed from $first to $second")
        }
    }
    private fun byteArrayDiff(first: GtvByteArray, second: GtvByteArray): DiffElement {
        return if (first.bytearray.contentEquals(second.bytearray)) GtvDiffElement.equal() else GtvDiffElement.diff("Value changed from $first to $second")
    }

    private fun arrayDiff(first: GtvArray, second: GtvArray): DiffElement {
        return GtvDiffElement.equal()
    }

    private fun dictDiff(first: GtvDictionary, second: GtvDictionary): DiffElement {
        return GtvDiffElement.equal()
    }

    data class GtvDiffElement(override val equals: Boolean, override val diff: String): DiffElement {
        companion object {
            fun equal() = GtvDiffElement(true, "")
            fun diff(str: String) = GtvDiffElement(false, str)
        }
    }
}