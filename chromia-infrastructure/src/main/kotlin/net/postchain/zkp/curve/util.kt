package net.postchain.zkp.curve

import java.math.BigInteger

/**
 * Generates an array of primitive roots of unity for different power levels.
 *
 * This function computes roots of unity required for Fast Fourier Transform (FFT).
 * Each root ω at index i satisfies ω^(2^i) = 1 mod curve.order.
 *
 * @param curveOrder The elliptic curve order
 * @return An array of primitive roots of unity where index i contains a 2^i-th root of unity
 */
fun generateRootsOfUnity(curveOrder: BigInteger): Array<BigInteger> {
    val orderMinusOne = curveOrder - BigInteger.ONE
    var cofactor = orderMinusOne
    var maxExponent = 0

    // Find the highest power of 2 that divides (order-1)
    // This determines the largest FFT domain we can support
    while (cofactor.mod(BigInteger.TWO) == BigInteger.ZERO) {
        maxExponent++
        cofactor = cofactor shr 1
    }

    // Find a non-quadratic residue in the field
    val halfOrder = orderMinusOne shr 1
    var nonQuadraticResidue = BigInteger.TWO
    var residueCheck = nonQuadraticResidue.modPow(halfOrder, curveOrder)

    while (residueCheck != orderMinusOne) {
        nonQuadraticResidue++
        residueCheck = nonQuadraticResidue.modPow(halfOrder, curveOrder)
    }

    // Calculate the primary root of unity of order 2^maxExponent
    val primaryRootOfUnity = nonQuadraticResidue.modPow(cofactor, curveOrder)

    val rootsOfUnity = Array(maxExponent + 1) { BigInteger.ZERO }

    // Store the primary root for the maximum domain size
    rootsOfUnity[maxExponent] = primaryRootOfUnity

    // Derive smaller roots of unity by squaring
    // If ω is a 2^m-th root of unity, then ω^2 is a 2^(m-1)-th root of unity
    for (i in maxExponent - 1 downTo 1) {
        rootsOfUnity[i] = rootsOfUnity[i + 1].modPow(BigInteger.TWO, curveOrder)
    }

    // The 2^0-th root of unity is always 1
    rootsOfUnity[0] = BigInteger.ONE

    return rootsOfUnity
}

fun BigInteger.toLeftPaddedBytes(size: Int): ByteArray =
        this.toByteArray().let { ByteArray(size) { i -> if (i < size - it.size) 0 else it[i - (size - it.size)] } }
