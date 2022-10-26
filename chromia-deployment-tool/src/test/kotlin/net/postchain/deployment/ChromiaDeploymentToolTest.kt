package net.postchain.deployment

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import net.postchain.client.core.ConcretePostchainClientProvider
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
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
    fun generateConfiguration(@TempDir testOutput: Path) {
        val sourceDir = Path.of("$rootResourcePath/sources")
        val runXmlFile = Path.of("$rootResourcePath/config/deploy.xml")
        val blockchainConfigurations =
            ChromiaDeploymentTool(ConcretePostchainClientProvider()).generateConfig(sourceDir, runXmlFile, testOutput)

        assert(blockchainConfigurations.configurations.size).isEqualTo(2)

        val config0 = GtvDecoder.decodeGtv(testOutput.resolve("0.gtv").readBytes())
        val config0Xml = GtvMLParser.parseGtvML(testOutput.resolve("0.xml").readText())
        assert(config0).isEqualTo(config0Xml)

        val config1 = GtvDecoder.decodeGtv(testOutput.resolve("1.gtv").readBytes())
        val config1Xml = GtvMLParser.parseGtvML(testOutput.resolve("1.xml").readText())
        assert(config1).isEqualTo(config1Xml)

        assert(config1).isNotEqualTo(config0)

        val expectedBlockchainRid = BlockchainRid(config0.merkleHash(GtvMerkleHashCalculator(cryptoSystem)))

        assert(blockchainConfigurations.name).isEqualTo("city")
        assert(blockchainConfigurations.blockchainRid).isEqualTo(expectedBlockchainRid)
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

    @Test
    fun generateConfigurationFirstNot0(@TempDir testOutput: Path) {
        val sourceDir = Path.of("$rootResourcePath/sources")
        val runXmlFile = Path.of("$rootResourcePath/config/first-not-0-deploy.xml")
        assertThrows<UserMistake> {
            ChromiaDeploymentTool(ConcretePostchainClientProvider()).generateConfig(
                sourceDir,
                runXmlFile,
                testOutput
            )
        }
    }

    @Test
    fun generateConfigurationWrongOrder(@TempDir testOutput: Path) {
        val sourceDir = Path.of("$rootResourcePath/sources")
        val runXmlFile = Path.of("$rootResourcePath/config/wrong-order-deploy.xml")
        assertThrows<UserMistake> {
            ChromiaDeploymentTool(ConcretePostchainClientProvider()).generateConfig(
                sourceDir,
                runXmlFile,
                testOutput
            )
        }
    }
}
