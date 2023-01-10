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

    @Test
    fun `Switching order of lines are not found`() {
        assertk.assert(StringDiffFinder.diff("a\nb", "b\na")).isEqualTo(StringDiffElement.diff("""
            ~a~
            b
            **a**
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
                    ~a~**b**
                """.trimIndent())),
                arrayOf("""
                    
                    
                    ab
                """.trimIndent(),
                        """
                    re
                    
                    45
                """.trimIndent(),
                        StringDiffElement.diff("""
                            **re**
                            
                            **45**
                            ~ab~
                            """.trimIndent()
                        ))
        )
    }
}
