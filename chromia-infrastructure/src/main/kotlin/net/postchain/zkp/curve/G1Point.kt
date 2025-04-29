package net.postchain.zkp.curve

import net.postchain.common.exception.UserMistake
import net.postchain.gtv.mapper.Name
import java.math.BigInteger

data class G1Point(@Name("x") val x: BigInteger, @Name("y") val y: BigInteger) {
    companion object {
        /**
         * Creates a G1Point from coordinates that are either at infinity or already in affine form.
         * Only supports the case where z=0 (point at infinity) or z=1 (already in affine form).
         *
         * @param curve The elliptic curve context.
         * @param x The x-coordinate.
         * @param y The y-coordinate.
         * @param z The z-coordinate, must be 0 or 1.
         * @return A G1Point representing the specified coordinates.
         * @throws UserMistake if z is not 0 or 1.
         */
        fun createG1Point(curve: Curve, x: BigInteger, y: BigInteger, z: BigInteger): G1Point = if (z == BigInteger.ZERO) {
            // Return point at infinity
            curve.infinityG1
        } else if (z == BigInteger.ONE) {
            G1Point(x, y)
        } else throw UserMistake("This function only supports z=0 (infinity) or z=1 (affine form)")
    }
}
