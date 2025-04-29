package net.postchain.zkp.curve

import com.sun.jna.ptr.IntByReference
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP196
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP196.EIP196_PREALLOCATE_FOR_ERROR_BYTES
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP196.EIP196_PREALLOCATE_FOR_RESULT_BYTES
import java.math.BigInteger
import kotlin.text.Charsets.UTF_8

object BN128 : Curve {
    override val order = BigInteger("21888242871839275222246405745257275088548364400416034343698204186575808495617")
    override val fieldModulus = BigInteger("21888242871839275222246405745257275088696311157297823662689037894645226208583")
    override val generatorG1 = G1Point(BigInteger.ONE, BigInteger.TWO)
    override val infinityG1 = G1Point(BigInteger.ZERO, BigInteger.ZERO)
    override val generatorG2 = G2Point(
            BigInteger("10857046999023057135944570762232829481370756359578518086990519993285655852781"),
            BigInteger("11559732032986387107991004021392285783925812861821192530917403151452391805634"),
            BigInteger("8495653923123431417604973247489272438418190587263600148770280649306958101930"),
            BigInteger("4082367875863433681332203403145435568316851327593401208105741076214120093531")
    )
    override val infinityG2 = G2Point(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
    override val rootsOfUnity: Array<BigInteger> = generateRootsOfUnity(order)

    override fun addPoints(point1: G1Point, point2: G1Point): G1Point {
        val input = encodePoint(point1) + encodePoint(point2)
        val (outputLength, output) = performCurveOp(LibGnarkEIP196.EIP196_ADD_OPERATION_RAW_VALUE, input)

        if (outputLength != 64) {
            throw ProgrammerMistake("Unexpected output length from G1 addition: $outputLength")
        }
        return decodePoint(output)
    }

    override fun multiplyPoint(point: G1Point, scalar: BigInteger): G1Point {
        val input = encodePoint(point) + encodeScalar(scalar)
        val (outputLength, output) = performCurveOp(LibGnarkEIP196.EIP196_MUL_OPERATION_RAW_VALUE, input)

        if (outputLength != 64) {
            throw ProgrammerMistake("Unexpected output length from G1 multiplication: $outputLength")
        }
        return decodePoint(output)
    }

    override fun verifyPairingEquality(pointPairs: List<Pair<G1Point, G2Point>>): Boolean {
        val input = pointPairs.fold(ByteArray(0)) { acc, point -> acc + encodePoint(point.first) + encodePoint(point.second) }
        val (outputLength, output) = performCurveOp(LibGnarkEIP196.EIP196_PAIR_OPERATION_RAW_VALUE, input)

        return output[outputLength - 1] == 1.toByte()
    }

    private fun performCurveOp(opCode: Byte, input: ByteArray): Pair<Int, ByteArray> {
        val output = ByteArray(EIP196_PREALLOCATE_FOR_RESULT_BYTES)
        val outputLength = IntByReference()
        val error = ByteArray(EIP196_PREALLOCATE_FOR_ERROR_BYTES)
        val errorLength = IntByReference()

        LibGnarkEIP196.eip196_perform_operation(
                opCode,
                input,
                input.size,
                output,
                outputLength,
                error,
                errorLength
        )

        if (errorLength.value > 0) {
            throw UserMistake("BN128 curve operation failed: ${String(error, 0, errorLength.value, UTF_8)}")
        }

        return outputLength.value to output
    }

    override fun isPointOnCurve(point: G1Point): Boolean {
        // y^2 = x^3 + 3
        val x3_3 = (point.x.modPow(BigInteger.valueOf(3), fieldModulus) + BigInteger.valueOf(3)) % fieldModulus
        val y2 = point.y * point.y % fieldModulus
        return x3_3 == y2
    }

    override fun encodePoint(point: G1Point): ByteArray {
        val xBytes = point.x.toLeftPaddedBytes(32)
        val yBytes = point.y.toLeftPaddedBytes(32)
        return xBytes + yBytes
    }

    override fun encodeScalar(scalar: BigInteger) = scalar.toLeftPaddedBytes(32)

    private fun encodePoint(p: G2Point): ByteArray {
        val x1Bytes = p.x1.toLeftPaddedBytes(32)
        val x2Bytes = p.x2.toLeftPaddedBytes(32)
        val y1Bytes = p.y1.toLeftPaddedBytes(32)
        val y2Bytes = p.y2.toLeftPaddedBytes(32)
        return x2Bytes + x1Bytes + y2Bytes + y1Bytes // 2 is encoded before 1, no idea why, not intuitive
    }

    private fun decodePoint(bytes: ByteArray): G1Point {
        val xBytes = bytes.slice(0..31).toByteArray()
        val yBytes = bytes.slice(32..63).toByteArray()
        return G1Point(BigInteger(1, xBytes), BigInteger(1, yBytes))
    }
}
