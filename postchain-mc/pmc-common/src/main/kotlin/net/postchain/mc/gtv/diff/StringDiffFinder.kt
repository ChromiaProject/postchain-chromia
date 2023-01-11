package net.postchain.mc.gtv.diff

import com.github.difflib.DiffUtils
import com.github.difflib.patch.Chunk
import com.github.difflib.patch.DeltaType
import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator

object StringDiffFinder {

    fun diff(first: String, second: String): StringDiffElement {
        if (first == second) return StringDiffElement.equal()
        val diff = DiffUtils.diff(first, second, null)
                .deltas
                .joinToString("\n") {
                    when (it.type) {
                        DeltaType.CHANGE -> "${format(it.source, "-")}\n${format(it.target, "+")}"
                        DeltaType.DELETE -> format(it.source, "-")
                        DeltaType.INSERT -> format(it.target, "+")
                        else -> ""
                    }
                }
        return StringDiffElement.diff(diff)
    }

    private fun<T> format(chunk: Chunk<T>, marker: String) = chunk.lines.mapIndexed { index, t -> "${chunk.position+index}$marker $t" }.joinToString("\n")

}
