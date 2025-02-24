package net.postchain.images.directory1

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import mu.KotlinLogging
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.toHex
import net.postchain.common.wrap
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.dapp.PostchainContainer
import net.postchain.dapp.postTransactionUntilConfirmed
import net.postchain.eif.hbridge.LINK_EVM_EOA_ACCOUNT
import net.postchain.eif.hbridge.linkEvmEoaAccountOperation
import net.postchain.eif.lib.ft4.core.auth.Signature
import net.postchain.eif.lib.ft4.external.auth.evmSignaturesOperation
import net.postchain.eif.lib.ft4.external.auth.ftAuthOperation
import net.postchain.eif.lib.ft4.external.auth.getAuthMessageTemplate
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.gtv.merkleHash
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.FastRawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.tx.response.PollingTransactionReceiptProcessor
import java.math.BigInteger
import java.nio.charset.StandardCharsets

// Test base to get common evm container test resources
abstract class EvmTestBase(evmLoggerName: String) : Directory1TestBase() {

    companion object {
        const val EIF_EVENT_RECEIVER_CONTRACT_PLACEHOLDER = "EIF_EVENT_RECEIVER_CONTRACT_PLACEHOLDER"
        const val EVM_EVENT_RECEIVER_CHAIN_NAME = "evm_event_receiver_chain"
    }

    val evmContainerLogger = KotlinLogging.logger(evmLoggerName)
    val evmContainerCredentials = Credentials.create("0x53914554952e5473a54b211a31303078abde83b8128995785901eed28df3f610")
    val evmContainer: GethContainer = GethContainer(logger = Slf4jLogConsumer(evmContainerLogger.underlyingLogger, true))
            .withNetwork(network)
            .apply {
                start()
            }
    val evmContainerNetworkId = 1337L

    // Web3j
    val web3j: Web3j = Web3j.build(HttpService(evmContainer.getExternalGethUrl()))
    val transactionManager: TransactionManager = FastRawTransactionManager(
            web3j,
            evmContainerCredentials,
            PollingTransactionReceiptProcessor(web3j, 1000, 30)
    )
    val gasProvider = DefaultGasProvider()

    val validatorBinary = getBinaryFromArtifactResource("/artifacts/contracts/Validator.sol/Validator.json")
    val tokenBridgeBinary = getBinaryFromArtifactResource("/artifacts/contracts/TokenBridge.sol/TokenBridge.json")
    val testTokenBinary = getBinaryFromArtifactResource("/artifacts/contracts/token/TestToken.sol/TestToken.json")

    // Users
    val aliceEvmAddressStr = "e105ba42b66d08ac7ca7fc48c583599044a6dab3"
    val aliceEvmAddress = aliceEvmAddressStr.hexStringToByteArray()
    val bobEvmAddressStr = "661683e5d36E83B38B1a20247ba6F5c410dC165d"
    val bobEvmAddress = bobEvmAddressStr.hexStringToByteArray()

    val node0EvmAddress = Address("659e4a3726275edFD125F52338ECe0d54d15BD99")

    @AfterAll
    override fun tearDown() {
        evmContainer.stop()
        super.tearDown()
    }

    fun linkAccount(userAuthenticator: FTAuthenticator, userEvmAddress: ByteArray, brid: BlockchainRid) {
        val signature = getLinkEvmEoaAccountSignature(userEvmAddress, evmContainerCredentials.ecKeyPair, userAuthenticator.accountId, brid)

        userAuthenticator.client.transactionBuilder().addNop()
                .evmSignaturesOperation(listOf(userEvmAddress), listOf(signature))
                .ftAuthOperation(userAuthenticator.accountId, userAuthenticator.authDescriptor.id.data)
                .linkEvmEoaAccountOperation(userEvmAddress)
                .postTransactionUntilConfirmed("Link EVM account")
    }

    fun getLinkEvmEoaAccountSignature(addressByteArray: ByteArray, evmKeyPair: ECKeyPair, accountId: ByteArray, brid: BlockchainRid): Signature {
        val opName = LINK_EVM_EOA_ACCOUNT
        val opArgs = gtv(listOf(gtv(addressByteArray)))

        val hashCalculator = GtvMerkleHashCalculatorV2(Secp256K1CryptoSystem())
        val nonce = gtv(listOf(
                gtv(brid),
                gtv(opName),
                opArgs,
                gtv(0),
        )).merkleHash(hashCalculator)

        val template = node1.client(brid).getAuthMessageTemplate(opName, opArgs)
        val message = template.replace("{blockchain_rid}", brid.toHex().uppercase())
                .replace("{nonce}", nonce.toHex().uppercase())
                .replace("{account_id}", accountId.toHex())

        val evmSig = Sign.signPrefixedMessage(
                message.toByteArray(StandardCharsets.UTF_8),
                evmKeyPair
        )
        val signature = Signature(
                evmSig.r.wrap(),
                evmSig.s.wrap(),
                BigInteger(evmSig.v).longValueExact()
        )
        return signature
    }

    fun PostchainContainer.withEifEnv(): PostchainContainer {
        withEnv("POSTCHAIN_EIF_ETHEREUM_URLS", evmContainer.getNetworkGethUrl())
        withEnv("POSTCHAIN_EIF_ETHEREUM_MAX_READ_AHEAD", 200.toString())
        withEnv("POSTCHAIN_EIF_ETHEREUM_MAX_QUEUE_SIZE", 100.toString())
        withEnv("POSTCHAIN_EIF_EVM_MAX_TRY_ERRORS", 1.toString())
        return this
    }

    fun getBinaryFromArtifactResource(resourcePath: String): String {
        val artifactFile = EvmTestBase::class.java.getResource(resourcePath)?.readText()
        val artifactJson = GsonBuilder().create().fromJson(artifactFile, JsonObject::class.java)
        return artifactJson.get("bytecode").asString
    }
}