package net.postchain.d1

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import java.io.File

val RELL_SOURCE_PATH = File("../chain0-impl/rell/src")

fun getSystemAnchoringChainConfig(): Gtv {
    val anchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_common/module.rell").readText()
    val systemAnchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_system/module.rell").readText()
    return GtvMLParser.parseGtvML(
            Any::class::class.java.getResource("/net/postchain/d1/anchoring/blockchain_config_2_system_anchoring.xml")!!.readText(),
            mapOf(
                    "anchoring_chain_common" to GtvFactory.gtv(anchoringRellCode),
                    "anchoring_chain_system" to GtvFactory.gtv(systemAnchoringRellCode)
            )
    )
}

fun getClusterAnchoringChainConfig(): Gtv {
    val anchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_common/module.rell").readText()
    val clusterAnchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_cluster/module.rell").readText()
    val icmfRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_cluster/icmf.rell").readText()
    return GtvMLParser.parseGtvML(
            Any::class::class.java.getResource("/net/postchain/d1/anchoring/blockchain_config_2_cluster_anchoring.xml")!!.readText(),
            mapOf(
                    "anchoring_chain_common" to GtvFactory.gtv(anchoringRellCode),
                    "anchoring_chain_cluster" to GtvFactory.gtv(clusterAnchoringRellCode + icmfRellCode)
            )
    )
}
