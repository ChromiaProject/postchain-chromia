package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.operations.registerNodeWithUnitsOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.evm_event_receiver.initEvmEventReceiverChainOperation
import net.postchain.chain0.evm_transaction_submitter.getEvmTransactionSubmitterChainRid
import net.postchain.chain0.evm_transaction_submitter.initEvmTransactionSubmitterChainOperation
import net.postchain.chain0.model.ProviderInfo
import net.postchain.chain0.model.ProviderTier
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.chain0.proposal_provider.proposeProvidersOperation
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.d1.ExclusiveTableLockTestGTXModule
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.getBlockchainHeight
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import org.awaitility.Duration
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.BindMode
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1DeadlockIT : EvmTestBase("deadlock") {

    private val systemAnchoringChainConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())
    private val clusterAnchoringChainConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
    private lateinit var evmChainConfig: Gtv
    private lateinit var txsChainConfig: Gtv
    private lateinit var systemAnchoringChainBrid: BlockchainRid
    private lateinit var clusterAnchoringChainBrid: BlockchainRid
    private lateinit var eventReceiverBrid: BlockchainRid
    private lateinit var txSubmitterBrid: BlockchainRid

    // Clients
    private val PostchainContainer.sac get() = client(systemAnchoringChainBrid)
    private val PostchainContainer.cac get() = client(clusterAnchoringChainBrid)
    private val PostchainContainer.evm get() = client(eventReceiverBrid)
    private val PostchainContainer.txs get() = client(txSubmitterBrid)

    private val lockGtxModule = ExclusiveTableLockTestGTXModule::class.java.canonicalName
    private val keepAliveAfterFailure = false // For manual debugging

    init {

        // Pipeline test? Then copy and mount the test jar from a host directory
        var testJarFile = Path("../chromia-devtools/target/")
                .listDirectoryEntries()
                .find { it.name.matches("chromia-devtools-.*.jar".toRegex()) && !it.name.endsWith("-sources.jar") }!!.pathString

        System.getenv("TEST_MOUNT_DIRECTORY")?.let {
            val testJarFileOnHost = File("$it/chromia-devtools.jar")
            testLogger.info { "Copying test jar $testJarFile to host mount: ${testJarFileOnHost.absolutePath}" }
            File(testJarFile).copyTo(testJarFileOnHost, true)
            testJarFile = testJarFileOnHost.absolutePath
        }

        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-mix")
                .withFileSystemBind(testJarFile, "/opt/chromaway/postchain/classpath/chromia-devtools.jar", BindMode.READ_ONLY)
                .withCreateContainerCmdModifier { it.withEntrypoint("java") }
                .withCommand("-XX:+UnlockDiagnosticVMOptions",
                        "-XX:AbortVMOnException=java.lang.OutOfMemoryError",
                        "-cp",
                        "/opt/chromaway/postchain/libs/*:/opt/chromaway/postchain/classpath/*",
                        "net.postchain.server.AppKt",
                        "run-server")
                .withEifEnv()
        node2 = postchainServer("node2",
                provider2KeyPair,
                "config-mix")
                .withFileSystemBind(testJarFile, "/opt/chromaway/postchain/classpath/chromia-devtools.jar", BindMode.READ_ONLY)
                .withCreateContainerCmdModifier { it.withEntrypoint("java") }
                .withCommand("-XX:+UnlockDiagnosticVMOptions",
                        "-XX:AbortVMOnException=java.lang.OutOfMemoryError",
                        "-cp",
                        "/opt/chromaway/postchain/libs/*:/opt/chromaway/postchain/classpath/*",
                        "net.postchain.server.AppKt",
                        "run-server")
                .withGenesisNode(node1)
                .withEifEnv()
        node3 = postchainServerWithSubnodes("node3",
                provider3KeyPair,
                "config-mix")
                .withFileSystemBind(testJarFile, "/opt/chromaway/postchain/classpath/chromia-devtools.jar", BindMode.READ_ONLY)
                .withCreateContainerCmdModifier { it.withEntrypoint("java") }
                .withCommand("-XX:+UnlockDiagnosticVMOptions",
                        "-XX:AbortVMOnException=java.lang.OutOfMemoryError",
                        "-cp",
                        "/opt/chromaway/postchain/libs/*:/opt/chromaway/postchain/classpath/*",
                        "net.postchain.server.AppKt",
                        "run-server")
                .withGenesisNode(node1)
                .withEifEnv()

        removeSubnodeContainers()
        startNodesAndChain0()

        testLogger.info { "Classpath files: ${node1.execInContainer("ls", "/opt/chromaway/postchain/classpath/").stdout}" }
    }

    @Test
    @Order(10)
    fun `Init - Network`() {

        testLogger.info("Setup the network")

        with(node1.c0) {
            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringChainConfig), GtvEncoder.encodeGtv(clusterAnchoringChainConfig))
                    .postTransactionUntilConfirmed("init")
            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
            assertAnchoringChainProperties()

            assertThat(nmFindNextConfigurationHeight(chain0Brid, 0)).isNull()

            val blockchains = node1.c0.getBlockchains(true)
            systemAnchoringChainBrid = BlockchainRid(blockchains.find { it.name == "system_anchoring" }?.rid!!)
            clusterAnchoringChainBrid = BlockchainRid(blockchains.find { it.name == "cluster_anchoring_system" }?.rid!!)
        }

        testLogger.info("Adding system providers provider2 and provider3 and their nodes")
        val newProviders = listOf(
                ProviderInfo(node2.provider.pubKey.wData, "provider2", "http://provider2.com"),
                ProviderInfo(node3.provider.pubKey.wData, "provider3", "http://provider3.com"),
        )

        node1.client(chain0Brid, listOf(node1.provider, node2.provider, node3.provider)).transactionBuilder().addNop()
                .proposeProvidersOperation(node1.providerPubkey, newProviders, ProviderTier.NODE_PROVIDER, system = true, active = true, description = "")
                .registerNodeWithUnitsOperation(node2.providerPubkey, node2.pubkey.data, node2.nodeHost, node2.nodePort.toLong(), node2.nodeApiPath(), listOf(systemCluster), 2)
                .registerNodeWithUnitsOperation(node3.providerPubkey, node3.pubkey.data, node3.nodeHost, node3.nodePort.toLong(), node3.nodeApiPath(), listOf(systemCluster), 2)
                .postTransactionUntilConfirmed("System provider2, provider3 and node2, node3")

        // Asserting that node1, node2 and node3 are signers of chain0 / cluster anchoring chain / system anchoring chain
        assertChainSigners(chain0Brid, *nodes())
        assertChainSigners(clusterAnchoringBrid, *nodes())
        assertChainSigners(systemAnchoringBrid, *nodes())
    }

    @Test
    @Order(11)
    fun `Init - EIF Event Receiver Chain`() {
        testLogger.info("Deploying EIF Event Receiver Chain")

        evmChainConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/eif_event_receiver.xml")!!.readText()
                .replace("<entry key=\"mininterblockinterval\">", "<entry key=\"maxblocktime\"><int>1000</int></entry><entry key=\"mininterblockinterval\">")
                .replace(EIF_EVENT_RECEIVER_CONTRACT_PLACEHOLDER, "0xC7b0F970c1EFBB181194Fc15ccD5C4a2c2Ab863B"))

        node1.c0.transactionBuilder()
                .initEvmEventReceiverChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(evmChainConfig))
                .postTransactionUntilConfirmed("Add $EVM_EVENT_RECEIVER_CHAIN_NAME")

        val erRid = node1.c0.getBlockchains(true).firstOrNull { it.name == EVM_EVENT_RECEIVER_CHAIN_NAME }?.rid
        assertThat(erRid).isNotNull()
        eventReceiverBrid = BlockchainRid(erRid!!)

        testLogger.info { "$EVM_EVENT_RECEIVER_CHAIN_NAME deployed: $eventReceiverBrid" }
    }

    @Test
    @Order(12)
    fun `Init - TXS`() {
        testLogger.info("Deploying Transaction submitter chain")

        txsChainConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/transaction_submitter.xml")!!.readText()
                .replace("DIRECTORY_CHAIN_VALIDATOR_VALUE", "6936b1761eafc2116650b6593bbc86bd79a339a5")
                .replace("x\"DIRECTORY_CHAIN_BRID_VALUE\"", chain0Brid.toHex())
                .replace("x\"SYSTEM_ANCHORING_CHAIN_BRID_VALUE\"", systemAnchoringBrid.toHex())
                .replace("ANCHORING_CONTRACT_VALUE", "6936b1761eafc2116650b6593bbc86bd79a339a5")
                .replace("VALIDATOR_CONTRACT_VALUE", "39615b16b74589919c9ce1ea73f1fc5d53141a78"))

        node1.c0.transactionBuilder()
                .initEvmTransactionSubmitterChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(txsChainConfig))
                .postTransactionUntilConfirmed("Add transaction submitter chain")

        txSubmitterBrid = BlockchainRid(node1.c0.getEvmTransactionSubmitterChainRid()!!)
        ensureBuildingBlocks(node1.txs)
    }

    @Test
    @Order(20)
    fun `Lock test - SAC`() {

        testLogger.info("System anchoring chain config with lock module")

        val chainLockConfig = addLockModule(systemAnchoringChainConfig)
        testUpdateWithLock(systemAnchoringChainBrid, chainLockConfig, node1.sac)
    }

    /**
     * The regression target. The CAC's `cluster_anchoring` config has an `icmf.receiver.global`
     * receiver (see directory1.yml), so a [net.postchain.d1.icmf.IntraClusterAnchoredTopicPipe] runs
     * on every CAC block build and issues `icmf_get_headers_with_messages_after_height` — a query that
     * JOINs the CAC's own `anchor_block` — against the CAC's own [net.postchain.base.BaseBlockQueries].
     * That SELECT takes an `AccessShareLock` on `anchor_block` at executor start, regardless of how many
     * rows exist, so no ICMF backlog is needed to trigger it. When the migrating lock config
     * ([ExclusiveTableLockTestGTXModule], which DROP-COLUMNs `anchor_block`) holds the write connection's
     * `AccessExclusiveLock` during the first block build, that second connection self-deadlocks — unless
     * the fix makes it reuse the held write context.
     */
    @Test
    @Order(21)
    fun `Lock test - CAC`() {

        testLogger.info("Cluster anchoring chain config with lock module")

        val chainLockConfig = addLockModule(clusterAnchoringChainConfig)
        testUpdateWithLock(clusterAnchoringChainBrid, chainLockConfig, node1.cac)
    }

    @Test
    @Order(30)
    fun `Lock test - DC`() {

        testLogger.info("Updating directory chain config with lock module")

//        val chainLockConfig = GtvMLParser.parseGtvML(addRellLock(chain0Config, "cluster"))
        val chainLockConfig = addLockModule(GtvMLParser.parseGtvML(chain0Config))
        testUpdateWithLock(chain0Brid, chainLockConfig, node1.c0)
    }

    @Test
    @Order(40)
    fun `Lock test - EVM receiver`() {

        testLogger.info("Updating EVM receiver chain config with lock module")

        val chainLockConfig = addLockModule(evmChainConfig)
        testUpdateWithLock(eventReceiverBrid, chainLockConfig, node1.evm)
    }

    @Test
    @Order(50)
    fun `Lock test - TXS`() {
        testLogger.info("Updating transaction submitter chain config with lock module")

        val chainLockConfig = addLockModule(txsChainConfig)
        testUpdateWithLock(txSubmitterBrid, chainLockConfig, node1.txs)
    }

    private fun testUpdateWithLock(bcRid: BlockchainRid, config: Gtv, chainClient: PostchainClient) {

        with(node1.c0) {

            try {
                transactionBuilder()
                        .proposeConfigurationOperation(provider1KeyPair.pubKey.data, bcRid, GtvEncoder.encodeGtv(config), "", null)
                        .postTransactionUntilConfirmed("Updated chain config", retries = 10)

                voteOnAllProposals(listOf(provider2KeyPair, provider3KeyPair))

                awaitUntilAsserted(Duration(2, TimeUnit.MINUTES)) {
                    testLogger.info("Checking if config is applied...")
                    assertThat(nmFindNextConfigurationHeight(bcRid, 0)).isNotNull()
                    testLogger.info(" - attempt is made to apply config")
                    assertThat(GtvFactory.decodeGtv(nmGetBlockchainConfiguration(bcRid,
                            Long.MAX_VALUE)!!)["gtx"]!!["modules"]!!.asArray()
                            .map { it.asString() }).contains(lockGtxModule)
                    testLogger.info(" - config is updated")
                }
            } catch (e: Exception) {

                testLogger.error("TEST FAILED!!! Is this due to a deadlock?")
                testLogger.error("TEST FAILED!!! Is this due to a deadlock?")
                testLogger.error("TEST FAILED!!! Is this due to a deadlock?")

                if (!keepAliveAfterFailure) {
                    throw e
                }

                testLogger.info("Keep test node alive for debugging...")

                while (true) {
                    testLogger.info("Height: ${chainClient.getBlockchainHeight()}")
                    Thread.sleep(10000)
                }
            }
        }
    }

    private fun addLockModule(config: Gtv) =
            gtv(config.asDict().mapValues { configRootEntry ->
                if (configRootEntry.key == "gtx") {
                    gtv(configRootEntry.value.asDict().mapValues { configGtxEntry ->
                        if (configGtxEntry.key == "modules") {
                            val modules = configGtxEntry.value.asArray()
                                    .filter { it.asString() != "net.postchain.rell.module.RellPostchainModuleFactory" }
                                    .toTypedArray()
                            gtv(listOf(
                                    gtv("net.postchain.rell.module.RellPostchainModuleFactory"),
                                    gtv(lockGtxModule),
                                    *modules,
                            ))
                        } else {
                            configGtxEntry.value
                        }
                    })
                } else {
                    configRootEntry.value
                }
            })

    // Add an attribute to an entity to make the rell module lock it
    fun addRellLock(config: String, entity: String): String {
        val sp = config.indexOf("entity $entity {")
        val ep = config.indexOf("}", sp)
        return config.substring(0, ep) +
                "mutable dummy: integer = 0;" +
                config.substring(ep, config.length)
    }

    private fun ensureBuildingBlocks(client: PostchainClient) {
        val currentHeight = client.getBlockchainHeight()
        awaitUntilAsserted(Duration(30, TimeUnit.SECONDS)) {
            assertThat(client.getBlockchainHeight()).isGreaterThan(currentHeight)
        }
    }
}