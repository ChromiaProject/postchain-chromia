package net.postchain.mc.test

import net.postchain.gtv.GtvFactory
import net.postchain.gtv.GtvInteger
import net.postchain.gtv.GtvString
import net.postchain.mc.cli.enterprise0.CliExecution
import net.postchain.mc.config.app.ClientConfig
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class Enterprise0Test : ManagedModeTest() {

    override fun cliExecution(cliConfig: ClientConfig): net.postchain.mc.cli.common0.CliExecution {
        return CliExecution(cliConfig)
    }
    val configFileName = "/net/postchain/mc/test/config/ai_blockchain_config.xml"
    val clientConfig = cliConf(clientConfigMap)
    val provConfig = cliConf(provConfigMap)
    val provExecutor = CliExecution(provConfig)


    @Before
    fun setup() {
        // do stuff
    }

//    @Test
//    fun testProposeAddBlockchain() {
//
//        addNodeAndBlockchain(configFileName, clientConfig, provConfig)
//
//        provExecutor.proposeBlockchain(clientConfig.brid, blockchainConfigFile, 20L, "xml")
//
//        val id = assertProposalTypeAndGetRowid("conf")
//        provExecutor.vote(id, true)
//        assertAddConfiguration(clientConfig)
//    }

    @Test
    fun testProposeConfigurationAndVote() {

        addNodeAndBlockchain(configFileName, clientConfig, provConfig)

        val blockchainConfigFile = Paths.get(".").toAbsolutePath().normalize().toString() + "/src/test/resources/net/postchain/mc/test/config/blockchain_config_1.xml"
        provExecutor.proposeConfiguration(clientConfig.brid, blockchainConfigFile, 20L, "xml")

        val id = assertProposalTypeAndGetRowid("conf")
        provExecutor.vote(id, true)
        assertAddConfiguration(clientConfig)
    }

//    Help function, retrieving the rowid of the proposal. NB: We assume that there exist only _one_ proposal at a time to vote on.
    private fun assertProposalTypeAndGetRowid(expectedType: String): Long {
        val client = getPostchainClient(cliConf(clientConfigMap))
        val proposals = client.query("get_proposals_since", GtvFactory.gtv(
                "since" to GtvFactory.gtv(0L))).get()
        val type = (proposals[0].asDict()["proposal_type"] as GtvString).string
        val id = (proposals[0].asDict()["rowid"] as GtvInteger).asInteger()
        assertEquals(expectedType, type, "Wrong proposal type")
        return id
    }

    @Test
    fun testProposeEnableDisableProvider() {
        val prov2Config = cliConf(prov2ConfigMap)
        addNodeAndBlockchain(configFileName, clientConfig, provConfig)

        provExecutor.proposeProvider(prov2Config.pubKey)
        var id = assertProposalTypeAndGetRowid("register_provider")
        provExecutor.vote(id, true)
        assertProviderRegistered(cliConf(clientConfigMap), prov2Config.pubKey)

        provExecutor.proposeEnableProvider(prov2Config.pubKey)
        id = assertProposalTypeAndGetRowid("provider_state")
        provExecutor.vote(id, true)
        assertProviderEnabled(cliConf(clientConfigMap), prov2Config.pubKey)

        provExecutor.proposeDisableProvider(prov2Config.pubKey)
        id = assertProposalTypeAndGetRowid("provider_state")
        provExecutor.vote(id, true)
        assertProviderDisabled(cliConf(clientConfigMap), prov2Config.pubKey)

    }
}