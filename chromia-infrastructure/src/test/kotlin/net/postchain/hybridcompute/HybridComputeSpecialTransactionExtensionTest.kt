package net.postchain.hybridcompute

import net.postchain.common.BlockchainRid
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.core.BlockEContext
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXModule
import net.postchain.hybridcompute.rell.lib.hybridcompute.GET_TAKEN_REQUEST
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class HybridComputeSpecialTransactionExtensionTest {

    private val hybridComputeConfig = HybridComputeConfig(
            engine = "test-engine",
            loadTimeoutSeconds = 3,
            computeTimeoutSeconds = 5,
            computeClusterTimeoutSeconds = 10,
            concurrency = 1
    )

    @Test
    fun isComputeClusterTimeout() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        val computeClusterTimeoutSeconds = 10L
        extension.config = hybridComputeConfig
        val now = Instant.now().toEpochMilli()
        assertFalse(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000, now))
        assertTrue(extension.isComputeClusterTimeout(now - computeClusterTimeoutSeconds * 1000 - 1, now))
    }

    @Test
    fun `FailureOp Taken request not found by id`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        extension.config = hybridComputeConfig
        extension.engine = StubHybridComputeEngine()
        val bctx = mock<BlockEContext>()
        val module = mock<GTXModule>()
        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv("fail"))))).thenReturn(GtvNull)
        extension.init(module, 1L, mock(), mock())

        val result = extension.validateSpecialOperations(mock(), bctx, listOf(FailureOp("fail", "test", "error message", ByteArray(0), ByteArray(0)).toOpData()))

        assertFalse(result)
    }

    @Test
    fun `FailureOp Invalid signature`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        extension.config = hybridComputeConfig
        extension.engine = StubHybridComputeEngine()
        val bctx = mock<BlockEContext>()
        val module = mock<GTXModule>()
        val node0Pubkey = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
        val node1Pubkey = "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"
        whenever(bctx.height).thenReturn(5L)
        val cs = Secp256K1CryptoSystem()
        extension.init(module, 1L, BlockchainRid("C9F360FA8B35A77EF0537C133DAEE629AFAB77873BD4A8DD6E5ED8B8D996B811".hexStringToByteArray()), cs)
        val signatureData = "4119C4ACCD4A8BF23447CC278A712EEF4AD01CB415248E052AF476A321629EDF7D3A5CFB06D731C7E272091CE73464736C872822C7156746359DBB7C571345DF".hexStringToByteArray()

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv("fail"))))).thenReturn(gtv(Pair("id", gtv("fail")), Pair("type", gtv("test")), Pair("taken_timestamp", gtv(0L)), Pair("processed_by", gtv(node1Pubkey.hexStringToWrappedByteArray()))))
        assertTrue(extension.validateSpecialOperations(mock(), bctx, listOf(FailureOp("fail", "test", "error message", node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv("fail"))))).thenReturn(gtv(Pair("id", gtv("fail")), Pair("type", gtv("test")), Pair("taken_timestamp", gtv(0L)), Pair("processed_by", gtv(node0Pubkey.hexStringToWrappedByteArray()))))
        assertFalse(extension.validateSpecialOperations(mock(), bctx, listOf(FailureOp("fail", "test", "error message", node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))
    }

    @Test
    fun `RequestTakenOp Invalid signature`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        extension.config = hybridComputeConfig
        extension.engine = StubHybridComputeEngine()
        val bctx = mock<BlockEContext>()
        val node0Pubkey = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
        val node1Pubkey = "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"
        whenever(bctx.height).thenReturn(1L)
        val cs = Secp256K1CryptoSystem()
        extension.init(mock(), 1L, BlockchainRid("C9F360FA8B35A77EF0537C133DAEE629AFAB77873BD4A8DD6E5ED8B8D996B811".hexStringToByteArray()), cs)
        val signatureData = "BAC412C226C0245B623D6E805130A84DEA19CBC563A9FD690F38B877B44E45FC3BFE82C6E3ACFAFAB72AEE66CD7B2B213E7B4F47DB37E0256B2AACD4F042A5C1".hexStringToByteArray()

        assertTrue(extension.validateSpecialOperations(mock(), bctx, listOf(RequestTakenOp("taken", node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))
        assertFalse(extension.validateSpecialOperations(mock(), bctx, listOf(RequestTakenOp("taken", node0Pubkey.hexStringToByteArray(), signatureData).toOpData())))
    }

    @Test
    fun `ResponseOp Invalid signature`() {
        val extension = HybridComputeSpecialTransactionExtension(MockDatabaseOperations())
        extension.config = hybridComputeConfig
        extension.engine = StubHybridComputeEngine()
        extension.load()
        val bctx = mock<BlockEContext>()
        val module = mock<GTXModule>()
        val node0Pubkey = "03A301697BDFCD704313BA48E51D567543F2A182031EFD6915DDC07BBCC4E16070"
        val node1Pubkey = "031B84C5567B126440995D3ED5AABA0565D71E1834604819FF9C17F5E9D5DD078F"
        whenever(bctx.height).thenReturn(5L)
        val cs = Secp256K1CryptoSystem()
        extension.init(module, 1L, BlockchainRid("C9F360FA8B35A77EF0537C133DAEE629AFAB77873BD4A8DD6E5ED8B8D996B811".hexStringToByteArray()), cs)
        val signatureData = "361E7D274BB51929052C60F2DC80815B9761A0839C0FE06EE31A2B407AFBE3E428517814EDD8EA3B6DF17606E9E1682DD0EDEE18F1DED3FF00435BD95872275B".hexStringToByteArray()

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv("success"))))).thenReturn(gtv(Pair("id", gtv("success")), Pair("type", gtv("test")), Pair("taken_timestamp", gtv(0L)), Pair("processed_by", gtv(node1Pubkey.hexStringToWrappedByteArray()))))
        assertTrue(extension.validateSpecialOperations(mock(), bctx, listOf(ResponseOp("success", "test", gtv("success"), node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))

        whenever(module.query(bctx, GET_TAKEN_REQUEST, gtv(Pair("id", gtv("success"))))).thenReturn(gtv(Pair("id", gtv("success")), Pair("type", gtv("test")), Pair("taken_timestamp", gtv(0L)), Pair("processed_by", gtv(node0Pubkey.hexStringToWrappedByteArray()))))
        assertFalse(extension.validateSpecialOperations(mock(), bctx, listOf(ResponseOp("success", "test", gtv("success"), node1Pubkey.hexStringToByteArray(), signatureData).toOpData())))
    }
}
