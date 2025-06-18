package net.postchain.images.directory1

import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair

class Directory1DeploymentAllSubnodesSlowIntegrationTest : Directory1DeploymentBase("deployment-subnodes") {

    init {
        node1 = postchainServerWithSubnodes("node1",
                provider1KeyPair,
                "config-all-subnodes",
                true)
        node2 = postchainServerWithSubnodes("node2",
                provider2KeyPair,
                "config-all-subnodes",
                true)
                .withGenesisNode(node1)
        node3 = postchainServerWithSubnodes("node3",
                provider3KeyPair,
                "config-all-subnodes",
                true)
                .withGenesisNode(node1)

        removeSubnodeContainers()
        startNodesAndChain0()
    }

    override val numberOfMasterNodes = 3
}