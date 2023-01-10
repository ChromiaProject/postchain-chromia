package net.postchain.mc.gtv.diff

import com.github.difflib.text.DiffRowGenerator

object StringDiffFinder {

    fun diff(first: String, second: String): StringDiffElement {
        if (first == second) return StringDiffElement.equal()
        val firstList = first.split("\n")
        val secondList = second.split("\n")
        val diff = DiffRowGenerator.create()
                .showInlineDiffs(true)
                .mergeOriginalRevised(true)
                .inlineDiffByWord(true)
                .oldTag{ f -> "~" }      //introduce markdown style for strikethrough
                .newTag{ f -> "**" }     //introduce markdown style for bold
                .build()
                .generateDiffRows(firstList, secondList)
        return StringDiffElement.diff(diff.joinToString("\n") { it.oldLine })
    }
}
