package net.postchain.zkp

import net.postchain.zkp.plonk.PlonkVerificationKey

class ZKPGTXModuleContext {
    lateinit var plonk: ZKPGTXPlonkModuleContext
}

data class ZKPGTXPlonkModuleContext(
        val verificationKeys: Map<String, PlonkVerificationKey>
)
