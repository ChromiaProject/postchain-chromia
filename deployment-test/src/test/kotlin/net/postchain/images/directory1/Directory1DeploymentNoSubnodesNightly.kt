package net.postchain.images.directory1

import net.postchain.crypto.KeyPair
import org.testcontainers.containers.output.Slf4jLogConsumer

class Directory1DeploymentNoSubnodesNightly : Directory1DeploymentBase() {

    companion object {
        init {
            node1 = postchainServer("node1", Slf4jLogConsumer(node1Logger.underlyingLogger, true),
                    KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"),
                    "config-no-subnodes")
                    .withEnv("POSTCHAIN_PCU", true.toString())
            node2 = postchainServer("node2", Slf4jLogConsumer(node2Logger.underlyingLogger, true),
                    KeyPair.of("03F9ABC05F7D7639AEC97B18784D5C83CA82D1EAF8F96DC31E77A83F21DDE67F95", "FFC28105CFE2CC336624DCDFDEDB58157B37ED565C29F11A3B54B8F721DBA7C5"),
                    "config-no-subnodes")
                    .withEnv("POSTCHAIN_PCU", true.toString())
/*
            node3 = postchainServer("node3", Slf4jLogConsumer(node3Logger.underlyingLogger, true),
                    KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                    "config-no-subnodes")
                    .withEnv("POSTCHAIN_PCU", true.toString())

 */

            removeSubnodeContainers()
            startNodesAndChain0()
        }
    }

    override val numberOfMasterNodes = 0
}