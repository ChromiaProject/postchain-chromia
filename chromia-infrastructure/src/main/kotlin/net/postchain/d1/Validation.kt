package net.postchain.d1

import net.postchain.base.BaseBlockWitness
import net.postchain.base.BaseBlockWitnessBuilder
import net.postchain.common.data.Hash
import net.postchain.common.exception.UserMistake
import net.postchain.core.block.BlockHeader
import net.postchain.crypto.CryptoSystem
import net.postchain.crypto.PubKey
import net.postchain.getBFTRequiredSignatureCount

object Validation {
    fun validateBlockSignatures(cryptoSystem: CryptoSystem,
                                previousBlockRid: ByteArray,
                                rawHeader: ByteArray,
                                blockRid: Hash,
                                peers: Collection<PubKey>,
                                witness: BaseBlockWitness) {
        val threshold = getBFTRequiredSignatureCount(peers.size)

        if (witness.getSignatures().size < threshold) {
            throw UserMistake("Insufficient number of witness (needs at least $threshold but got only ${witness.getSignatures().size})")
        }

        val blockWitnessBuilder = BaseBlockWitnessBuilder(cryptoSystem, object : BlockHeader {
            override val prevBlockRID = previousBlockRid
            override val rawData = rawHeader
            override val blockRID = blockRid
        }, peers.map { it.data }.toTypedArray(), threshold)

        for (signature in witness.getSignatures()) {
            blockWitnessBuilder.applySignature(signature)
        }

        if (!blockWitnessBuilder.isComplete()) {
            throw UserMistake("Insufficient number of valid witness signatures")
        }
    }
}
