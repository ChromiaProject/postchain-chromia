package net.postchain.images.directory1

import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair
import org.testcontainers.containers.output.Slf4jLogConsumer

class Directory1DeploymentAllSubnodesSlowIntegrationTest : Directory1DeploymentBase() {

    companion object {
        init {
            node1 = postchainMasterChildServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    provider1KeyPair,
                    "config-all-subnodes",
                    true,
                    true)
            node2 = postchainMasterChildServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    provider2KeyPair,
                    "config-all-subnodes",
                    true)
            node3 = postchainMasterChildServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    provider3KeyPair,
                    "config-all-subnodes",
                    true)

            removeSubnodeContainers()
            startNodesAndChain0()
        }
    }

    override val numberOfMasterNodes = 3
}