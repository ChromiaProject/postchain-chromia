package net.postchain.images.directory1

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.postchain.chain0.common.init.initOperation
import net.postchain.chain0.common.queries.getContainers
import net.postchain.chain0.common.queries.getNodeData
import net.postchain.chain0.common.queries.getSummary
import net.postchain.chain0.direct_container.createContainerOperation
import net.postchain.common.BlockchainRid
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.gtv.GtvEncoder
import net.postchain.gtx.Gtx
import net.postchain.images.common.ManagedModeBase
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junitpioneer.jupiter.DisableIfTestFails
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import kotlin.test.assertEquals

private const val systemRellSource = "../chain0-impl/rell/src"

@Testcontainers
@DisableIfTestFails // Will abort test execution if any test case fails
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class ReconfigurationBase {

    companion object : ManagedModeBase(systemRellSource) {
        private val dapps = mutableMapOf<String, BlockchainRid>()
        private val dappTxs = mutableMapOf<BlockchainRid, Gtx>()
        private const val systemContainer = "system"
        private const val foobarContainer = "foobar"
    }

    abstract val numberOfMasterNodes: Int

    @Test
    @Order(1)
    fun `Directory1 is deployed and initialized`() {
        node1Db.awaitBlockHeight(0)

        with(node1.c0) {
            // compile cluster anchoring dapp
            val clusterAnchoringDapp = compileChain("anchoring/blockchain_config_cluster_anchoring.run.xml", File(systemRellSource))
            val clusterAnchoringGtvConfig = getBaseConfig(clusterAnchoringDapp.config.chains.first().configs.entries.first().value)

            // compile system anchoring dapp
            val systemAnchoringDapp = compileChain("anchoring/blockchain_config_system_anchoring.run.xml", File(systemRellSource))
            val systemAnchoringGtvConfig = getBaseConfig(systemAnchoringDapp.config.chains.first().configs.entries.first().value)

            // Initializing
            transactionBuilder()
                    .initOperation(GtvEncoder.encodeGtv(systemAnchoringGtvConfig), GtvEncoder.encodeGtv(clusterAnchoringGtvConfig))
                    .postTransactionUntilConfirmed("init")
            assert(getSummary().providers).isEqualTo(1L)
            assert(getNodeData(node1.nodeKeyPair.pubKey).active).isTrue()
            assertAnchoringChainProperties()
            assertAnchoringChainsFunctional()

            // Add new container
            transactionBuilder()
                    .createContainerOperation(
                            node1.providerPubkey,
                            foobarContainer,
                            "system",
                            1,
                            listOf(node1.provider.pubKey.data)
                    )
                    .postTransactionUntilConfirmed("$foobarContainer container")

            awaitUntilAsserted {
                val containers = getContainers().map { it.name }.toSet()
                assertEquals(setOf(systemContainer, foobarContainer), containers)
            }
        }
    }

    /*
    `Reconfigure chain0`
    `Reconfigure cluster anchoring chain`
    `Reconfigure system anchoring chain`
     */

}