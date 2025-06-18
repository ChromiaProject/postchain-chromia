package net.postchain.images.directory1

import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair

class Directory1DeploymentMixIT : Directory1DeploymentBase("deployment-mix") {

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

    override val numberOfMasterNodes = 1
}