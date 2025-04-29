package net.postchain.zkp.curve

import net.postchain.common.exception.UserMistake
import java.math.BigInteger

data class G2Point(
        val x1: BigInteger,
        val x2: BigInteger,
        val y1: BigInteger,
        val y2: BigInteger
) {
    companion object {
        /**
         * Creates a G2Point from coordinates that are either at infinity or already in affine form.
         * Only supports the case where z=(0,0) (point at infinity) or z=(1,0) (already in affine form).
         *
         * @param curve The elliptic curve context.
         * @param x1 The x1-coordinate.
         * @param x2 The x2-coordinate.
         * @param y1 The y1-coordinate.
         * @param y2 The y2-coordinate.
         * @param z1 The z1-coordinate, must be 0 or 1.
         * @param z2 The z2-coordinate, must be 0.
         * @return A G2Point representing the specified coordinates.
         * @throws UserMistake if z coordinate values are not supported.
         */
        fun createG2Point(
                curve: Curve,
                x1: BigInteger, x2: BigInteger,
                y1: BigInteger, y2: BigInteger,
                z1: BigInteger, z2: BigInteger
        ): G2Point = if (z1 == BigInteger.ZERO && z2 == BigInteger.ZERO) {
            // Return point at infinity
            curve.infinityG2
        } else if (z1 == BigInteger.ONE && z2 == BigInteger.ZERO) { // Check for affine form (z1=1, z2=0)
            G2Point(x1, x2, y1, y2)
        } else throw UserMistake("This function only supports z=(0,0) (infinity) or z=(1,0) (affine form)")
    }
}
