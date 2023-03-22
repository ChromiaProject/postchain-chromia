package net.postchain.mc.test

import mu.KLogging
import net.postchain.base.BaseBlockBuildingStrategyConfigurationData
import net.postchain.client.config.PostchainClientConfig
import net.postchain.client.request.EndpointPool
import net.postchain.common.BlockchainRid
import net.postchain.concurrent.util.get
import net.postchain.core.*
import net.postchain.core.block.BlockBuilder
import net.postchain.core.block.BlockBuildingStrategy
import net.postchain.core.block.BlockData
import net.postchain.core.block.BlockQueries
import net.postchain.crypto.KeyPair
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.utils.configuration.BlockchainSetup
import net.postchain.devtools.utils.configuration.BlockchainSetupFactory
import net.postchain.devtools.utils.configuration.system.SystemSetupFactory
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import net.postchain.rell.module.RellVersions
import net.postchain.rell.tools.runcfg.RellRunConfigGenerator
import java.io.File
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

    protected fun runXmlFile(): File {
        return File.createTempFile("run", ".xml").apply {
            bufferedWriter().use { out -> out.write(runXml()) }
        }
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

    protected fun cliConf(keyIndex: Int, blockchainRid: BlockchainRid? = null): PostchainClientConfig {
        return PostchainClientConfig(
                blockchainRid ?: nodes[0].getBlockchainRid(0)!!,
                EndpointPool.singleUrl("http://127.0.0.1:" + nodes[0].getRestApiHttpPort()),
                listOf(
                        KeyPair.of(KeyPairHelper.pubKeyHex(keyIndex), KeyPairHelper.privKeyHex(keyIndex))
                )
        )
    }

    /**
     * Create a node running the rell source in rellSourceDir
     */
    protected fun run(runConfigFile: File, rellSourceDir: File): Gtv {
        val appConfig = RellRunConfigGenerator.generateCli(rellSourceDir, runConfigFile, RellVersions.VERSION, false)

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
        systemSetup.nodeConfProvider = "net.postchain.devtools.utils.configuration.TestNodeConfigurationProvider" // "managed" not implemented yet. See NodeConfigurationProviderGenerator
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
        configData: BaseBlockBuildingStrategyConfigurationData,
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

    override fun blockFailed() {
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