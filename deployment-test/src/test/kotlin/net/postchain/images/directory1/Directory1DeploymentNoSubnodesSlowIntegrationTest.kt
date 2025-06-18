package net.postchain.images.directory1

import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair

class Directory1DeploymentNoSubnodesSlowIntegrationTest : Directory1DeploymentBase("deployment-no-subnodes") {

    init {
        node1 = postchainServer("node1",
                provider1KeyPair,
                "config-no-subnodes")
        node2 = postchainServer("node2",
                provider2KeyPair,
                "config-no-subnodes")
                .withGenesisNode(node1)
        node3 = postchainServer("node3",
                provider3KeyPair,
                "config-no-subnodes")
                .withGenesisNode(node1)

        removeSubnodeContainers()
        startNodesAndChain0()
    }

    override val numberOfMasterNodes = 0
}