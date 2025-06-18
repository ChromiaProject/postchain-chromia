package net.postchain.images.directory1

import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair
import org.testcontainers.containers.output.Slf4jLogConsumer

class Directory1DeploymentNoSubnodesSlowIntegrationTest : Directory1DeploymentBase() {

    companion object {
        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    provider1KeyPair,
                    "config-no-subnodes")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    provider2KeyPair,
                    "config-no-subnodes")
                    .withGenesisNode(node1)
            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    provider3KeyPair,
                    "config-no-subnodes")
                    .withGenesisNode(node1)

            removeSubnodeContainers()
            startNodesAndChain0()
        }
    }

    override val numberOfMasterNodes = 0
}