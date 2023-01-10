package net.postchain.mc.gtv.diff

import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator

object StringDiffFinder {

    fun diff(first: String, second: String): StringDiffElement {
        if (first == second) return StringDiffElement.equal()
        val firstList = first.split("\n")
        val secondList = second.split("\n")
        val diff = DiffRowGenerator.create()
                .oldTag { f -> "" }      //do not introduce markdown style for strikethrough ~
                .newTag { f -> "" }     //do not introduce markdown style for bold **
                .build()
                .generateDiffRows(firstList, secondList)
        return StringDiffElement.diff(diff.map {
            when (it.tag) {
                DiffRow.Tag.EQUAL -> ""
                DiffRow.Tag.INSERT -> "${secondList.indexOf(it.newLine)}+ ${it.newLine}"
                DiffRow.Tag.DELETE -> "${firstList.indexOf(it.oldLine)}- ${it.oldLine}"
                DiffRow.Tag.CHANGE -> "${firstList.indexOf(it.oldLine)}- ${it.oldLine}\n${secondList.indexOf(it.newLine)}+ ${it.newLine}"
                else -> ""
            }
        }.filter { it.isNotBlank() }.joinToString("\n"))
    }
}
