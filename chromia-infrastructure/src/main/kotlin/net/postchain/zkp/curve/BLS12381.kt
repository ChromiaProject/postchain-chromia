package net.postchain.zkp.curve

import com.sun.jna.ptr.IntByReference
import net.postchain.common.exception.ProgrammerMistake
import net.postchain.common.exception.UserMistake
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP2537
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP2537.EIP2537_PREALLOCATE_FOR_ERROR_BYTES
import org.hyperledger.besu.nativelib.gnark.LibGnarkEIP2537.EIP2537_PREALLOCATE_FOR_RESULT_BYTES
import java.math.BigInteger
import kotlin.text.Charsets.UTF_8

object BLS12381 : Curve {
    override val order = BigInteger("52435875175126190479447740508185965837690552500527637822603658699938581184513")
    override val fieldModulus = BigInteger("4002409555221667393417789825735904156556882819939007885332058136124031650490837864442687629129015664037894272559787")
    override val generatorG1 = G1Point(
            BigInteger("3685416753713387016781088315183077757961620795782546409894578378688607592378376318836054947676345821548104185464507"),
            BigInteger("1339506544944476473020471379941921221584933875938349620426543736416511423956333506472724655353366534992391756441569"),
    )
    override val infinityG1 = G1Point(BigInteger.ZERO, BigInteger.ZERO)
    override val generatorG2 = G2Point(
            BigInteger("352701069587466618187139116011060144890029952792775240219908644239793785735715026873347600343865175952761926303160"),
            BigInteger("3059144344244213709971259814753781636986470325476647558659373206291635324768958432433509563104347017837885763365758"),
            BigInteger("1985150602287291935568054521177171638300868978215655730859378665066344726373823718423869104263333984641494340347905"),
            BigInteger("927553665492332455747201965776037880757740193453592970025027978793976877002675564980949289727957565575433344219582")
    )
    override val infinityG2 = G2Point(BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO)
    override val rootsOfUnity: Array<BigInteger> = generateRootsOfUnity(order)

    override fun addPoints(point1: G1Point, point2: G1Point): G1Point {
        val input = encodePointForBesu(point1) + encodePointForBesu(point2)
        val (outputLength, output) = performCurveOp(LibGnarkEIP2537.BLS12_G1ADD_OPERATION_SHIM_VALUE, input)

        if (outputLength != 128) {
            throw ProgrammerMistake("Unexpected output length from G1 addition: $outputLength")
        }
        return decodePoint(output)
    }

    override fun multiplyPoint(point: G1Point, scalar: BigInteger): G1Point {
        val input = encodePointForBesu(point) + encodeScalar(scalar)
        val (outputLength, output) = performCurveOp(LibGnarkEIP2537.BLS12_G1MUL_OPERATION_SHIM_VALUE, input)

        if (outputLength != 128) {
            throw ProgrammerMistake("Unexpected output length from G1 multiplication: $outputLength")
        }
        return decodePoint(output)
    }

    override fun verifyPairingEquality(pointPairs: List<Pair<G1Point, G2Point>>): Boolean {
        val input = pointPairs.fold(ByteArray(0)) { acc, point -> acc + encodePointForBesu(point.first) + encodePoint(point.second) }
        val (outputLength, output) = performCurveOp(LibGnarkEIP2537.BLS12_PAIR_OPERATION_SHIM_VALUE, input)

        return output[outputLength - 1] == 1.toByte()
    }

    private fun performCurveOp(opCode: Byte, input: ByteArray): Pair<Int, ByteArray> {
        val output = ByteArray(EIP2537_PREALLOCATE_FOR_RESULT_BYTES)
        val outputLength = IntByReference()
        val error = ByteArray(EIP2537_PREALLOCATE_FOR_ERROR_BYTES)
        val errorLength = IntByReference()

        LibGnarkEIP2537.eip2537_perform_operation(
                opCode,
                input,
                input.size,
                output,
                outputLength,
                error,
                errorLength
        )

        if (errorLength.value > 0) {
            throw UserMistake("BLS12-381 curve operation failed: ${String(error, 0, errorLength.value, UTF_8)}")
        }

        return outputLength.value to output
    }

    override fun isPointOnCurve(point: G1Point): Boolean {
        // y^2 = x^3 + 4
        val x3_4 = (point.x.modPow(BigInteger.valueOf(3), fieldModulus) + BigInteger.valueOf(4)) % fieldModulus
        val y2 = point.y * point.y % fieldModulus
        return x3_4 == y2
    }

    override fun encodePoint(point: G1Point): ByteArray {
        val xBytes = point.x.toLeftPaddedBytes(48)
        val yBytes = point.y.toLeftPaddedBytes(48)
        return xBytes + yBytes
    }

    override fun encodeScalar(scalar: BigInteger) = scalar.toLeftPaddedBytes(32)

    /**
     * Besu lib has its own encoding of points for BLS-12381 that differs from what we want in plonk calculations
     */
    private fun encodePointForBesu(p: G1Point): ByteArray {
        val xBytes = p.x.toLeftPaddedBytes(64)
        val yBytes = p.y.toLeftPaddedBytes(64)
        return xBytes + yBytes
    }

    private fun encodePoint(p: G2Point): ByteArray {
        val x1Bytes = p.x1.toLeftPaddedBytes(64)
        val x2Bytes = p.x2.toLeftPaddedBytes(64)
        val y1Bytes = p.y1.toLeftPaddedBytes(64)
        val y2Bytes = p.y2.toLeftPaddedBytes(64)
        return x1Bytes + x2Bytes + y1Bytes + y2Bytes
    }

    private fun decodePoint(bytes: ByteArray): G1Point {
        val xBytes = bytes.slice(0..63).toByteArray()
        val yBytes = bytes.slice(64..127).toByteArray()
        return G1Point(BigInteger(1, xBytes), BigInteger(1, yBytes))
    }
}
