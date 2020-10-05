package net.postchain.mc.test

import net.postchain.mc.cli.common0.CliExecution
import net.postchain.mc.config.app.ClientConfig
import org.apache.commons.configuration2.MapConfiguration
import org.junit.Test

class Enterprise0Test : ManagedModeTest() {

    @Test
    fun testRegisterProvider() {
        val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
        testRegisterProviderInternal(configFileName, cliConf(clientConfigMap))
     }

    @Test
    fun testAddConfiguration() {
        val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
        val clientConfig = cliConf(clientConfigMap)
        val provConfig = cliConf(provConfigMap)
        testAddConfigurationInternal(configFileName, clientConfig, provConfig)
    }

    override fun cliExecution(cliConfig: ClientConfig): CliExecution {
        return net.postchain.mc.cli.enterprise0.CliExecution(cliConfig)
    }
}