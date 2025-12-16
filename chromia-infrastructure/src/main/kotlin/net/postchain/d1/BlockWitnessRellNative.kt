package net.postchain.d1

import net.postchain.base.BaseBlockWitness
import net.postchain.common.exception.UserMistake
import net.postchain.crypto.Signature
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.rell.api.nativ.RellNativeEnvironment

class BlockWitnessRellNative(val env: RellNativeEnvironment) {
    // @native function encode0(witness: gtv): byte_array;
    fun encode0(witness: Gtv): ByteArray =
            BaseBlockWitness.fromSignatures(witness.asArray().map {
                Signature(
                        it["subjectID"]?.asByteArray() ?: throw UserMistake("missing signature subjectID"),
                        it["data"]?.asByteArray() ?: throw UserMistake("missing signature data"),
                )
            }.toTypedArray()).getRawData()

    // @native function decode0(data: byte_array): gtv;
    fun decode0(data: ByteArray): Gtv =
            gtv(BaseBlockWitness.fromBytes(data).getSignatures().map {
                gtv(mapOf("subjectID" to gtv(it.subjectID), "data" to gtv(it.data)))
            })
}
