package net.postchain.images.directory1

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.chain0.direct_container.removeContainerOperation
import net.postchain.containers.infra.ContainerNodeConfig
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path

class Directory1DeploymentAllSubnodesSlowIntegrationTest : Directory1DeploymentBase("deployment-subnodes") {

    init {
        node1 = postchainServerWithSubnodes("node1",
                provider1KeyPair,
                "config-all-subnodes")
        node2 = postchainServerWithSubnodes("node2",
                provider2KeyPair,
                "config-all-subnodes")
                .withGenesisNode(node1)
        node3 = postchainServerWithSubnodes("node3",
                provider3KeyPair,
                "config-all-subnodes")
                .withGenesisNode(node1)

        removeSubnodeContainers()
        startNodesAndChain0()
    }

    override val numberOfMasterNodes = 3

    @Test
    @Order(100) // Run last
    fun `Remove container and verify storage cleanup`() {

        nodes().forEach { node ->
            val containerDir = ContainerNodeConfig.fromAppConfig(node.appConfig).hostMountDir

            assertThat(Files.list(Path(containerDir)).filter {
                val name = it.toFile().name
                name.contains("-${fooContainer}-") || name.contains("-${barContainer}-")
            }.count()).isEqualTo(2)
        }

        with(node1.c0) {
            transactionBuilder()
                    .removeContainerOperation(node1.providerPubkey, fooContainer)
                    .removeContainerOperation(node1.providerPubkey, barContainer)
                    .postTransactionUntilConfirmed("$fooContainer and $barContainer containers removed")
        }

        nodes().forEach { node ->

            testLogger.info("Waits for container data to be removed on node ${node.pubkey}")

            val containerDir = ContainerNodeConfig.fromAppConfig(node.appConfig).hostMountDir
            awaitUntilAsserted {
                assertThat(Files.list(Path(containerDir)).filter {
                    val name = it.toFile().name
                    name.contains("-${fooContainer}-") || name.contains("-${barContainer}-")
                }.count()).isEqualTo(0)
            }
        }
    }
}