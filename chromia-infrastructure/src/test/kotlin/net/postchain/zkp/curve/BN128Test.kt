package net.postchain.zkp.curve

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

/**
 * All test data is taken from besu repository.
 *
 * We don't want to test that the calculations are correct here, just that we call and handle responses from the API
 * correctly.
 */
class BN128Test {

    @Test
    fun `Add G1 points`() {
        val input = "1b43c36e6eb9566ae50c78f79802a80a963cf4317079c9e201361b1afbd64d2b2796ffa55aaf5946e40b8038cda20238c53f328d5e4150287564eb08ce1efa590d4d3c9a95606d838edb0b494a64d5ad5933335cd7acf5ee9409492135b44fed2d02a128bfb4cb61db495d5389feba0c4943e8c8c936acf7231fc8edefb619b5".hexStringToByteArray()
        val expectedOutput = "29a99e2465491ddb57b131ea6d0469f4faaa4202bf870b3e82365b9c3a59cf8227d26a8780d9d0c30452f13d9673e2d9cae926a2158b62f8cf4804adaa112e98".hexStringToByteArray()

        val inputPoint1 = decodeG1Point(input.sliceArray(0..63))
        val inputPoint2 = decodeG1Point(input.sliceArray(64..127))
        val outputPoint = decodeG1Point(expectedOutput)

        val result = BN128.addPoints(inputPoint1, inputPoint2)

        assertThat(result).isEqualTo(outputPoint)
    }

    @Test
    fun `Adding G1 point that is not on curve throws exception`() {
        val input = "0174fc233104c2ad4f56a8396b8c1b7d9c6ad10bffc70761c5e8f5280862f137029733a9f20a4cdbb7ae9c5dd1adf6ccc7fe3439d7dc71093af0656ae0ca0f290964773f12e2292f332306374f957d10000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000".hexStringToByteArray()

        val inputPoint1 = decodeG1Point(input.sliceArray(0..63))
        val inputPoint2 = decodeG1Point(input.sliceArray(64..127))

        val exception = assertThrows<UserMistake> {
            BN128.addPoints(inputPoint1, inputPoint2)
        }

        assertThat(exception.message!!).contains("point is not on curve")
    }

    @Test
    fun `Multiply G1 point`() {
        val input = "30005d82f093499b9fa478a525daaa1008a848343a9f7250bfbeb7ba4238522502722e6335664879c0e42d01e5512fa9ce2650059cba069f8893074beb49367a18c2d54e16d2b30e3c720090149f62cb3b46090d089237a051b85495d9a3a8ca".hexStringToByteArray()
        val expectedOutput = "168f142b66703dd6b5bf32f5d10565cc930edd1bccb6d56c5589a2f2c947604e03486e8f77ed536e7be3aa3e02162950c326b5dbfc0b76a34e985971186bf86a".hexStringToByteArray()

        val inputPoint = decodeG1Point(input.sliceArray(0..63))
        val scalar = BigInteger(1, input.sliceArray(64..95))
        val outputPoint = decodeG1Point(expectedOutput)

        val result = BN128.multiplyPoint(inputPoint, scalar)

        assertThat(result).isEqualTo(outputPoint)
    }

    @Test
    fun `Multiplying G1 point that is not on curve throws exception`() {
        val input = "02acf800b3ba0ff68ef8d5fd4d6c250d3e70b3bed17894f958579644c83fa9d405121d580e2b061c697e68f9502977680d6ad8e12b4f61e3e2a2252ce11428941f2a84b7f0a821cb8cc7699303bd4fec2247870562618fd8d6169072d9b33614".hexStringToByteArray()

        val inputPoint = decodeG1Point(input.sliceArray(0..63))
        val scalar = BigInteger(1, input.sliceArray(64..95))

        val exception = assertThrows<UserMistake> {
            BN128.multiplyPoint(inputPoint, scalar)
        }

        assertThat(exception.message!!).contains("point is not on curve")
    }

    @Test
    fun `Successful pairing check`() {
        val input = "1c76476f4def4bb94541d57ebba1193381ffa7aa76ada664dd31c16024c43f593034dd2920f673e204fee2811c678745fc819b55d3e9d294e45c9b03a76aef41209dd15ebff5d46c4bd888e51a93cf99a7329636c63514396b4a452003a35bf704bf11ca01483bfa8b34b43561848d28905960114c8ac04049af4b6315a416782bb8324af6cfc93537a2ad1a445cfd0ca2a71acd7ac41fadbf933c2a51be344d120a2a4cf30c1bf9845f20c6fe39e07ea2cce61f0c9bb048165fe5e4de877550111e129f1cf1097710d41c4ac70fcdfa5ba2023c6ff1cbeac322de49d1b6df7c2032c61a830e3c17286de9462bf242fca2883585b93870a73853face6a6bf411198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c21800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa".hexStringToByteArray()

        val g1inputPoint1 = decodeG1Point(input.sliceArray(0..63))
        val g2inputPoint1 = decodeG2Point(input.sliceArray(64..191))
        val g1inputPoint2 = decodeG1Point(input.sliceArray(192..255))
        val g2inputPoint2 = decodeG2Point(input.sliceArray(256..383))

        val result = BN128.verifyPairingEquality(listOf(g1inputPoint1 to g2inputPoint1, g1inputPoint2 to g2inputPoint2))

        assertThat(result).isTrue()
    }

    @Test
    fun `Failed pairing check`() {
        val input = "1c76476f4def4bb94541d57ebba1193381ffa7aa76ada664dd31c16024c43f593034dd2920f673e204fee2811c678745fc819b55d3e9d294e45c9b03a76aef41209dd15ebff5d46c4bd888e51a93cf99a7329636c63514396b4a452003a35bf704bf11ca01483bfa8b34b43561848d28905960114c8ac04049af4b6315a416782bb8324af6cfc93537a2ad1a445cfd0ca2a71acd7ac41fadbf933c2a51be344d120a2a4cf30c1bf9845f20c6fe39e07ea2cce61f0c9bb048165fe5e4de877550111e129f1cf1097710d41c4ac70fcdfa5ba2023c6ff1cbeac322de49d1b6df7c103188585e2364128fe25c70558f1560f4f9350baf3959e603cc91486e110936198e9393920d483a7260bfb731fb5d25f1aa493335a9e71297e485b7aef312c21800deef121f1e76426a00665e5c4479674322d4f75edadd46debd5cd992f6ed090689d0585ff075ec9e99ad690c3395bc4b313370b38ef355acdadcd122975b12c85ea5db8c6deb4aab71808dcb408fe3d1e7690c43d37b4ce6cc0166fa7daa".hexStringToByteArray()

        val g1inputPoint1 = decodeG1Point(input.sliceArray(0..63))
        val g2inputPoint1 = decodeG2Point(input.sliceArray(64..191))
        val g1inputPoint2 = decodeG1Point(input.sliceArray(192..255))
        val g2inputPoint2 = decodeG2Point(input.sliceArray(256..383))

        val result = BN128.verifyPairingEquality(listOf(g1inputPoint1 to g2inputPoint1, g1inputPoint2 to g2inputPoint2))

        assertThat(result).isFalse()
    }

    @Test
    fun `Pairing check for point that is not on curve throws exception`() {
        val input = "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000e3ec5510d7c4ad06c584c12197f7da897e3a2cb4c3f38c516a9eb2a1923083d214ee4b4a1571cb20a8e0c456faf2d27ce3c1529d73709bd735c513ba3d7e18a0762587d6d8b5e91de95a596ce583c5f1a671c3430a7f209eaa562b777b57f2b1ca31d687bd757c4b14f17feddff6785f2a6c1d1a65bab833889324f255c6e62".hexStringToByteArray()

        val g1inputPoint1 = decodeG1Point(input.sliceArray(0..63))
        val g2inputPoint1 = decodeG2Point(input.sliceArray(64..191))

        val exception = assertThrows<UserMistake> {
            BN128.verifyPairingEquality(listOf(g1inputPoint1 to g2inputPoint1))
        }

        assertThat(exception.message!!).contains("point is not on curve")
    }

    private fun decodeG1Point(bytes: ByteArray): G1Point {
        val xBytes = bytes.slice(0..31).toByteArray()
        val yBytes = bytes.slice(32..63).toByteArray()
        return G1Point(BigInteger(1, xBytes), BigInteger(1, yBytes))
    }

    private fun decodeG2Point(bytes: ByteArray): G2Point {
        val x2Bytes = bytes.slice(0..31).toByteArray()
        val x1Bytes = bytes.slice(32..63).toByteArray()
        val y2Bytes = bytes.slice(64..95).toByteArray()
        val y1Bytes = bytes.slice(96..127).toByteArray()
        return G2Point(
                BigInteger(1, x1Bytes),
                BigInteger(1, x2Bytes),
                BigInteger(1, y1Bytes),
                BigInteger(1, y2Bytes)
        )
    }
}
