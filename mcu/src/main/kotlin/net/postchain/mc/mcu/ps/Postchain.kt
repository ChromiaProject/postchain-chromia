package net.postchain.mc.mcu.ps

import mu.KLogging
import net.postchain.gtv.GtvFactory
import net.postchain.mc.cli.base.CliError
import net.postchain.mc.cli.chromia0.CliExecutionC0
import net.postchain.mc.config.app.ClientConfig
import net.postchain.mc.mcu.config.Config
import net.postchain.mc.mcu.model.Dapp
import java.io.File

class Postchain(val config: Config) {

    companion object : KLogging()

    fun initDevnet() {
        try {
            // Add provider0
            // ./pmc.sh register-provider -cfg cfg-admin.properties -k 03EFA243CAF1D442125029578850882B10157A4AAC1794DFEF2AB78D0E82E1954C
            onAdminConfig {
                it.registerProvider(config.providerPubKey)
            }
            pause()

            // Update provider0
            // ./pmc.sh update-provider -cfg cfg-provider.properties -k 03EFA243CAF1D442125029578850882B10157A4AAC1794DFEF2AB78D0E82E1954C --name provider0
            onProviderConfig {
                it.updateProvider(config.providerPubKey, "provider0", "")
            }
            pause()

            // Enable provider0
            // ./pmc.sh enable-provider -cfg cfg-admin.properties -k 03EFA243CAF1D442125029578850882B10157A4AAC1794DFEF2AB78D0E82E1954C
            onAdminConfig {
                it.enableProvider(config.providerPubKey)
            }
            pause()

            // Add (self) node0
            // ./pmc.sh add-node -cfg cfg-provider.properties -h 172.26.32.1 -p 9870 -k 020CCD8A16F1CA7433D2EA5880D727AB8563B25F33CAA2201E7691AB373FBABFCF
            onProviderConfig {
                val node0 = config.nodes.first()
                it.addNode(node0.key, node0.host, node0.port)
            }
            pause()

            // Add blockchain chromia0
            // ./pmc.sh add-blockchain -cfg cfg-admin.properties -bc cfg-chromia0-container.xml -n 020CCD8A16F1CA7433D2EA5880D727AB8563B25F33CAA2201E7691AB373FBABFCF -fmt xml
            onAdminConfig {
                it.addBlockchain(config.chain0Config0Path, getSigners(), "xml")
            }

        } catch (e: CliError.Companion.CliException) {
            println("Error occurred: " + e.message)
        }
    }

    private fun pause() {
        Thread.sleep(2000L)
    }

    fun launchBlockchain(dappName: String) {
        // ./pmc.sh add-blockchain -cfg cfg-admin.properties -bc cfg-cities.xml -n 020CCD8A16F1CA7433D2EA5880D727AB8563B25F33CAA2201E7691AB373FBABFCF -fmt xml
        forDapp(dappName) { dapp ->
            onAdminConfig { cli ->
                cli.addBlockchain(dapp.config0Path, config.nodes.first().key, "xml")
            }
        }
    }

    fun pauseBlockchain(dappName: String) {
        // ./pmc.sh stop-blockchain -cfg cfg-admin.properties -brid F4BFBB70A9F0A91540AC57F4F1F9DF9E19AA46CB4DE3DA3F86F2E925DB883016 -r
        forDapp(dappName) { dapp ->
            onAdminConfig { cli ->
                cli.stopBlockchain(dapp.brid, true)
            }
        }
    }

    fun resumeBlockchain(dappName: String) {
        // -hd should be > (last_config_height + 1) -- see chromia0 dapp
        // ./pmc.sh add-blockchain-signers -cfg cfg-admin.properties -brid F4BFBB70A9F0A91540AC57F4F1F9DF9E19AA46CB4DE3DA3F86F2E925DB883016 -hd 6 -s 020CCD8A16F1CA7433D2EA5880D727AB8563B25F33CAA2201E7691AB373FBABFCF
        forDapp(dappName) { dapp ->
            val heightDelay = dapp.resume()
            Config.toFile(config, Config.DEFAULT_FILENAME) // TODO: [!]
            onAdminConfig { cli ->
                cli.addBlockchainSigners(dapp.brid, getSigners(), heightDelay)
            }
        }
    }

    fun configureBlockchain(dappName: String, configName: String) {
        // ./pmc.sh add-configuration -cfg cfg-admin.properties -brid 1332F03A3E0AF426C810970C74FABDC5188D4C8D8FDC9417E47099D7399AC522 -bc cfg-books-fast.xml -h 130 -fmt xml
        if (dappName == config.chain0Name) {
            onAdminConfig { cli ->
                val configDelay = 10
                val lastHeight = cli.getBlockchainLastHeight(config.chain0BlockchainRid)
                val newHeight = lastHeight + configDelay
                val configFilename = config.chain0Configs[configName]
                if (configFilename != null && File(configFilename).exists()) {
                    println("Config $configName will be added in $configDelay blocks at height $newHeight")
                    cli.addConfiguration(config.chain0BlockchainRid, configFilename, newHeight, "xml")
                }
            }
        } else {
            forDapp(dappName) { dapp ->
                //val heightDelay = dapp.configure()
                //Config.toFile(config, Config.DEFAULT_FILENAME) // TODO: [!]
                onAdminConfig { cli ->
                    println(cli.getBlockchainLastHeight(dapp.brid))
                    //cli.addConfiguration(dapp.brid, getSigners(), heightDelay)
                }
            }
        }
    }

    fun postTx(dappName: String, opName: String, key: String, value: ByteArray) {
        executeAsAdmin(dappName) { dapp, cli ->
            cli.sendTxUnconfirmed { txBuilder ->
                txBuilder.addOperation(
                        opName,
                        arrayOf(
                                GtvFactory.gtv(key),
                                GtvFactory.gtv(value)
                        ))
            }
        }
    }

    private fun onAdminConfig(action: (CliExecutionC0) -> Unit) {
        val config = object : ClientConfig {
            override val apiURL: String = config.nodes.first().apiUrl
            override val brid: String = config.chain0BlockchainRid
            override val privKey: String = config.adminPrivKey
            override val pubKey: String = config.adminPubKey
        }
        action(CliExecutionC0(config))
    }

    private fun onProviderConfig(action: (CliExecutionC0) -> Unit) {
        val config = object : ClientConfig {
            override val apiURL: String = config.nodes.first().apiUrl
            override val brid: String = config.chain0BlockchainRid
            override val privKey: String = config.providerPrivKey
            override val pubKey: String = config.providerPubKey
        }
        action(CliExecutionC0(config))
    }

    // TODO: Merger 'forDapp' and 'onAdminConfig' into single onContext(dapp, config) method. (See below.)
    private fun forDapp(dappName: String, action: (dapp: Dapp) -> Unit) {
        val dapp = config.dapps.firstOrNull { it.name == dappName }
        if (dapp != null) {
            action(dapp)
        }
    }

    private fun executeAsAdmin(dappName: String, action: (dapp: Dapp, cli: CliExecutionC0) -> Unit) {
        val dapp = config.dapps.firstOrNull { it.name == dappName }
        if (dapp == null) {
            logger.error { "Can't find dapp: $dappName" }
            return
        }

        val config = object : ClientConfig {
            override val apiURL: String = config.nodes.first().apiUrl
            override val brid: String = dapp.brid
            override val privKey: String = config.adminPrivKey
            override val pubKey: String = config.adminPubKey
        }
        val cli = CliExecutionC0(config)

        action(dapp, cli)
    }

    private fun getSigners(): String {
        return config.nodes.first().key
    }

}