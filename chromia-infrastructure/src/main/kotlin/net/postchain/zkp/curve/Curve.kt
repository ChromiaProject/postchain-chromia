package net.postchain.zkp.curve

import java.math.BigInteger

/**
 * Represents an abstract elliptic curve interface with common operations for cryptographic purposes.
 *
 * This interface defines common properties and methods for elliptic curve types.
 * It supports operations for point addition, scalar multiplication, curve membership checks,
 * encoding, and pairing-based equality checks.
 */
interface Curve {
    val order: BigInteger
    val fieldModulus: BigInteger
    val generatorG1: G1Point
    val infinityG1: G1Point
    val generatorG2: G2Point
    val infinityG2: G2Point
    val rootsOfUnity: Array<BigInteger>

    /**
     * Adds two points on the G1 elliptic curve.
     *
     * @param point1 The first G1 curve point.
     * @param point2 The second G1 curve point.
     * @return The resulting G1 curve point obtained by adding `point1` and `point2`.
     */
    fun addPoints(point1: G1Point, point2: G1Point): G1Point

    /**
     * Multiplies a point on the G1 elliptic curve by a scalar value.
     *
     * @param point The G1 curve point to be multiplied.
     * @param scalar The scalar by which the `point` is multiplied.
     * @return The resulting G1 curve point after the scalar multiplication.
     */
    fun multiplyPoint(point: G1Point, scalar: BigInteger): G1Point

    /**
     * Verifies pairing-based equality for a given set of points.
     *
     * @param pointPairs A list of pairs containing G1Points and G2Points, where each pair represents
     *               corresponding elements from two elliptic curve groups to be checked for pairing
     *               equality.
     * @return True if the pairing equality holds for the given points; false otherwise.
     */
    fun verifyPairingEquality(pointPairs: List<Pair<G1Point, G2Point>>): Boolean

    fun isPointOnCurve(point: G1Point): Boolean

    fun encodePoint(point: G1Point): ByteArray
    fun encodeScalar(scalar: BigInteger): ByteArray
}
