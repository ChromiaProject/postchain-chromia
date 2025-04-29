package net.postchain.zkp.plonk

import net.postchain.zkp.curve.BLS12381
import net.postchain.zkp.curve.BN128
import net.postchain.zkp.curve.G1Point
import net.postchain.zkp.curve.G2Point
import net.postchain.gtv.mapper.Name
import java.math.BigInteger

data class PlonkVerificationKey(
        @Name("curve")
        val curveName: CurveName,
        @Name("nPublic")
        val nPublicLong: Long,
        @Name("power")
        val powerLong: Long,
        @Name("Qm")
        val Qm: G1Point,
        @Name("Ql")
        val Ql: G1Point,
        @Name("Qr")
        val Qr: G1Point,
        @Name("Qo")
        val Qo: G1Point,
        @Name("Qc")
        val Qc: G1Point,
        @Name("S1")
        val S1: G1Point,
        @Name("S2")
        val S2: G1Point,
        @Name("S3")
        val S3: G1Point,
        @Name("k1")
        val k1: BigInteger,
        @Name("k2")
        val k2: BigInteger,
        @Name("X_2")
        val X2: G2Point
) {
    enum class CurveName {
        bls12381,
        bn128;

        fun getCurve() = when (this) {
            bls12381 -> BLS12381
            bn128 -> BN128
        }
    }

    // Since GTV Object mapper can't parse Int (and this way we can avoid toInt() everywhere else)
    val nPublic = nPublicLong.toInt()
    val power = powerLong.toInt()

    val n: BigInteger = BigInteger.valueOf(2).pow(power)

    val nLagrange = maxOf(nPublic, 1)

    val curve = curveName.getCurve()
}
