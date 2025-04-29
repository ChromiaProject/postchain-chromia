package net.postchain.zkp.curve

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.exception.UserMistake
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

class G1PointTest {

    @Test
    fun `createG1Point converts point at infinity correctly`() {
        // Test point at infinity (z = 0)
        val result = G1Point.createG1Point(
                BN128,
                BigInteger.ONE,  // x
                BigInteger.TWO,  // y
                BigInteger.ZERO  // z
        )

        // Point at infinity should be the curve's infinity point
        assertThat(result).isEqualTo(BN128.infinityG1)
    }

    @Test
    fun `createG1Point handles already affine point correctly`() {
        // Test point already in affine form (z = 1)
        val x = BigInteger("1")
        val y = BigInteger("2")

        val result = G1Point.createG1Point(
                BN128,
                x, y,
                BigInteger.ONE
        )

        // Result should be the same as input
        assertThat(result).isEqualTo(G1Point(x, y))
    }

    @Test
    fun `createG1Point rejects other projective coordinates`() {
        // Test a point in projective coordinates
        val x = BigInteger("1947797958057089990081174216512463775704284562842809162881833792739115499985")
        val y = BigInteger("19406006430744529112134626036621020530567851323402615558370561720610315934395")
        val z = BigInteger("3")

        // Call the method under test
        assertThrows<UserMistake> {
            G1Point.createG1Point(
                    BN128,
                    x, y, z
            )
        }
    }
}
