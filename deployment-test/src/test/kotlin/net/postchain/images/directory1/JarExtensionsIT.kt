package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.queries.getBlockchains
import net.postchain.chain0.common.queries.getContainers
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSubnodeJarExtensions
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_container.createContainerWithResourceLimitsAndJarExtensionsOperation
import net.postchain.chain0.model.ContainerResourceLimitType.container_units
import net.postchain.chain0.model.SubnodeJarExtensionType
import net.postchain.chain0.proposal_subnode_jar_extension.proposeSubnodeJarExtensionOperation
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.images.common.ManagedModeBase
import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class JarExtensionsIT : ManagedModeBase("jar_extensions") {

    companion object {
        const val JAR_EXTENSION_NAME = "chromia_devtools"
    }

    private val dappContainer = "dappContainer"

    init {
        node1 = postchainServerWithSubnodes("node1",
                provider1KeyPair,
                "config-all-subnodes")

        startNodesAndChain0()
    }

    @AfterAll
    fun tearDown() {
        super.breakdown()
    }

    @Test
    @Order(1)
    fun `Chain0 dapp is deployed`() {
        getDb(node1).awaitBlockHeight(0)
    }

    @Test
    @Order(2)
    fun `Initialize network with provider1`() {
        with(node1.c0) {
            val clusterAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/cluster_anchoring.xml")!!.readText())
            val systemAnchoringGtvConfig = GtvMLParser.parseGtvML(this::class.java.getResource("/directory1deployment/system_anchoring.xml")!!.readText())

            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .postTransactionUntilConfirmed("init")

            awaitUntilAsserted {
                assertThat(getSummary().providers).isEqualTo(1L)
                assertThat(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            }
        }

        assertAnchoringChainProperties()
    }

    @Test
    @Order(3)
    fun `Add JAR extension`() {
        with(node1.c0) {
            val chromiaDevtoolsJarFile = Path("../chromia-devtools/target/")
                    .listDirectoryEntries()
                    .find { it.name.matches("chromia-devtools-.*.jar".toRegex()) && !it.name.endsWith("-sources.jar") }!!

            transactionBuilder().proposeSubnodeJarExtensionOperation(
                    node1.providerPubkey, JAR_EXTENSION_NAME, chromiaDevtoolsJarFile.toFile().readBytes(),
                    SubnodeJarExtensionType.COMMON, "Dummy", "net.postchain.d1.dummy.DummyExtensionGTXModule",
                    "net.postchain.d1.dummy.DummySynchronizationInfrastructureExtension", "", null, "")
                    .postTransactionUntilConfirmed("$JAR_EXTENSION_NAME extension proposed")

            awaitQueryResult {
                assertThat(getSubnodeJarExtensions().map { it.name }).containsExactly(JAR_EXTENSION_NAME)
            }
        }
    }

    @Test
    @Order(4)
    fun `Add new container with JAR extension`() {
        with(node1.c0) {
            // Asserting that there is only one container (system) before test
            awaitQueryResult {
                assertThat(getSummary().containers).isEqualTo(1L)
            }

            transactionBuilder()
                    .createContainerWithResourceLimitsAndJarExtensionsOperation(
                            node1.providerPubkey, dappContainer, systemCluster, 1,
                            listOf(node1.provider.pubKey.data), mapOf(container_units to 1),
                            listOf(JAR_EXTENSION_NAME), null
                    )
                    .postTransactionUntilConfirmed("$dappContainer container created")

            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertThat(containers).isEqualTo(setOf(systemContainer, dappContainer))
            }
        }
    }

    @Test
    @Order(5)
    fun `Deploy dapp`() {
        assertThat(node1.c0.getBlockchains(true).size).isEqualTo(3)

        deployDapp("test_ticker_jar_extension", dappContainer, assertSigners = arrayOf(node1))

        // Asserting that blockchain is added
        assertThat(node1.c0.getBlockchains(true).size).isEqualTo(4)
    }

    @Test
    @Order(6)
    fun `Query dapp`() {
        with(node1.client(dapps["test_ticker_jar_extension"]!!)) {
            // Let's wait until we have seen some ticks, then we are happy
            awaitQueryResult {
                assertThat(query("get_latest_tick", gtv(mapOf())).asInteger()).isGreaterThan(5)
            }
        }
    }
}
