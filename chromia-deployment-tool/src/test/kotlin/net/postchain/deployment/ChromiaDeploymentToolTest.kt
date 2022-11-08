package net.postchain.deployment

import assertk.assert
import assertk.assertions.isEqualTo
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.gtvml.GtvMLParser
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText

class ChromiaDeploymentToolTest {
    private val rootResourcePath = "src/test/resources/net/postchain/deployment"

    private val cryptoSystem = Secp256K1CryptoSystem()

    @Test
    fun generateInitialConfiguration(@TempDir testOutput: Path) {
        val sourceDir = Path.of("$rootResourcePath/sources")
        val runXmlFile = Path.of("$rootResourcePath/config/deploy.xml")
        val blockchainConfiguration =
            ChromiaDeploymentTool(ConcretePostchainClientProvider()).generateConfig(sourceDir, runXmlFile, testOutput)

        val config = GtvDecoder.decodeGtv(testOutput.resolve("city.gtv").readBytes())
        val configXml = GtvMLParser.parseGtvML(testOutput.resolve("city.xml").readText())
        assert(config).isEqualTo(configXml)
        assert(config["blockstrategy"]!!["maxblocktime"]!!.asInteger()).isEqualTo(2000L)

        val expectedBlockchainRid = BlockchainRid(config.merkleHash(GtvMerkleHashCalculator(cryptoSystem)))

        assert(blockchainConfiguration.blockchainName).isEqualTo("city")
        assert(blockchainConfiguration.generatedBlockchainRid).isEqualTo(expectedBlockchainRid)
        assert(blockchainConfiguration.containerName).isEqualTo("test-container")
    }

    @Test
    fun generateUpdateConfiguration(@TempDir testOutput: Path) {
        val sourceDir = Path.of("$rootResourcePath/sources")
        val runXmlFile = Path.of("$rootResourcePath/config/deploy-update.xml")
        val blockchainConfiguration =
            ChromiaDeploymentTool(ConcretePostchainClientProvider()).generateConfig(sourceDir, runXmlFile, testOutput)

        val config = GtvDecoder.decodeGtv(testOutput.resolve("city.gtv").readBytes())
        val configXml = GtvMLParser.parseGtvML(testOutput.resolve("city.xml").readText())
        assert(config).isEqualTo(configXml)
        assert(config["blockstrategy"]!!["maxblocktime"]!!.asInteger()).isEqualTo(2000L)

        val expectedBlockchainRid = BlockchainRid("6A9398B45D864BEF53BCBDF0F4B203701F986036AFE8A3544C9D681E30096E3B".hexStringToByteArray())

        assert(blockchainConfiguration.blockchainName).isEqualTo("city")
        assert(blockchainConfiguration.specifiedBlockchainRid).isEqualTo(expectedBlockchainRid)
    }

    @Test
    fun generateConfigurationSyntaxError(@TempDir testOutput: Path) {
        val sourceDir = Path.of("$rootResourcePath/sources")
        val runXmlFile = Path.of("$rootResourcePath/config/broken-deploy.xml")
        assertThrows<UserMistake> {
            ChromiaDeploymentTool(ConcretePostchainClientProvider()).generateConfig(
                sourceDir,
                runXmlFile,
                testOutput
            )
        }
    }
}
