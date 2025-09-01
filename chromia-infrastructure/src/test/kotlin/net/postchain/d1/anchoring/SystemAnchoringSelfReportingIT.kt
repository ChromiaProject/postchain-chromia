package net.postchain.d1.anchoring

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import net.postchain.base.configuration.KEY_SYNC
import net.postchain.base.withReadConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.d1.icmf.DEFAULT_MESSAGE_QUERY_LIMIT
import net.postchain.d1.icmf.IcmfSenderDatabaseOperationsImpl
import net.postchain.devtools.ManagedModeTest
import net.postchain.devtools.utils.ChainUtil
import net.postchain.devtools.utils.configuration.NodeSetup
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class SystemAnchoringSelfReportingIT : ManagedModeTest() {

    override fun addNodeConfigurationOverrides(nodeSetup: NodeSetup) {
        super.addNodeConfigurationOverrides(nodeSetup)
        nodeSetup.nodeSpecificConfigs.setProperty("infrastructure", D1PTestPcuInfrastructureFactory::class.qualifiedName)
        nodeSetup.nodeSpecificConfigs.setProperty("clusterManagementMock", AnchoringTestClusterManagement::class.qualifiedName)
    }

    @Test
    @Timeout(60, unit = TimeUnit.SECONDS)
    fun systemAnchoringSelfReportsFaultyConfig() {
        startManagedSystem(3, 0)
        val systemAnchoringChain = startSystemAnchoringChain()
        val signers = setOf(0, 1, 2)

        buildBlock(systemAnchoringChain, 0L)

        val initialConfigHash = nodes.first().getBlockchainInstance(systemAnchoringChain)
                .blockchainEngine.getConfiguration().configHash

        // Add invalid config
        val reconfigHeight = 3L
        addGtxBlockchainConfiguration(
                systemAnchoringChain,
                signers.associateWith { nodes[it].pubKey.hexStringToByteArray() },
                null,
                reconfigHeight,
                mapOf(KEY_SYNC to GtvFactory.gtv("invalid")),
                true
        )

        buildBlockNoWait(nodes, systemAnchoringChain, 2)

        // Wait until we reverted faulty config
        awaitChainRestarted(systemAnchoringChain, 2, initialConfigHash)

        // Build two blocks to trigger self reporting
        buildBlock(systemAnchoringChain, 4)

        // Assert configuration failed ICMF message was sent
        for (node in getChainNodes(systemAnchoringChain)) {
            withReadConnection(node.postchainContext.blockBuilderStorage, systemAnchoringChain) {
                val dbOps = IcmfSenderDatabaseOperationsImpl()

                val configFailedMessages = dbOps.getSentMessagesAfterHeight(it, "G_configuration_failed", -1, DEFAULT_MESSAGE_QUERY_LIMIT)
                assertThat(configFailedMessages).hasSize(1)

                val failedConfigMessageBody = configFailedMessages.first().body.asArray()
                assertThat(BlockchainRid(failedConfigMessageBody[0].asByteArray())).isEqualTo(ChainUtil.ridOf(systemAnchoringChain))
                assertThat(failedConfigMessageBody[1].asInteger()).isEqualTo(reconfigHeight)
            }
        }
    }

    private fun startSystemAnchoringChain(): Long {
        val anchorGtvConfig = GtvMLParser.parseGtvML(javaClass.getResource("/anchoring/system_anchoring.xml")!!.readText())
        return startNewBlockchain(setOf(0, 1, 2), setOf(), rawBlockchainConfiguration = GtvEncoder.encodeGtv(anchorGtvConfig))
    }
}
