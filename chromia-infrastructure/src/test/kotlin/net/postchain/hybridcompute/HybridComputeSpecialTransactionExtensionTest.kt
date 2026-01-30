package net.postchain.hybridcompute

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import net.postchain.base.BaseBlockEContext
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtx.BroadcastContext
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant

class HybridComputeSpecialTransactionExtensionTest {

    @Test
    fun isComputeClusterTimeout() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations(), mock())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val node = cs.generateKeyPair()
        val computeClusterTimeoutSeconds = 10L
        extension.initializeBroadcastContext { }
        extension.init(module, 1, BlockchainRid.buildRepeat(1), cs)
        extension.load(
                node,
                null,
                null,
                mapOf(),
                1,
                computeClusterTimeoutSeconds,
                blockBuildingIntervalMillis = 60 * 1000,
                mock(),
                listOf(StubHybridComputeEngine()),
                listOf()
        )

        val now = Instant.now().toEpochMilli()
        assertFalse(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000, now))
        assertTrue(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000 - 1, now))
    }

    @Test
    fun `RequestTakenOp Invalid signature`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations(), mock())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val blockchainRID = BlockchainRid.buildRepeat(1)
        val bctx = mock<BlockEContext>()
        val node1 = cs.generateKeyPair()
        val node2 = cs.generateKeyPair()
        val sigMaker1 = cs.buildSigMaker(node1)
        extension.initializeBroadcastContext { }
        extension.init(module, 1, blockchainRID, cs)
        extension.load(
                node1,
                null,
                null,
                mapOf(),
                concurrency = 1,
                computeClusterTimeoutSeconds = 10,
                blockBuildingIntervalMillis = 60 * 1000,
                mock(),
                listOf(StubHybridComputeEngine()),
                listOf()
        )
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        whenever(bctx.height).thenReturn(1L)
        val signature = sigMaker1.signDigest(extension.requestTakenHash(blockchainRID, "taken"))
        assertTrue(extension.validateSpecialOperations(mock(), bctx, listOf(RequestTakenOp("taken", "test", node1.pubKey.data, signature.data).toOpData())))
        assertFailure {
            extension.validateSpecialOperations(mock(), bctx, listOf(RequestTakenOp("taken", "test", node2.pubKey.data, signature.data).toOpData()))
        }.isInstanceOf<UserMistake>().messageContains("Validate __hc.request_taken operation failed for request id [taken] of type [test]: Invalid signature")
    }

    @Test
    fun `ResponseOp validation`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations(), mock())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val blockchainRID = BlockchainRid.buildRepeat(1)
        val node1 = cs.generateKeyPair()
        val node2 = cs.generateKeyPair()
        val sigMaker1 = cs.buildSigMaker(node1)
        val sigMaker2 = cs.buildSigMaker(node2)
        val ctx = mock<EContext>()
        val bctx = BaseBlockEContext(ctx, 1, 1, 1, mapOf(), mock())
        val broadcastContext = mock<BroadcastContext>()
        extension.initializeBroadcastContext(broadcastContext)
        extension.init(module, 1, blockchainRID, cs)
        extension.load(
                node1,
                null,
                null,
                mapOf(),
                concurrency = 1,
                computeClusterTimeoutSeconds = 10,
                blockBuildingIntervalMillis = 60 * 1000,
                mock(),
                listOf(StubHybridComputeEngine()),
                listOf()
        )
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        val signature1 = sigMaker1.signDigest(extension.responseHash(blockchainRID, "success", gtv("output")))
        val signature2 = sigMaker2.signDigest(extension.responseHash(blockchainRID, "success", gtv("output")))
        val signatureBogus = sigMaker1.signDigest(extension.responseHash(blockchainRID, "success", gtv("bogus")))

        whenever(module.query(bctx, GET_REQUESTS, gtv(mapOf()))).thenReturn(
                gtv(listOf(GtvObjectMapper.toGtvDictionary(ComputeRequest("success", "test", gtv("input"), 0, ByteArray(0).wrap(), State.NEW)))))
        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("bogus")))).thenReturn(
                GtvNull)
        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("success")))).thenReturn(
                GtvObjectMapper.toGtvDictionary(ComputeRequest("success", "test", gtv("input"), 1L, node1.pubKey.wData, State.TAKEN)))
        whenever(module.query(bctx, GET_TAKEN_REQUESTS, gtv(mapOf()))).thenReturn(
                gtv(listOf(GtvObjectMapper.toGtvDictionary(ComputeRequest("success", "test", gtv("input"), 1L, node1.pubKey.wData, State.TAKEN)))))
        extension.createSpecialOperations(SpecialTransactionPosition.End, bctx)
        assertFailure {
            extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                    ResponseOp("success", "test", gtv("input"), gtv("output"), signature1.subjectID, signature1.data).toOpData()))
        }.isInstanceOf<UserMistake>().messageContains("Validation of response for id [success] of type [test] failed: local computation state is TakenComputation, expected FinishedComputation")
        bctx.blockWasCommitted()
        Thread.sleep(500)
        extension.createSpecialOperations(SpecialTransactionPosition.Begin, bctx)
        assertTrue(extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                ResponseOp("success", "test", gtv("input"), gtv("output"), signature1.subjectID, signature1.data).toOpData())))
        verify(broadcastContext).broadcast(any())
        assertFailure {
            extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                    ResponseOp("bogus", "test", gtv("input"), gtv("output"), signature1.subjectID, signature1.data).toOpData()))
        }.isInstanceOf<UserMistake>().messageContains("Validate __hc.response operation failed for request id [bogus] of type [test]: Invalid signature")
        assertFailure {
            extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                    ResponseOp("success", "test", gtv("input"), gtv("output"), signature2.subjectID, signature2.data).toOpData()))
        }.isInstanceOf<UserMistake>().messageContains("Validation of response for id [success] of type [test] failed: unexpected signer:")
        assertFailure {
            extension.validateSpecialOperations(SpecialTransactionPosition.Begin, bctx, listOf(
                    ResponseOp("success", "test", gtv("input"), gtv("bogus"), signatureBogus.subjectID, signatureBogus.data).toOpData()))
        }.isInstanceOf<UserMistake>().messageContains("Validation of response for id [success] of type [test] failed: local output does not match operation output")
    }

    @Test
    fun `FailureOp Taken request not found by id`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations(), mock())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val node1 = cs.generateKeyPair()
        val sigMaker = cs.buildSigMaker(node1)
        val blockchainRID = BlockchainRid.buildRepeat(1)
        val ctx = mock<EContext>()
        val bctx = BaseBlockEContext(ctx, 1, 1, 1, mapOf(), mock())
        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv("fail"))))).thenReturn(GtvNull)
        extension.initializeBroadcastContext { }
        extension.init(module, 1, blockchainRID, cs)
        extension.load(
                node1,
                null,
                null,
                mapOf(),
                concurrency = 1,
                computeClusterTimeoutSeconds = 10,
                blockBuildingIntervalMillis = 60 * 1000,
                mock(),
                listOf(StubHybridComputeEngine()),
                listOf()
        )
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        val signature = sigMaker.signDigest(extension.failureHash(blockchainRID, "success", "error message"))
        assertFailure {
            extension.validateSpecialOperations(mock<SpecialTransactionPosition>(), bctx, listOf(FailureOp("fail", "test", gtv("input"), "error message", signature.subjectID, signature.data).toOpData()))
        }.isInstanceOf<UserMistake>().messageContains("Validate __hc.failure operation failed for request id [fail] of type [test]: Invalid signature")
    }

    @Test
    fun `FailureOp Invalid signature`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations(), mock())
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val node1 = cs.generateKeyPair()
        val node2 = cs.generateKeyPair()
        val sigMaker2 = cs.buildSigMaker(node2)
        val blockchainRID = BlockchainRid.buildRepeat(1)
        val bctx = mock<BlockEContext>()
        extension.initializeBroadcastContext { }
        extension.init(module, 1, blockchainRID, cs)
        extension.load(
                node1,
                null,
                null,
                mapOf(),
                concurrency = 1,
                computeClusterTimeoutSeconds = 10,
                blockBuildingIntervalMillis = 60 * 1000,
                mock(),
                listOf(StubHybridComputeEngine()),
                listOf()
        )
        Awaitility.await().atMost(Duration.TEN_SECONDS).untilAsserted {
            assertThat(extension.loaded.get()).isEqualTo(1)
        }

        whenever(bctx.height).thenReturn(5L)
        val signature = sigMaker2.signDigest(extension.failureHash(blockchainRID, "fail", "error message"))

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("fail")))).thenReturn(
                GtvObjectMapper.toGtvDictionary(ComputeRequest("fail", "test", gtv("input"), 0L, node2.pubKey.wData, State.TAKEN)))
        assertTrue(extension.validateSpecialOperations(mock(), bctx, listOf(FailureOp("fail", "test", gtv("input"), "error message", node2.pubKey.data, signature.data).toOpData())))

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv("id" to gtv("fail")))).thenReturn(
                GtvObjectMapper.toGtvDictionary(ComputeRequest("fail", "test", gtv("input"), 0L, node1.pubKey.wData, State.TAKEN)))
        assertFailure {
            extension.validateSpecialOperations(mock(), bctx, listOf(FailureOp("fail", "test", gtv("input"), "error message", node2.pubKey.data, signature.data).toOpData()))
        }.isInstanceOf<UserMistake>().messageContains("Validation of failure for id [fail] of type [test] failed: unexpected signer:")
    }

    @Test
    fun `trigger block building`() {
        val clock: Clock = mock()
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations(), mock(), clock = clock)
        val module = mock<GTXModule>()
        val cs = Secp256K1CryptoSystem()
        val node = cs.generateKeyPair()
        val blockchainRID = BlockchainRid.buildRepeat(1)
        assertFalse(extension.shouldBuildBlock())
        extension.initializeBroadcastContext { }
        extension.init(module, 1, blockchainRID, cs)
        extension.load(
                node,
                null,
                null,
                mapOf(),
                concurrency = 1,
                computeClusterTimeoutSeconds = 10,
                blockBuildingIntervalMillis = 1000,
                mock(),
                listOf(StubHybridComputeEngine()),
                listOf()
        )
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
        extension.receiveBroadcast(gtv("test"))
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
