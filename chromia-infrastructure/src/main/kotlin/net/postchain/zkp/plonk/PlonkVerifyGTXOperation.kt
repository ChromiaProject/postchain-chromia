package net.postchain.zkp.plonk

import net.postchain.core.TxEContext
import net.postchain.d1.iccf.IccfProofTxMaterialBuilder.Companion.ICCF_OP_NAME
import net.postchain.zkp.ZKPGTXModuleContext
import net.postchain.gtv.GtvArray
import net.postchain.gtx.GTXOpMistake
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.GtxNop
import net.postchain.gtx.GtxTimeB
import net.postchain.gtx.data.ExtOpData

class PlonkVerifyGTXOperation(
        private val moduleContext: ZKPGTXModuleContext,
        opData: ExtOpData
) : GTXOperation(opData) {
    private val nonCustomOps = setOf(OP_NAME, ICCF_OP_NAME, GtxNop.OP_NAME, GtxTimeB.OP_NAME)

    companion object {
        const val OP_NAME = "zkp_plonk_verify"
    }

    override fun apply(ctx: TxEContext) = true

    override fun checkCorrectness() {
        // TODO: Implement a generic mechanism for GTX modules to register these operations in Postchain
        if (data.operations.all { nonCustomOps.contains(it.opName) }) {
            throw GTXOpMistake("Tx must contain other operations than $nonCustomOps", data)
        }
        if (data.operations.withIndex().any { (index, op) ->
                    index != data.opIndex && op.opName == OP_NAME && op.args[0] == data.args[0] && op.args[2] == data.args[2]
                }) {
            throw GTXOpMistake(
                    "Tx contains duplicate proofs, only one proof per verification key and unique public signals is allowed",
                    data
            )
        }
        if (data.args.size != 3) throw GTXOpMistake("Wrong number of arguments", data)

        val verificationKeyId = data.args[0].asString()
        val verificationKey = moduleContext.plonk.verificationKeys[verificationKeyId]
                ?: throw GTXOpMistake("Unknown verification key: $verificationKeyId", data)

        val proof = PlonkProof.fromGtvArray(data.args[1] as GtvArray)
        val publicSignals = data.args[2].asArray().map { it.asBigInteger() }.toTypedArray()

        // This will throw UserMistake if the proof is invalid
        PlonkVerifier.verifyProof(verificationKey, proof, publicSignals)
    }
}
