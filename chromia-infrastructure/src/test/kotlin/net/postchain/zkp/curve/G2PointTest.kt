package net.postchain.zkp.curve

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.common.exception.UserMistake
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

class G2PointTest {

    @Test
    fun `createG2Point converts point at infinity correctly`() {
        // Test point at infinity (z = 0)
        val result = G2Point.createG2Point(
                BN128,
                BigInteger.ONE, BigInteger.TWO,  // x1, x2
                BigInteger.valueOf(3), BigInteger.valueOf(4),  // y1, y2
                BigInteger.ZERO, BigInteger.ZERO  // z1, z2
        )

        // Point at infinity should have all coordinates set to zero
        assertThat(result).isEqualTo(G2Point(
                BigInteger.ZERO, BigInteger.ZERO,
                BigInteger.ZERO, BigInteger.ZERO
        ))
    }

    @Test
    fun `createG2Point handles already affine point correctly`() {
        // Test point already in affine form (z = (1,0))
        val x1 = BigInteger("10857046999023057135944570762232829481370756359578518086990519993285655852781")
        val x2 = BigInteger("11559732032986387107991004021392285783925812861821192530917403151452391805634")
        val y1 = BigInteger("8495653923123431417604973247489272438418190587263600148770280649306958101930")
        val y2 = BigInteger("4082367875863433681332203403145435568316851327593401208105741076214120093531")

        val result = G2Point.createG2Point(
                BN128,
                x1, x2,
                y1, y2,
                BigInteger.ONE, BigInteger.ZERO
        )

        // Result should be the same as input
        assertThat(result).isEqualTo(G2Point(x1, x2, y1, y2))
    }

    @Test
    fun `createG2Point rejects other projective coordinates`() {
        // Test a point in projective coordinates
        val x1 = BigInteger("10857046999023057135944570762232829481370756359578518086990519993285655852781")
        val x2 = BigInteger("11559732032986387107991004021392285783925812861821192530917403151452391805634")
        val y1 = BigInteger("8495653923123431417604973247489272438418190587263600148770280649306958101930")
        val y2 = BigInteger("4082367875863433681332203403145435568316851327593401208105741076214120093531")
        val z1 = BigInteger("2")
        val z2 = BigInteger("3")

        // Call the method under test
        assertThrows<UserMistake> {
            G2Point.createG2Point(
                    BN128,
                    x1, x2,
                    y1, y2,
                    z1, z2
            )
        }
    }
}
