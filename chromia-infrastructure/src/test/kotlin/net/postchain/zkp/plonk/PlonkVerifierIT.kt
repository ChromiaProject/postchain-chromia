package net.postchain.zkp.plonk

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.google.gson.Gson
import com.google.gson.JsonArray
import net.postchain.common.tx.TransactionStatus
import net.postchain.common.wrap
import net.postchain.concurrent.util.get
import net.postchain.devtools.IntegrationTestSetup
import net.postchain.devtools.PostchainTestNode.Companion.DEFAULT_CHAIN_IID
import net.postchain.enqueueTx
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.merkle.GtvMerkleHashCalculatorV2
import net.postchain.zkp.curve.BN128
import net.postchain.zkp.rell.lib.zkp.test.plonk_test_chain.verifyPlonkProofOperation
import org.junit.jupiter.api.Test
import java.math.BigInteger

class PlonkVerifierIT : IntegrationTestSetup() {
    val gson = Gson()
    val hashCalculator = GtvMerkleHashCalculatorV2(cryptoSystem)

    val validProofBn128 = snarkJsProofJSONToGtv(
            gson,
            BN128,
            javaClass.getResource("/net/postchain/zkp/plonk/bn128/proof.json")!!.readText()
    )

    val validPublicSignalsBn128 = gson.fromJson(
            javaClass.getResource("/net/postchain/zkp/plonk/bn128/public.json")!!.readText(),
            JsonArray::class.java
    ).map { it.asBigInteger }

    @Test
    fun `Valid PLONK proof is accepted once and only once`() {
        createNodes(1, "/infra-libs/zkp_plonk_test.xml")

        val txRid = enqueueTx(DEFAULT_CHAIN_IID, hashCalculator) {
            it.apply {
                addOperation(
                        PlonkVerifyGTXOperation.OP_NAME,
                        gtv("test_vk"),
                        validProofBn128,
                        gtv(validPublicSignalsBn128.map { gtv(it) })
                )
                verifyPlonkProofOperation(validPublicSignalsBn128)
            }
        }

        buildBlock(DEFAULT_CHAIN_IID)
        val blockQueries = nodes.first().getBlockchainInstance().blockchainEngine.getBlockQueries()

        val txIsConfirmed = blockQueries.isTransactionConfirmed(txRid).get()

        assertThat(txIsConfirmed).isTrue()
    }

    @Test
    fun `Invalid PLONK proof is rejected`() {
        createNodes(1, "/infra-libs/zkp_plonk_test.xml")

        val incorrectTxRid = enqueueTx(DEFAULT_CHAIN_IID, hashCalculator) {
            it.apply {
                addOperation(
                        PlonkVerifyGTXOperation.OP_NAME,
                        gtv("test_vk"),
                        validProofBn128,
                        gtv(listOf(gtv(BigInteger.ONE))) // This public signal does not match proof so should be rejected
                )
                verifyPlonkProofOperation(validPublicSignalsBn128)
            }
        }

        buildBlock(DEFAULT_CHAIN_IID)

        val txQueue = nodes.first().getBlockchainInstance().blockchainEngine.getTransactionQueue()

        val txStatus = txQueue.getTransactionStatus(incorrectTxRid)
        assertThat(txStatus).isEqualTo(TransactionStatus.REJECTED)
        val rejectReason = txQueue.getRejectionReason(incorrectTxRid.wrap())!!.first.message!!
        assertThat(rejectReason).contains("Pairing check failed: proof is invalid")
    }

    @Test
    fun `Duplicate PLONK proof is rejected`() {
        createNodes(1, "/infra-libs/zkp_plonk_test.xml")

        val incorrectTxRid = enqueueTx(DEFAULT_CHAIN_IID, hashCalculator) {
            it.apply {
                addOperation(
                        PlonkVerifyGTXOperation.OP_NAME,
                        gtv("test_vk"),
                        validProofBn128,
                        gtv(validPublicSignalsBn128.map { gtv(it) })
                )
                addOperation(
                        PlonkVerifyGTXOperation.OP_NAME,
                        gtv("test_vk"),
                        validProofBn128,
                        gtv(validPublicSignalsBn128.map { gtv(it) })
                )
                verifyPlonkProofOperation(validPublicSignalsBn128)
            }
        }

        buildBlock(DEFAULT_CHAIN_IID)

        val txQueue = nodes.first().getBlockchainInstance().blockchainEngine.getTransactionQueue()

        val txStatus = txQueue.getTransactionStatus(incorrectTxRid)
        assertThat(txStatus).isEqualTo(TransactionStatus.REJECTED)
        val rejectReason = txQueue.getRejectionReason(incorrectTxRid.wrap())!!.first.message!!
        assertThat(rejectReason).contains("Tx contains duplicate proofs")
    }
}
