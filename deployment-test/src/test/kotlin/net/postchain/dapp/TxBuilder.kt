package net.postchain.dapp

import net.postchain.common.BlockchainRid
import net.postchain.crypto.KeyPair
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.crypto.Secp256k1SigMaker
import net.postchain.crypto.SigMaker
import net.postchain.gtv.Gtv
import net.postchain.gtx.Gtx
import net.postchain.gtx.GtxBuilder

class TxBuilder(private val brid: BlockchainRid, private val signer: SigMaker) {

    companion object {
        private val cryptoSystem = Secp256K1CryptoSystem()
    }

    fun build(tx: String, vararg args: Gtv): Gtx {
        val signer = signer as Secp256k1SigMaker
        return GtxBuilder(brid, listOf(signer.pubKey), cryptoSystem)
            .addNop()
            .addOperation(tx, *args)
            .finish()
            .sign(cryptoSystem.buildSigMaker(KeyPair(signer.pubKey, signer.privKey)))
            .buildGtx()
    }
}
