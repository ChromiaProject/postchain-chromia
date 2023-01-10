package net.postchain.mc.gtv.diff

object StringDiffFinder {

    /**
     * Finds the Lines that differs between two possibly multiline strings.
     * It currently does not find if a line has been moved or blank lines, only new/removed lines
     */
    fun diff(first: String, second: String): StringDiffElement {
        if (first == second) return StringDiffElement.equal()
        val firstList = first.split("\n")
        val secondList = second.split("\n")
        val removedLines = firstList.mapIndexed { index, s -> if (!secondList.contains(s)) index to s else null }
                .filterNotNull()
                .map { it.first to "${it.first + 1} - ${it.second}" }

        val addedLines = secondList.mapIndexed { index, s -> if (!firstList.contains(s)) index to s else null }
                .filterNotNull()
                .map { it.first to "${it.first + 1} + ${it.second}" }

        val result = (removedLines + addedLines).sortedBy { it.first }.joinToString("\n") { it.second }
        if (result.isBlank()) return StringDiffElement.equal()
        return StringDiffElement.diff(result)
    }
}
