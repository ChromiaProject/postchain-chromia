package net.postchain.mc.test

import net.postchain.mc.cli.enterprise0.CliExecution
import net.postchain.mc.config.app.ClientConfig
import org.junit.Test
import java.nio.file.Paths

class Enterprise0Test : ManagedModeTest() {

    override fun cliExecution(cliConfig: ClientConfig): net.postchain.mc.cli.common0.CliExecution {
        return CliExecution(cliConfig)
    }
    @Test
    fun testRegisterProvider() {
        val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
        val provConfig = cliConf(provConfigMap)
//        testRegisterProviderInternal(configFileName, cliConf(clientConfigMap), provConfig.pubKey)
        testRegisterProviderInternal(configFileName, cliConf(clientConfigMap))
     }

    @Test
    fun testProposeConfigurationAndVote() {
        val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
        val clientConfig = cliConf(clientConfigMap)
        val provConfig = cliConf(provConfigMap)

        addNodeAndBlockchain(configFileName, clientConfig, provConfig)

        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml"
//        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/ai_blockchain_config.xml"

//        I must be a block signer to have authorization to propose changes and vote on them. Thus create a block signer client.
        val nodeConfig = nodes[0].nodeConfigProvider.getConfiguration()
        val blockSignerClient = cliConf(mapOf("pubkey" to nodeConfig.pubKey, "privkey" to nodeConfig.privKey))
        val executor = CliExecution(blockSignerClient)
        executor.proposeConfiguration(clientConfig.brid, blockSignerClient.pubKey, blockchainConfigFile, 20L, "xml")
        executor.vote(clientConfig.brid, blockSignerClient.pubKey, 20L, true)
    }


}