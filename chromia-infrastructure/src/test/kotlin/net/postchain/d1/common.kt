package net.postchain.d1

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import java.io.File


val RELL_SOURCE_PATH = File(System.getenv("D1_SOURCE"))

fun getSystemAnchoringChainConfig(): Gtv {
    val chainVersionCode = File(RELL_SOURCE_PATH, "chain_version.rell").readText()
    val anchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_common/module.rell").readText()
    val systemAnchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_system/module.rell").readText()
    val systemAnchoringSelfReportRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_system/self_report_configuration_updated.rell").readText()
    val selfReportConfigurationFailed = File(RELL_SOURCE_PATH, "anchoring_chain_system/self_report_configuration_failed.rell").readText()
    val messagingIcmfRellCode = File(RELL_SOURCE_PATH, "lib/icmf/module.rell").readText()
    val messagingIcmfConstantsRellCode = File(RELL_SOURCE_PATH, "lib/icmf/constants.rell").readText()
    val confUpdateMessage = File(RELL_SOURCE_PATH, "messaging/configuration_update_message.rell").readText()
    return GtvMLParser.parseGtvML(
            Any::class::class.java.getResource("/net/postchain/d1/anchoring/blockchain_config_2_system_anchoring.xml")!!.readText(),
            mapOf(
                    "chain_version" to GtvFactory.gtv(chainVersionCode),
                    "anchoring_chain_common" to GtvFactory.gtv(anchoringRellCode),
                    "anchoring_chain_system" to GtvFactory.gtv(systemAnchoringRellCode + systemAnchoringSelfReportRellCode),
                    "self_report_configuration_failed" to GtvFactory.gtv(selfReportConfigurationFailed),
                    "lib.icmf" to GtvFactory.gtv(messagingIcmfRellCode),
                    "lib.icmf.constants" to GtvFactory.gtv(messagingIcmfConstantsRellCode),
                    "messaging.configuration_update_message" to GtvFactory.gtv(confUpdateMessage)
            )
    )
}

fun getClusterAnchoringChainConfig(blockchainConfigFile: String): Gtv {
    val chainVersionCode = File(RELL_SOURCE_PATH, "chain_version.rell").readText()
    val anchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_common/module.rell").readText()
    val clusterAnchoringRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_cluster/module.rell").readText()
    val icmfRellCode = File(RELL_SOURCE_PATH, "anchoring_chain_cluster/icmf.rell").readText()
    val nodeAvailabilityReporting = File(RELL_SOURCE_PATH, "anchoring_chain_cluster/node_availability_reporting.rell").readText()
    val resourceUsageStatistics = File(RELL_SOURCE_PATH, "anchoring_chain_cluster/resource_usage_statistics.rell").readText()
    val messagingIcmfRellCode = File(RELL_SOURCE_PATH, "lib/icmf/module.rell").readText()
    val icmfReceiverRellCode = File(RELL_SOURCE_PATH, "lib/icmf/receiver.rell").readText()
    val messagingIcmfConstantsRellCode = File(RELL_SOURCE_PATH, "lib/icmf/constants.rell").readText()
    val messagingNodeAvailabilityReporting = File(RELL_SOURCE_PATH, "messaging/node_availability_reporting.rell").readText()
    val confUpdateMessage = File(RELL_SOURCE_PATH, "messaging/configuration_update_message.rell").readText()
    val containerBlockchainMessage = File(RELL_SOURCE_PATH, "messaging/container_blockchain.rell").readText()
    return GtvMLParser.parseGtvML(
            Any::class::class.java.getResource(blockchainConfigFile)!!.readText(),
            mapOf(
                    "chain_version" to GtvFactory.gtv(chainVersionCode),
                    "anchoring_chain_common" to GtvFactory.gtv(anchoringRellCode),
                    "anchoring_chain_cluster" to GtvFactory.gtv(clusterAnchoringRellCode + icmfRellCode + nodeAvailabilityReporting + resourceUsageStatistics),
                    "lib.icmf" to GtvFactory.gtv(messagingIcmfRellCode),
                    "lib.icmf.constants" to GtvFactory.gtv(messagingIcmfConstantsRellCode),
                    "lib.icmf.receiver" to GtvFactory.gtv(icmfReceiverRellCode),
                    "messaging.configuration_update_message" to GtvFactory.gtv(confUpdateMessage),
                    "messaging.node_availability_reporting" to GtvFactory.gtv(messagingNodeAvailabilityReporting),
                    "messaging.container_blockchain" to GtvFactory.gtv(containerBlockchainMessage)
            )
    )
}
