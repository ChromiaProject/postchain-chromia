package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import net.postchain.chain0.cm_api.cmGetClusterInfo
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.addNodeToClusterOperation
import net.postchain.chain0.common.operations.disableNodeOperation
import net.postchain.chain0.common.operations.enableNodeOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.operations.updateNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getClusterBlockchains
import net.postchain.chain0.common.queries.getClusters
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_cluster.createClusterOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.proposal.voting.createVoterSetOperation
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.chain0.proposal_cluster.proposeClusterProviderOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.chromia.nm_api.nmGetBlockchainConfigurationInfo
import net.postchain.common.BlockchainRid
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1ReconfigurationMixSlowIntegrationTest : ManagedModeBase("reconfig") {

    init {
        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-mix")
        node2 = postchainServer("node2",
                provider2KeyPair,
                "config-mix")
                .withGenesisNode(node1)
        node3 = postchainServerWithSubnodes("node3",
                provider3KeyPair,
                "config-mix",
                true)
                .withGenesisNode(node1)

        removeSubnodeContainers()
        startNodesAndChain0()
    }

    @AfterAll
    fun tearDown() {
        super.breakdown()
    }

    @Test
    @Order(1)
    fun `Initialize network with provider1`() {
        getDb(node1).awaitBlockHeight(0)
        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .updateNodeWithUnitsOperation(node1.providerPubkey, node1.pubkey.data, null, null, null, 2)
                    .postTransactionUntilConfirmed("init")

            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
        }
        assertAnchoringChainProperties()
    }

    @Test
    @Order(2)
    fun `Reconfigure chain0 by faulty-config - compile error`() {
        testLogger.info("Reconfigure Chain0 by faulty compile config")

        testLogger.info("Proposing faulty chain0 config")
        node1.c0.transactionBuilder(listOf(node1.provider))
                .proposeConfigurationOperation(node1.providerPubkey, chain0Brid, chain0Config(true), "", null)
                .postTransactionUntilConfirmed("Propose a faulty chain0 config")

        // Asserting that chain0 is building blocks
        val height = node1.c0.currentBlockHeight()
        awaitQueryResult {
            assertThat(node1.c0.currentBlockHeight()).isGreaterThan(height)
        }

        // Asserting that current chain0 config is the latest valid one
        val config0 = node1.c0.nmGetBlockchainConfigurationInfo(chain0Brid, 0)!!
        awaitQueryResult {
            val currentHeight = node1.c0.currentBlockHeight()
            val currentConfig = node1.c0.nmGetBlockchainConfigurationInfo(chain0Brid, currentHeight)
            assertThat(currentConfig?.configHash).isEqualTo(config0.configHash)
        }
    }

    @Test
    @Order(3)
    fun `Reconfigure chain0 by faulty-config - runtime error`() {

        testLogger.info("Reconfigure Chain0 by faulty runtime config")

        val compileDapp = compileDapp("manager", faulty = true, bugSupplier = ::addEntityWithoutDefault)

        testLogger.info("Proposing chain0 config")
        node1.c0.transactionBuilder(listOf(node1.provider))
                .proposeConfigurationOperation(node1.providerPubkey, chain0Brid, GtvEncoder.encodeGtv(compileDapp), "", null)
                .postTransactionUntilConfirmed("Propose a chain0 config")

        val testConfig = node1.c0.nmGetBlockchainConfigurationInfo(chain0Brid, node1.c0.currentBlockHeight())!!

        val config0 = node1.c0.nmGetBlockchainConfigurationInfo(chain0Brid, 0)!!
        assertThat(testConfig.configHash).isNotEqualTo(config0.configHash)

        // Add row to entity (to make the default attribute fail on next config update)
        node1.c0.transactionBuilder(listOf(node1.provider))
                .addOperation("add_postchain_chromia_test", gtv(node1.providerPubkey), gtv("t1"))
                .postTransactionUntilConfirmed("add_postchain_chromia_test")

        val compileDappUpdate = compileDapp("manager", faulty = true, bugSupplier = ::addEntityWithoutDefaultNewAttribute)

        testLogger.info("Proposing faulty chain0 config")
        node1.c0.transactionBuilder(listOf(node1.provider))
                .proposeConfigurationOperation(node1.providerPubkey, chain0Brid, GtvEncoder.encodeGtv(compileDappUpdate), "", null)
                .postTransactionUntilConfirmed("Propose a faulty chain0 config")

        // Asserting that chain0 is building blocks
        val height2 = node1.c0.currentBlockHeight()
        awaitQueryResult {
            assertThat(node1.c0.currentBlockHeight()).isGreaterThan(height2)
        }

        // Asserting that current chain0 config is the latest valid one
        awaitQueryResult {
            val currentHeight = node1.c0.currentBlockHeight()
            val currentConfig = node1.c0.nmGetBlockchainConfigurationInfo(chain0Brid, currentHeight)!!
            assertThat(currentConfig.configHash).isEqualTo(testConfig.configHash)
        }
    }

    @Test
    @Order(4)
    fun `Add node2 and node3 as signers to c0`() {
        testLogger.info("Adding provider2/node2 and provider3/node3 to the cluster")

        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com")
        )

        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 2)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("Register p2 and p3 as system")

        // Asserting that node1, node2, node3 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(5)
    fun `Disable and re-enable node2 and node3`() {
        testLogger.info("Disable and re-enable node2 and node3")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                .disableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .postTransactionUntilConfirmed("Propose disabling node2 and node3")

        assertChainSigners(chain0Brid, node1)
        assertChainSigners(clusterAnchoringBrid, node1)
        assertChainSigners(systemAnchoringBrid, node1)

        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider))
                .enableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, systemCluster)
                .postTransactionUntilConfirmed("Enable and add node2 to the $systemCluster cluster")

        node1.c0.transactionBuilder(listOf(node1.provider, node3.provider))
                .enableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, systemCluster)
                .postTransactionUntilConfirmed("Enable and add node3 to the $systemCluster cluster")

        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(6)
    fun `Reconfigure CAC by various config types`() {
        testLogger.info("Update cluster anchoring chain")

        // 1. Mixed tx: proposing config, duplicated config, removing signer, faulty config, config again
        testLogger.info("Proposing different kinds of configs for CAC: config, duplicated config, removing signer, faulty config, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18200), "", null)
                // propose the same config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18200), "", null)
                // disable node3
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18300, true), "", null)
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18400), "", null)
                .postTransactionUntilConfirmed("Propose different cluster anchoring chain configs")

        voteOnAllProposals(listOf(node2.provider, node3.provider))

        awaitQueryResult {
            assertThat(getLastBlockConfigSigners(node1, clusterAnchoringBrid).toSet()).isEqualTo(setOf(node1.pubkey.wData, node2.pubkey.wData))
            assertThat(getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, clusterAnchoringBrid)).isEqualTo(setOf(500, 18200, 18400))
        }

        // 2. Mixed tx: proposing config, faulty config, adding signer, config again
        testLogger.info("Proposing different kinds of configs for CAC: proposing config, faulty config, adding signer, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose already applied config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18400), "", null)
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18500, true), "", null)
                // re-enable node3
                .enableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, systemCluster)
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, clusterAnchoringBrid, cacConfig(18600), "", null)
                .postTransactionUntilConfirmed("Propose different cluster anchoring chain configs #2")

        voteOnAllProposals(listOf(node2.provider, node3.provider))

        awaitQueryResult {
            assertThat(getLastBlockConfigSigners(node1, clusterAnchoringBrid).toSet()).isEqualTo(
                    setOf(node1.pubkey.wData, node2.pubkey.wData, node3.pubkey.wData))

            assertThat(getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, clusterAnchoringBrid)).isEqualTo(
                    setOf(500, 18200, 18400, 18600))
        }
    }

    @Test
    @Order(7)
    fun `Reconfigure SAC by various config types`() {
        testLogger.info("Update system anchoring chain")

        // 1. Mixed tx: proposing config, duplicated config, removing signer, faulty config, config again
        testLogger.info("Proposing different kinds of configs for SAC: config, duplicated config, removing signer, faulty config, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19200), "", null)
                // propose the same config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19200), "", null)
                // disable node3
                .disableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19300, true), "", null)
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19400), "", null)
                .postTransactionUntilConfirmed("Propose different system anchoring chain configs")

        voteOnAllProposals(listOf(node2.provider, node3.provider))

        awaitQueryResult {
            assertThat(getLastBlockConfigSigners(node1, systemAnchoringBrid).toSet()).isEqualTo(
                    setOf(node1.pubkey.wData, node2.pubkey.wData))

            assertThat(getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, systemAnchoringBrid)).isEqualTo(
                    setOf(500, 19200, 19400))
        }

        // 2. Mixed tx: proposing config, faulty config, adding signer, config again
        testLogger.info("Proposing different kinds of configs for SAC: proposing config, faulty config, adding signer, config again")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                // propose already applied config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19400), "", null)
                // propose faulty config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19500, true), "", null)
                // re-enable node3
                .enableNodeOperation(node3.providerPubkey, node3.pubkey.data)
                .addNodeToClusterOperation(node3.providerPubkey, node3.pubkey.data, systemCluster)
                // propose config
                .proposeConfigurationOperation(node1.providerPubkey, systemAnchoringBrid, sacConfig(19600), "", null)
                .postTransactionUntilConfirmed("Propose different system anchoring chain configs #2")

        voteOnAllProposals(listOf(node2.provider, node3.provider))

        awaitQueryResult {
            assertThat(getLastBlockConfigSigners(node1, systemAnchoringBrid).toSet()).isEqualTo(
                    setOf(node1.pubkey.wData, node2.pubkey.wData, node3.pubkey.wData))
        }
        awaitQueryResult {
            assertThat(getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, systemAnchoringBrid)).isEqualTo(
                    setOf(500, 19200, 19400, 19600))
        }
    }

    @Test
    @Order(8)
    fun `Reconfigure anchoring chain by faulty-remove-signer-config`() {
        testLogger.info("Reconfigure CAC by faulty-remove-signer-config")

        val pcuVs = "pcu_vs"
        val pcuCluster = "pcu_cluster"

        // 1. Create a new cluster `pcu_cluster`
        node1.c0.transactionBuilder()
                .createVoterSetOperation(node1.providerPubkey, pcuVs, 1, listOf(node1.providerPubkey), null)
                .createClusterOperation(node1.providerPubkey, pcuCluster, pcuVs, listOf(node1.providerPubkey))
                .addNodeToClusterOperation(node1.providerPubkey, node1.pubkey.data, pcuCluster)
                .postTransactionUntilConfirmed("Create voterset $pcuVs, cluster $pcuCluster with provider1/node1")

        awaitQueryResult {
            assertThat(node1.c0.getClusters().find { it.name == pcuCluster }).isNotNull()
            val chains = node1.c0.getClusterBlockchains(pcuCluster)
            assertThat(chains.size).isEqualTo(1)
            val brid = BlockchainRid(chains.first())

            // signers from cluster anchoring chain (CAC) config
            val actual = getLastBlockConfigSigners(node1, brid)
            assertThat(actual.toSet()).isEqualTo(setOf(node1.pubkey.wData))
        }

        // 2. Add provider2/node2 to the pcu_cluster
        val cac = BlockchainRid(node1.c0.cmGetClusterInfo(pcuCluster).anchoringChain)
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider, node3.provider))
                .proposeClusterProviderOperation(node1.providerPubkey, pcuCluster, node2.providerPubkey, true, "")
                .addNodeToClusterOperation(node2.providerPubkey, node2.pubkey.data, pcuCluster)
                .postTransactionUntilConfirmed("Add provider2/node2 to the $pcuCluster")

        awaitQueryResult {
            assertThat(getLastBlockConfigSigners(node1, cac).toSet()).isEqualTo(
                    setOf(node1.pubkey.wData, node2.pubkey.wData))
        }

        // 3. Proposing a faulty remove signer config
        testLogger.info("Proposing faulty remove signer pending config")
        node1.c0.transactionBuilder(listOf(node1.provider, node2.provider))
                // proposing faulty pending_config1 and pending_removed_signers_config2,
                // so that config2 will contain base_config1 and will fail
                // signer update should be retried and eventually succeed
                .proposeConfigurationOperation(node1.providerPubkey, cac, cacConfig(20100, true), "", null)
                .disableNodeOperation(node2.providerPubkey, node2.pubkey.data)
                .proposeConfigurationOperation(node1.providerPubkey, cac, cacConfig(20200), "", null)
                .postTransactionUntilConfirmed("Propose different cluster anchoring chain configs #3")

        awaitQueryResult {
            assertThat(getLastBlockConfigSigners(node1, cac).toSet()).isEqualTo(
                    setOf(node1.pubkey.wData))
        }
        awaitQueryResult {
            assertThat(getMaxBlockTransactionsOfAllCommittedBlockchainConfigs(node1, cac)).isEqualTo(
                    setOf(500, 20200))
        }
    }

    private fun chain0Config(faulty: Boolean = false) = GtvEncoder.encodeGtv(
            compileDapp("manager", faulty = faulty, bugSupplier = ::addBrokenBlockStrategyClass)
    ).also {
        testLogger.error { it }
    }

    private fun addBrokenBlockStrategyClass(config: String) = findAndReplaceBugSupplier(
            config, "net.postchain.base.BaseBlockBuildingStrategy", "non-existing-class"
    )

    private fun sacConfig(param: Int, faulty: Boolean = false) = GtvEncoder.encodeGtv(compileDapp("system_anchoring", param, faulty = faulty))

    private fun cacConfig(param: Int, faulty: Boolean = false) = GtvEncoder.encodeGtv(compileDapp("cluster_anchoring", param, faulty = faulty))

    private fun addEntityWithoutDefault(config: String): String {
        val patch = addTestCode(config,
                "entity postchain_chromia_test {\n" +
                        "    value1: text;\n" +
                        "}\n" +
                        "operation add_postchain_chromia_test(provider_pubkey: pubkey, value: text) {\n" +
                        "    create postchain_chromia_test(value);\n" +
                        "}"
        )
        return patch
    }

    private fun addEntityWithoutDefaultNewAttribute(config: String): String {
        return addTestCode(config,
                "entity postchain_chromia_test {\n" +
                        "    value1: text;\n" +
                        "    value2: text;" +
                        "}")
    }

    // Inject rell code as isolated fle in the management_chain_common module
    private fun addTestCode(config: String, rellModule: String) =
            config.replace("<entry key=\"sources\">[\\v\\s]*<dict>".toRegex(RegexOption.MULTILINE),
                    "<entry key=\"sources\"><dict><entry key=\"management_chain_common/postchain_chromia_test.rell\"><string>" +
                            rellModule +
                            "</string></entry>"
            )
}
