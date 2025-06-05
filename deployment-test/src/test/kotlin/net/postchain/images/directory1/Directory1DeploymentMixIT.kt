package net.postchain.images.directory1

import net.postchain.images.directory1.Directory1TestBase.Companion.provider1KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider2KeyPair
import net.postchain.images.directory1.Directory1TestBase.Companion.provider3KeyPair
import org.testcontainers.containers.output.Slf4jLogConsumer

class Directory1DeploymentMixIT : Directory1DeploymentBase() {

    companion object {
        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    provider1KeyPair,
                    "config-mix")
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    provider2KeyPair,
                    "config-mix")
                    .withEnv("POSTCHAIN_GENESIS_PUBKEY", node1.pubkey.hex())
                    .withEnv("POSTCHAIN_GENESIS_HOST", node1.nodeHost)
                    .withEnv("POSTCHAIN_GENESIS_PORT", node1.nodePort.toString())
            node3 = postchainMasterChildServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    provider3KeyPair,
                    "config-mix",
                    true)

            removeSubnodeContainers()
            startNodesAndChain0()
        }
    }

    override val numberOfMasterNodes = 1
}