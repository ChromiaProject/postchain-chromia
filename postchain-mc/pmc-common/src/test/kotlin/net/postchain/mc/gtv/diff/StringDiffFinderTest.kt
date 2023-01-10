package net.postchain.mc.gtv.diff

import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class StringDiffFinderTest {

    @ParameterizedTest
    @MethodSource("strings")
    fun multiString(first: String, second: String, expected: StringDiffElement) {
        assertk.assert(StringDiffFinder.diff(first, second)).isEqualTo(expected)
    }

    // This shows a problem with current algorithm. If/when algorithm is improved, this test *should* fail
    @Test
    fun `Switching order of lines are not found`() {
        assertk.assert(StringDiffFinder.diff("a\nb", "b\na")).isEqualTo(StringDiffElement.equal())
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
                    1 - a
                    1 + b
                """.trimIndent())),
                arrayOf("""
                    
                    
                    ab
                """.trimIndent(),
                        """
                    re
                    
                    45
                """.trimIndent(),
                        StringDiffElement.diff("""
                    1 + re
                    3 - ab
                    3 + 45
                """.trimIndent()
                        ))
        )
    }
}
