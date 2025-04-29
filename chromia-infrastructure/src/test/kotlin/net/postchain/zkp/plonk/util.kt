package net.postchain.zkp.plonk

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.zkp.curve.Curve
import net.postchain.zkp.curve.G1Point
import net.postchain.zkp.curve.G2Point

fun snarkJsVerificationKeyJSONToGtv(gson: Gson, rawVkJSON: String): Gtv {
    val vkJSON = gson.fromJson(rawVkJSON, JsonObject::class.java)
    val curveName = vkJSON.get("curve").asString
    val curve = PlonkVerificationKey.CurveName.valueOf(curveName).getCurve()
    return gtv(mapOf(
            "curve" to gtv(curveName),
            "nPublic" to gtv(vkJSON.get("nPublic").asInt.toLong()),
            "power" to gtv(vkJSON.get("power").asInt.toLong()),
            "Qm" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("Qm").asJsonArray),
            "Ql" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("Ql").asJsonArray),
            "Qr" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("Qr").asJsonArray),
            "Qo" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("Qo").asJsonArray),
            "Qc" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("Qc").asJsonArray),
            "S1" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("S1").asJsonArray),
            "S2" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("S2").asJsonArray),
            "S3" to convertSnarkJsG1PointToGtvDict(curve, vkJSON.get("S3").asJsonArray),
            "k1" to gtv(vkJSON.get("k1").asBigInteger),
            "k2" to gtv(vkJSON.get("k2").asBigInteger),
            "X_2" to convertSnarkJsG2PointToGtvDict(curve, vkJSON.get("X_2").asJsonArray),
    ))
}

fun snarkJsProofJSONToGtv(gson: Gson, curve: Curve, rawProofJSON: String): GtvArray {
    val proofJSON = gson.fromJson(rawProofJSON, JsonObject::class.java)
    return GtvArray(arrayOf(
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("A").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("B").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("C").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("Z").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("T1").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("T2").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("T3").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("Wxi").asJsonArray),
            convertSnarkJsG1PointToGtvArray(curve, proofJSON.get("Wxiw").asJsonArray),
            gtv(proofJSON.get("eval_a").asBigInteger),
            gtv(proofJSON.get("eval_b").asBigInteger),
            gtv(proofJSON.get("eval_c").asBigInteger),
            gtv(proofJSON.get("eval_s1").asBigInteger),
            gtv(proofJSON.get("eval_s2").asBigInteger),
            gtv(proofJSON.get("eval_zw").asBigInteger),
    ))
}

private fun convertSnarkJsG1PointToGtvDict(curve: Curve, snarkG1Point: JsonArray) = GtvObjectMapper.toGtvDictionary(
        convertSnarkJSG1Point(curve, snarkG1Point)
)

private fun convertSnarkJsG1PointToGtvArray(curve: Curve, snarkG1Point: JsonArray): GtvArray {
    val convertedPoint = convertSnarkJSG1Point(curve, snarkG1Point)
    return GtvArray(arrayOf(gtv(convertedPoint.x), gtv(convertedPoint.y)))
}

private fun convertSnarkJSG1Point(curve: Curve, snarkG1Point: JsonArray) = G1Point.createG1Point(
        curve,
        snarkG1Point.get(0).asBigInteger,
        snarkG1Point.get(1).asBigInteger,
        snarkG1Point.get(2).asBigInteger,
)

private fun convertSnarkJsG2PointToGtvDict(curve: Curve, snarkG2Point: JsonArray): Gtv {
    val xPoints = snarkG2Point.get(0).asJsonArray.map { it.asBigInteger }
    val yPoints = snarkG2Point.get(1).asJsonArray.map { it.asBigInteger }
    val zPoints = snarkG2Point.get(2).asJsonArray.map { it.asBigInteger }

    return GtvObjectMapper.toGtvDictionary(
            G2Point.createG2Point(
                    curve,
                    xPoints[0],
                    xPoints[1],
                    yPoints[0],
                    yPoints[1],
                    zPoints[0],
                    zPoints[1],
            )
    )
}