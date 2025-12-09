package net.postchain.hybridcompute

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.BaseBlockEContext
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.common.wrap
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtx.GTXModule
import net.postchain.hybridcompute.rell.lib.hybridcompute.ComputeRequest
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUEST
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUESTS
import net.postchain.hybridcompute.rell.lib.hybridcompute.State
import org.awaitility.Awaitility
import org.awaitility.Duration
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant

class HybridComputeSpecialTransactionExtensionTest {

    @Test
    fun isComputeClusterTimeout() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        extension.init(module, 1, BlockchainRid.buildRepeat(1), cs)
        val computeClusterTimeoutSeconds = 10L
        extension.computeClusterTimeoutSeconds = 10
        extension.blockBuildingIntervalMillis = 60 * 1000

        val now = Instant.now().toEpochMilli()
        assertFalse(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000, now))
        assertTrue(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000 - 1, now))
    }

    @Test
    fun `RequestTakenOp Invalid signature`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        extension.init(module, 1L, BlockchainRid("C9F360FA8B35A77EF0537C133DAEE629AFAB77873BD4A8DD6E5ED8B8D996B811".hexStringToByteArray()), cs)
        extension.concurrency = 1
        extension.loadTimeoutSeconds = 3
        extension.computeTimeoutSeconds = 5
        extension.computeClusterTimeoutSeconds = 10
        extension.blockBuildingIntervalMillis = 60 * 1000
        extension.setEngines(listOf(StubHybridComputeEngine()), listOf())
        val bctx = mock<BlockEContext>()
        val node0Pubkey = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
        val node1Pubkey = "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"
        extension.load()
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        whenever(bctx.height).thenReturn(1L)
        val signatureData = "BAC412C226C0245B623D6E805130A84DEA19CBC563A9FD690F38B877B44E45FC3BFE82C6E3ACFAFAB72AEE66CD7B2B213E7B4F47DB37E0256B2AACD4F042A5C1".hexStringToByteArray()
        assertTrue(extension.validateSpecialOperations(mock(), bctx, listOf(RequestTakenOp("taken", "test", node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))
        assertFalse(extension.validateSpecialOperations(mock(), bctx, listOf(RequestTakenOp("taken", "test", node0Pubkey.hexStringToByteArray(), signatureData).toOpData())))
    }

    @Test
    fun `ResponseOp validation`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val blockchainRID = BlockchainRid.buildRepeat(1)
        extension.init(module, 1, blockchainRID, cs)
        val node1 = cs.generateKeyPair()
        val node2 = cs.generateKeyPair()
        val sigMaker1 = cs.buildSigMaker(node1)
        val sigMaker2 = cs.buildSigMaker(node2)
        extension.initSigMaker(node1.pubKey.data, node1.privKey.data)
        extension.concurrency = 1
        extension.loadTimeoutSeconds = 3
        extension.computeTimeoutSeconds = 5
        extension.computeClusterTimeoutSeconds = 10
        extension.blockBuildingIntervalMillis = 60 * 1000
        extension.setEngines(listOf(StubHybridComputeEngine()), listOf())
        val ctx = mock<EContext>()
        val bctx = BaseBlockEContext(ctx, 1, 1, 1, mapOf(), mock())
        val signature1 = sigMaker1.signDigest(extension.hash("success", blockchainRID.toHex(), bctx.height))
        val signature2 = sigMaker2.signDigest(extension.hash("success", blockchainRID.toHex(), bctx.height))
        extension.load()
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        whenever(module.query(bctx, GET_REQUESTS, gtv(mapOf()))).thenReturn(
                gtv(listOf(GtvObjectMapper.toGtvDictionary(ComputeRequest("success", "test", gtv("input"), 0, ByteArray(0).wrap(), State.NEW)))))
        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("bogus")))).thenReturn(
                GtvNull)
        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("success")))).thenReturn(
                GtvObjectMapper.toGtvDictionary(ComputeRequest("success", "test", gtv("input"), 1L, node1.pubKey.wData, State.TAKEN)))
        whenever(module.query(bctx, GET_TAKEN_REQUESTS, gtv(mapOf()))).thenReturn(
                gtv(listOf(GtvObjectMapper.toGtvDictionary(ComputeRequest("success", "test", gtv("input"), 1L, node1.pubKey.wData, State.TAKEN)))))
        extension.createSpecialOperations(SpecialTransactionPosition.End, bctx)
        assertFalse(extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                ResponseOp("success", "test", gtv("input"), gtv("output"), signature1.subjectID, signature1.data).toOpData())))
        bctx.blockWasCommitted()
        Thread.sleep(500)
        extension.createSpecialOperations(SpecialTransactionPosition.Begin, bctx)
        assertTrue(extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                ResponseOp("success", "test", gtv("input"), gtv("output"), signature1.subjectID, signature1.data).toOpData())))
        assertFalse(extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                ResponseOp("bogus", "test", gtv("input"), gtv("output"), signature1.subjectID, signature1.data).toOpData())))
        assertFalse(extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                ResponseOp("success", "test", gtv("input"), gtv("output"), signature2.subjectID, signature2.data).toOpData())))
        assertFalse(extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                ResponseOp("success", "test", gtv("input"), gtv("bogus"), signature1.subjectID, signature1.data).toOpData())))
    }

    @Test
    fun `FailureOp Taken request not found by id`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val node1 = cs.generateKeyPair()
        val sigMaker = cs.buildSigMaker(node1)
        val blockchainRID = BlockchainRid.buildRepeat(1)
        extension.init(module, 1, blockchainRID, cs)
        extension.concurrency = 1
        extension.loadTimeoutSeconds = 3
        extension.computeTimeoutSeconds = 5
        extension.computeClusterTimeoutSeconds = 10
        extension.blockBuildingIntervalMillis = 60 * 1000
        extension.setEngines(listOf(StubHybridComputeEngine()), listOf())
        val ctx = mock<EContext>()
        val bctx = BaseBlockEContext(ctx, 1, 1, 1, mapOf(), mock())
        val signature = sigMaker.signDigest(extension.hash("success", blockchainRID.toHex(), bctx.height))
        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv("fail"))))).thenReturn(GtvNull)
        extension.load()
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        assertFalse(extension.validateSpecialOperations(mock<SpecialTransactionPosition>(), bctx, listOf(FailureOp("fail", "test", gtv("input"), "error message", signature.subjectID, signature.data).toOpData())))
    }

    @Test
    fun `FailureOp Invalid signature`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        extension.init(module, 1L, BlockchainRid("C9F360FA8B35A77EF0537C133DAEE629AFAB77873BD4A8DD6E5ED8B8D996B811".hexStringToByteArray()), cs)
        extension.concurrency = 1
        extension.loadTimeoutSeconds = 3
        extension.computeTimeoutSeconds = 5
        extension.computeClusterTimeoutSeconds = 10
        extension.blockBuildingIntervalMillis = 60 * 1000
        extension.setEngines(listOf(StubHybridComputeEngine()), listOf())
        val bctx = mock<BlockEContext>()
        val node0Pubkey = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
        val node1Pubkey = "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"
        extension.load()
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        whenever(bctx.height).thenReturn(5L)
        val signatureData = "4119C4ACCD4A8BF23447CC278A712EEF4AD01CB415248E052AF476A321629EDF7D3A5CFB06D731C7E272091CE73464736C872822C7156746359DBB7C571345DF".hexStringToByteArray()

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("fail")))).thenReturn(
                GtvObjectMapper.toGtvDictionary(ComputeRequest("fail", "test", gtv("input"), 0L, node1Pubkey.hexStringToWrappedByteArray(), State.TAKEN)))
        assertTrue(extension.validateSpecialOperations(mock(), bctx, listOf(FailureOp("fail", "test", gtv("input"), "error message", node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("fail")))).thenReturn(
                GtvObjectMapper.toGtvDictionary(ComputeRequest("fail", "test", gtv("input"), 0L, node0Pubkey.hexStringToWrappedByteArray(), State.TAKEN)))
        assertFalse(extension.validateSpecialOperations(mock(), bctx, listOf(FailureOp("fail", "test", gtv("input"), "error message", node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))
    }

    @Test
    fun `trigger block building`() {
        val clock: Clock = mock()
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations(), clock = clock)
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        extension.init(module, 1L, BlockchainRid("C9F360FA8B35A77EF0537C133DAEE629AFAB77873BD4A8DD6E5ED8B8D996B811".hexStringToByteArray()), cs)
        extension.concurrency = 1
        extension.loadTimeoutSeconds = 3
        extension.computeTimeoutSeconds = 5
        extension.computeClusterTimeoutSeconds = 10
        extension.blockBuildingIntervalMillis = 1000
        extension.setEngines(listOf(StubHybridComputeEngine()), listOf())

        assertFalse(extension.shouldBuildBlock())
        extension.load()
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }
        assertFalse(extension.shouldBuildBlock())

        extension.myComputations["mine"] = FinishedComputation("type", gtv("input"), gtv("output"), isFast = false)
        assertTrue(extension.shouldBuildBlock())

        extension.myComputations.clear()
        assertFalse(extension.shouldBuildBlock())

        whenever(clock.millis()) doReturn 10000
        extension.blockCommitted(mock())
        assertFalse(extension.shouldBuildBlock())
        extension.otherNodesPendingComputations.add("others")
        whenever(clock.millis()) doReturn 10100
        assertFalse(extension.shouldBuildBlock())
        whenever(clock.millis()) doReturn 11001
        assertTrue(extension.shouldBuildBlock())
        whenever(clock.millis()) doReturn 11021
        assertTrue(extension.shouldBuildBlock())

        whenever(clock.millis()) doReturn 12000
        extension.blockCommitted(mock())
        assertFalse(extension.shouldBuildBlock())
    }
}
