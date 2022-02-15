package net.postchain.mc.test

import mu.KLogging
import net.postchain.base.BaseBlockchainConfigurationData
import net.postchain.core.*
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.KeyPairHelper
import net.postchain.devtools.utils.configuration.BlockchainSetup
import net.postchain.devtools.utils.configuration.BlockchainSetupFactory
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.mc.config.app.BaseClientConfig
import net.postchain.mc.config.app.ClientConfig
import net.postchain.mc.config.app.DelegatingClientConfig
import net.postchain.rell.model.R_LangVersion
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import org.apache.commons.configuration2.MapConfiguration
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue

abstract class RellIntegrationTest : IntegrationTestSetup() {

    companion object : KLogging()

    protected fun runXml(): String {
        return """
            <run wipe-db="true">
                <nodes>
                    <config add-signers="false" >
                    #
                    </config>
                </nodes>
                ${chainConfSnippet()}
            </run>
        """.trimIndent()
    }

    /**
     * Override this method with a chain conf snippet. For example:
     *
    """
    <chains>
        <chain name="manager" iid="0">
            <config height="0" add-dependencies="false">
                <app module="myModule">
                    <args module="myModule">
                        <arg key="admin"><bytea>${KeyPairHelper.pubKeyHex(7)}</bytea></arg>
                    </args>
                </app>
                <gtv path="signers">
                    <array>
                        <bytea>${KeyPairHelper.pubKeyHex(7)}</bytea>
                    </array>
                </gtv>
            </config>
        </chain>
    </chains>
    """
     *
     */
    protected abstract fun chainConfSnippet(): String

    protected fun cliConf(keyIndex: Int): ClientConfig {
        val base = MapConfiguration(mapOf(
                "pubkey" to KeyPairHelper.pubKeyHex(keyIndex),
                "privkey" to KeyPairHelper.privKeyHex(keyIndex)
        ))
        return object : DelegatingClientConfig(BaseClientConfig(base)) {
            override val apiURL: String
                get() = "http://127.0.0.1:" + nodes[0].getRestApiHttpPort()

            override val brid: String
                get() = nodes[0].getBlockchainRid(0)!!.toHex()
        }
    }

    /**
     * Create a node running the rell source in rellSourceDir, which is
     * relative to the folder src/main/rell
     */
    protected fun run(rellSourceDir: String): Gtv {
        // Create blockchain config file
        val resourceDirectory = Paths.get("src", "main", "rell", rellSourceDir)
        val rellSourceDir = resourceDirectory.toFile()

        val tempRunXml = File.createTempFile("run", ".xml")
        tempRunXml.bufferedWriter().use { out -> out.write(runXml()) }

        return run(tempRunXml, rellSourceDir)
    }

    private fun run(runConfigFile: File, rellSourceDir: File): Gtv {
        val appConfig = RellRunConfigGenerator.generateCli(rellSourceDir, runConfigFile, R_LangVersion.of("0.10.8"), false)

        val blockchainSetups = mutableListOf<BlockchainSetup>()
        val blockchainConfigsGtv = mutableListOf<Gtv>()
        for (chain in appConfig.config.chains) {
/*
            <entry key="blockstrategy">
            <!--
            This block strategy is the default one if no config is made
            maxblocktime=30000 (milliseconds)
            blockdelay=1000 (milliseconds)
            -->
            <dict>
            <entry key="name">
            <string>net.postchain.devtools.OnDemandBlockBuildingStrategy</string>
            </entry>
            </dict>
            </entry>*/
            val bcGtv = chain.configs[0]!!
            val dict = bcGtv.gtvConfig.asDict().toMutableMap()
            dict["blockstrategy"] = GtvFactory.gtv(mapOf("name" to GtvFactory.gtv("net.postchain.mc.test.SmartOnDemandBlockBuildingStrategy")))
            val moddedGtv = GtvFactory.gtv(dict)

            val bs = BlockchainSetupFactory.buildFromGtv(0, moddedGtv)
//            val bs = BlockchainSetupFactory.buildFromGtv(0, chain.configs[0]!!)
            blockchainSetups.add(bs)
            blockchainConfigsGtv.add(moddedGtv)
        }

        val systemSetup = SystemSetupFactory.buildSystemSetup(blockchainSetups)
        systemSetup.nodeConfProvider = "legacy" // "managed" not implemented yet. See NodeConfigurationProviderGenerator
        systemSetup.confInfrastructure = "net.postchain.managed.ManagedEBFTInfrastructureFactory"
        systemSetup.chainConfProvider = "managed"
        systemSetup.needRestApi = true

        createNodesFromSystemSetup(systemSetup, true)
        return blockchainConfigsGtv[0]
        //return appConfig.config.chains[0].configs[0]!!
    }
}

@Suppress("UNUSED_PARAMETER")
class SmartOnDemandBlockBuildingStrategy(
        configData: BaseBlockchainConfigurationData,
        val blockchainConfiguration: BlockchainConfiguration,
        blockQueries: BlockQueries,
        val txQueue: TransactionQueue
) : BlockBuildingStrategy {

    companion object : KLogging()

    @Volatile
    var upToHeight: Long = -1

    @Volatile
    var committedHeight = blockQueries.getBestHeight().get().toInt()
    val blocks = LinkedBlockingQueue<BlockData>()

    override fun shouldBuildBlock(): Boolean {
        return upToHeight > committedHeight
    }

    fun buildBlocksUpTo(height: Long) {
        upToHeight = height
    }

    override fun blockCommitted(blockData: BlockData) {
        committedHeight++
        blocks.add(blockData)
    }

    fun awaitCommitted(height: Int) {
        while (committedHeight < height) {
            blocks.take()
        }
    }

    override fun shouldStopBuildingBlock(bb: BlockBuilder): Boolean {
        return false
    }
}