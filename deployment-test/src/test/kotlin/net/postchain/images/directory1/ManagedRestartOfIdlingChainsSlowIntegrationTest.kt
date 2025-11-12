package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.proposal_blockchain.BlockchainAction
import net.postchain.chain0.proposal_blockchain.proposeBlockchainActionOperation
import net.postchain.chain0.proposal_blockchain.proposeBlockchainOperation
import net.postchain.chain0.proposal_container.proposeContainerOperation
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.dapp.getBlockchainHeight
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.images.common.ManagedModeBase
import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import org.awaitility.Duration
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers
import java.lang.Thread.sleep

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ManagedRestartOfIdlingChainsSlowIntegrationTest : ManagedModeBase("restart_idling_chain") {

    private val restartPattern = "Chain has been idling for .* and will be restarted".toRegex()
    private lateinit var dappClient: PostchainClient
    private lateinit var dappBrid: BlockchainRid

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        node1 = postchainServerWithSubnodes("node1",
                provider1KeyPair,
                "config-no-subnodes")
                .withEnv("POSTCHAIN_HOUSEKEEPING_INTERVAL_MS", "5000")
                .withEnv("POSTCHAIN_HOUSEKEEPING_RESTART_INACTIVE_CHAIN_MS", "10000")

        startNodesAndChain0()
        getDb(node1).awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider1`() {
        with(node1.c0) {
            transactionBuilder()
                    .initOperation(null, null)
                    .postTransactionUntilConfirmed("init")

            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
        }
    }

    @Test
    @Order(3)
    fun `Setup dapp`() {
        with(node1.c0.transactionBuilder()) {
            proposeContainerOperation(provider1KeyPair.pubKey.data, "system", "dapp", "SYSTEM_P", "")
            proposeBlockchainOperation(provider1KeyPair.pubKey.data, GtvEncoder.encodeGtv(simpleChainConfig), "simple", "dapp", "")
                    .postTransactionUntilConfirmed("Propose simple chain")
        }
        dappBrid = BlockchainRid(node1.c0.getBlockchains(false)
                .find { it.name == "simple" }!!
                .rid)
        dappClient = node1.client(dappBrid)

        awaitUntilAsserted {
            assertThat(dappClient.getBlockchainHeight()).isEqualTo(2)
        }
    }

    @Test
    @Order(4)
    fun `Chain should not be restarted`() {
        sleep(45_000)
        assertThat(restartPattern.findAll(node1.logs).count()).isEqualTo(0)
    }

    @Test
    @Order(5)
    fun `Pause chain`() {
        with(node1.c0.transactionBuilder()) {
                proposeBlockchainActionOperation(node1.providerPubkey, dappBrid, BlockchainAction.pause, "")
                .postTransactionUntilConfirmed("Propose pause simple chain")
        }

        awaitUntilAsserted {
            assertThat(node1.logs).contains("Blockchain has been started: ReadOnlyBlockchainProcess:PAUSED, blockchain RID: ${dappBrid.toHex()}")
        }
    }

    @Test
    @Order(6)
    fun `Chain should be restarted`() {
        val startedPattern = "Blockchain has been started: [^,]+, blockchain RID: ${dappBrid.toHex()}".toRegex()
        awaitUntilAsserted(Duration.ONE_MINUTE) {
            assertThat(restartPattern.findAll(node1.logs).count())
                    .isGreaterThan(1)
            assertThat(startedPattern.findAll(node1.logs).count())
                    .isGreaterThan(1)
        }
    }

    @Test
    @Order(6)
    fun `Chain0 was never restarted`() {
        val pattern = "Blockchain has been started: [^,]+, blockchain RID: ${chain0Brid.toHex()}".toRegex()
        assertThat(pattern.findAll(node1.logs).count()).isEqualTo(1)
    }

    private val simpleChainConfig = gtv(
            "blockstrategy" to gtv(
                    "name" to gtv("net.postchain.base.BaseBlockBuildingStrategy"),
                    "mininterblockinterval" to gtv(1000),
                    "maxblocktime" to gtv(2000)
            ),
            "revolt" to gtv(
                    "fast_revolt_status_timeout" to gtv(2000),
                    "revolt_when_should_build_block" to gtv(1)
            ),
            "gtx" to gtv(
                    "modules" to gtv(gtv("net.postchain.gtx.StandardOpsGTXModule"))
            ),
            "features" to gtv("merkle_hash_version" to gtv(2)),
            "config_consensus_strategy" to gtv("HEADER_HASH"),
            "configurationfactory" to gtv("net.postchain.gtx.GTXBlockchainConfigurationFactory"),
            "add_primary_key_to_header" to gtv(1),
    )
}
