package net.postchain.mc.gtv.diff

import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class StringDiffFinderTest {

    @ParameterizedTest
    @MethodSource("strings")
    fun multiString(first: String, second: String, expected: StringDiffElement) {
        assertk.assert(StringDiffFinder.diff(first, second)).isEqualTo(expected)
    }

    @Test
    fun `Switching order of lines are found`() {
        assertk.assert(StringDiffFinder.diff("a\nb", "b\na")).isEqualTo(StringDiffElement.diff("""
            0- a
            1+ a
        """.trimIndent()))
    }

    @Test
    fun `Empty lines give incorrect line numbers`() {
        assertk.assert(StringDiffFinder.diff("a\n\nb\n\nc", "a\nb\nc")).isEqualTo(StringDiffElement.diff("""
            1- 
            3- 
        """.trimIndent()))
    }

    @Test
    fun `bad index test`() {
        assertk.assert(StringDiffFinder.diff("a\nb\na", "a\nb")).isEqualTo(StringDiffElement.diff("""
        2- a
    """.trimIndent()))
    }


    companion object {
        @JvmStatic
        fun strings() = arrayListOf(
                arrayOf("", "", StringDiffElement.equal()),
                arrayOf("""
                    a
                """.trimIndent(),
                        """
                    b
                """.trimIndent(),
                        StringDiffElement.diff("""
                    0- a
                    0+ b
                """.trimIndent())),
                arrayOf("""
                    
                    
                    ab
                """.trimIndent(),
                        """
                    re
                    
                    45
                """.trimIndent(),
                        StringDiffElement.diff("""
                            0+ re
                            1- 
                            2- ab
                            2+ 45
                            """.trimIndent()
                        ))
        )
    }
}
