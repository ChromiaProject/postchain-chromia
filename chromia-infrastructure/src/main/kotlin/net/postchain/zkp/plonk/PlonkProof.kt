package net.postchain.zkp.plonk

import net.postchain.zkp.curve.G1Point
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import java.math.BigInteger

data class PlonkProof(
        val A: G1Point,
        val B: G1Point,
        val C: G1Point,
        val Z: G1Point,
        val T1: G1Point,
        val T2: G1Point,
        val T3: G1Point,
        val Wxi: G1Point,
        val Wxiw: G1Point,
        val evalA: BigInteger,
        val evalB: BigInteger,
        val evalC: BigInteger,
        val evalS1: BigInteger,
        val evalS2: BigInteger,
        val evalZw: BigInteger,
) {
    companion object {
        fun fromGtvArray(gtv: GtvArray) = PlonkProof(
                decodeGtvPoint(gtv[0]),
                decodeGtvPoint(gtv[1]),
                decodeGtvPoint(gtv[2]),
                decodeGtvPoint(gtv[3]),
                decodeGtvPoint(gtv[4]),
                decodeGtvPoint(gtv[5]),
                decodeGtvPoint(gtv[6]),
                decodeGtvPoint(gtv[7]),
                decodeGtvPoint(gtv[8]),
                gtv[9].asBigInteger(),
                gtv[10].asBigInteger(),
                gtv[11].asBigInteger(),
                gtv[12].asBigInteger(),
                gtv[13].asBigInteger(),
                gtv[14].asBigInteger(),
        )

        private fun decodeGtvPoint(gtv: Gtv) = gtv.asArray().let {
            G1Point(gtv[0].asBigInteger(), gtv[1].asBigInteger())
        }
    }
}
