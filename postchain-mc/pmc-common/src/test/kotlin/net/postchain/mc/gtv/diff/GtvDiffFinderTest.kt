package net.postchain.mc.gtv.diff

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.hexStringToByteArray
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvBigInteger
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigInteger

internal class GtvDiffFinderTest {

    @ParameterizedTest
    @MethodSource("primitives")
    fun primitiveTypes(first: Gtv, second: Gtv, equal: Boolean) {
        assertThat(GtvDiffFinder.diff(first, second).equals).isEqualTo(equal)
    }

    @Test
    fun array() {
        assertThat(GtvDiffFinder.diff(gtv(gtv(1)), gtv(gtv(1))).equals).isTrue()
        val diff = GtvDiffFinder.diff(gtv(gtv(1), gtv(2), gtv(4)), gtv(gtv(1), gtv(2)))
        assertThat(diff.equals).isFalse()
    }

    @Test
    fun dict() {
        assertThat(GtvDiffFinder.diff(gtv("a" to gtv("b")), gtv("a" to gtv("b"))).equals).isTrue()
        val first = gtv(
                "a" to gtv("b"),
                "b" to gtv("c" to gtv(0)))
        val second = gtv(
                "a" to gtv("a"),
                "b" to gtv("c" to gtv(1)))
        val diff = GtvDiffFinder.diff(
                first,
                second)
        assertThat(diff.equals).isFalse()
        assertThat(GtvDiffFinder.diff(gtv("a" to gtv("b" to gtv(1))), gtv("a" to gtv("b" to gtv(2)))).equals).isFalse()
    }

    companion object {
        @JvmStatic
        fun primitives() = arrayOf(
                arrayOf(GtvNull, GtvNull, true),
                arrayOf(GtvNull, gtv(1), false),
                arrayOf(gtv(true), gtv(true), true),
                arrayOf(gtv(true), gtv(false), false),
                arrayOf(GtvBigInteger(BigInteger.valueOf(12)), GtvBigInteger(BigInteger.valueOf(12)), true),
                arrayOf(GtvBigInteger(BigInteger.valueOf(1)), GtvBigInteger(BigInteger.valueOf(2)), false),
                arrayOf(gtv("AA".hexStringToByteArray()), gtv("AA".hexStringToByteArray()), true),
                arrayOf(gtv("AA".hexStringToByteArray()), gtv("AB".hexStringToByteArray()), false),
                arrayOf(gtv("a"), gtv("a"), true),
                arrayOf(gtv("a"), gtv("b"), false),
        )
    }
}
