package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import mu.KotlinLogging
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.evm_event_receiver.initEvmEventReceiverChainOperation
import net.postchain.chain0.evm_transaction_submitter.getEvmTransactionSubmitterChainRid
import net.postchain.chain0.evm_transaction_submitter.initEvmTransactionSubmitterChainOperation
import net.postchain.chain0.nm_api.nmFindNextConfigurationHeight
import net.postchain.chain0.nm_api.nmGetBlockchainConfiguration
import net.postchain.chain0.proposal_blockchain.proposeConfigurationOperation
import net.postchain.client.core.PostchainClient
import net.postchain.common.BlockchainRid
import net.postchain.d1.ExclusiveTableLockTestGTXModule
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.getBlockchainHeight
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory
import net.postchain.gtv.gtvml.GtvMLParser
import org.awaitility.Duration
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class Directory1DeadlockTest : EvmTestBase("EvmDeadlock_EvmContainerLogger") {

    private val node1Logger = KotlinLogging.logger("Deadlock_Node1Logger")

    private lateinit var evmChainConfig: String
    private lateinit var txsChainConfig: String
    private lateinit var eventReceiverBrid: BlockchainRid
    private lateinit var txSubmitterBrid: BlockchainRid

    // Clients
    private val PostchainContainer.evm get() = client(eventReceiverBrid)
    private val PostchainContainer.txs get() = client(txSubmitterBrid)

    private val lockGtxModule = ExclusiveTableLockTestGTXModule::class.java.canonicalName
    private val keepAliveAfterFailure = false // For manual debugging

    init {

        // Pipeline test? Then copy and mount the test jar from a host directory
        var testJarFile = Path("../chromia-devtools/target/")
                .listDirectoryEntries("chromia-devtools-*.jar")
                .first().pathString
        System.getenv("TEST_MOUNT_DIRECTORY")?.let {
            val testJarFileOnHost = File("$it/chromia-devtools.jar")

            testLogger.info { "Copying test jar file to host mount: ${testJarFileOnHost.absolutePath}" }

            File(testJarFile).copyTo(testJarFileOnHost)
            testJarFile = testJarFileOnHost.absolutePath
        }

        node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                provider1KeyPair,
                "config-no-subnodes")
                .withFileSystemBind(testJarFile, "/opt/chromaway/postchain/classpath/deployment-test-dev-tests.jar", BindMode.READ_ONLY)
                .withCreateContainerCmdModifier { it.withEntrypoint("java") }
                .withCommand("-XX:+UnlockDiagnosticVMOptions",
                        "-XX:AbortVMOnException=java.lang.OutOfMemoryError",
                        "-cp",
                        "/opt/chromaway/postchain/libs/*:/opt/chromaway/postchain/classpath/*",
                        "net.postchain.server.AppKt",
                        "run-server")
                .withEifEnv()

        removeSubnodeContainers()
        startNodesAndChain0()
    }

    @Test
    @Order(10)
    fun `Init - Network`() {

        testLogger.info("Setup the network")

        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())
            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .postTransactionUntilConfirmed("init")
            assertThat(getSummary().providers).isEqualTo(1L)
            assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            assertAnchoringChainProperties()

            assertThat(nmFindNextConfigurationHeight(chain0Brid, 0)).isNull()
        }
    }

    @Test
    @Order(11)
    fun `Init - EIF Event Receiver Chain`() {
        testLogger.info("Deploying EIF Event Receiver Chain")

        evmChainConfig = this::class.java.getResource("/directory1deployment/eif_event_receiver.xml")!!.readText()
                .replace("<entry key=\"mininterblockinterval\">", "<entry key=\"maxblocktime\"><int>1000</int></entry><entry key=\"mininterblockinterval\">")
                .replace(EIF_EVENT_RECEIVER_CONTRACT_PLACEHOLDER, "0xC7b0F970c1EFBB181194Fc15ccD5C4a2c2Ab863B")
        val gtvConfig = GtvMLParser.parseGtvML(evmChainConfig)

        node1.c0.transactionBuilder()
                .initEvmEventReceiverChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(gtvConfig))
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

        txsChainConfig = this::class.java.getResource("/directory1deployment/transaction_submitter.xml")!!.readText()
                .replace("DIRECTORY_CHAIN_VALIDATOR_VALUE", "6936b1761eafc2116650b6593bbc86bd79a339a5")
                .replace("x\"DIRECTORY_CHAIN_BRID_VALUE\"", chain0Brid.toHex())
                .replace("x\"SYSTEM_ANCHORING_CHAIN_BRID_VALUE\"", systemAnchoringBrid.toHex())
                .replace("ANCHORING_CONTRACT_VALUE", "6936b1761eafc2116650b6593bbc86bd79a339a5")
                .replace("VALIDATOR_CONTRACT_VALUE", "39615b16b74589919c9ce1ea73f1fc5d53141a78")

        val gtvConfig = GtvMLParser.parseGtvML(txsChainConfig)

        node1.c0.transactionBuilder()
                .initEvmTransactionSubmitterChainOperation(node1.providerPubkey, GtvEncoder.encodeGtv(gtvConfig))
                .postTransactionUntilConfirmed("Add transaction submitter chain")

        txSubmitterBrid = BlockchainRid(node1.c0.getEvmTransactionSubmitterChainRid()!!)
        ensureBuildingBlocks(node1.txs)
    }

    @Test
    @Order(20)
    fun `Lock test - DC`() {

        testLogger.info("Updating directory chain config with lock module")

//        val chainLockConfig = GtvMLParser.parseGtvML(addRellLock(chain0Config, "cluster"))
        val chainLockConfig = addLockModule(chain0Config)

        testUpdateWithLock(chain0Brid, GtvEncoder.encodeGtv(chainLockConfig), node1.c0)
    }

    @Test
    @Order(21)
    fun `Lock test - EVM receiver`() {

//        val chainLockConfig = GtvMLParser.parseGtvML(addRellLock(evmChainConfig, "cluster"))
        val chainLockConfig = addLockModule(evmChainConfig)

        testUpdateWithLock(eventReceiverBrid, GtvEncoder.encodeGtv(chainLockConfig), node1.evm)
    }

    @Test
    @Order(22)
    fun `Lock test - TXS`() {
        testLogger.info("Updating transaction submitter chain config with lock module")

        val chainLockConfig = addLockModule(txsChainConfig)

        testUpdateWithLock(txSubmitterBrid, GtvEncoder.encodeGtv(chainLockConfig), node1.txs)
    }

    private fun testUpdateWithLock(bcRid: BlockchainRid, config: ByteArray, chainClient: PostchainClient) {

        testLogger.info("Updating directory chain config with lock module")

        with(node1.c0) {

            try {
                transactionBuilder()
                        .proposeConfigurationOperation(provider1KeyPair.pubKey.data, bcRid, config, "", null)
                        .postTransactionUntilConfirmed("Updated chain config", retries = 10)

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

    private fun addLockModule(chain0LockConfig: String) =
            GtvFactory.gtv(GtvMLParser.parseGtvML(chain0LockConfig).asDict().mapValues { configRootEntry ->
                if (configRootEntry.key == "gtx") {
                    GtvFactory.gtv(configRootEntry.value.asDict().mapValues { configGtxEntry ->
                        if (configGtxEntry.key == "modules") {
                            val modules = configGtxEntry.value.asArray()
                                    .filter { it.asString() != "net.postchain.rell.module.RellPostchainModuleFactory" }
                                    .toTypedArray()
                            GtvFactory.gtv(listOf(
                                    GtvFactory.gtv("net.postchain.rell.module.RellPostchainModuleFactory"),
                                    GtvFactory.gtv(lockGtxModule),
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