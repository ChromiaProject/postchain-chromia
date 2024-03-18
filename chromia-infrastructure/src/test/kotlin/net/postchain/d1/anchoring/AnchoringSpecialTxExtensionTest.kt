package net.postchain.d1.anchoring

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.SpecialTransactionPosition
import net.postchain.common.BlockchainRid
import net.postchain.core.BlockEContext
import net.postchain.crypto.CryptoSystem
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.config.BlockchainConfigProvider
import net.postchain.gtv.GtvArray
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class AnchoringSpecialTxExtensionTest() {

    lateinit var anchoringReceiverFactory: AnchoringReceiverFactory
    lateinit var anchoringSpecialTxExtension: AnchoringSpecialTxExtension
    lateinit var module: GTXModule
    lateinit var pipe: AnchoringPipe

    var mightHaveNewPackets = true

    @BeforeEach
    fun beforeEeach() {

        val blockchainRidMock = BlockchainRid.buildRepeat(0)

        pipe = mock<AnchoringPipe> {
            on { blockchainRid } doReturn blockchainRidMock
            on { mightHaveNewPackets() } doReturn mightHaveNewPackets
            on { fetchNextRange(any(), any()) } doReturn listOf(AnchoringPacket(0, "".toByteArray(), GtvEncoder.encodeGtv(gtv("header")), GtvEncoder.encodeGtv(GtvArray(arrayOf()))))
        }
        val anchoringReceiver = mock<AnchoringReceiver> {
            on { getRelevantPipes() } doReturn listOf(pipe)
        }
        module = mock<GTXModule>()
        anchoringReceiverFactory = mock<AnchoringReceiverFactory> {
            on { create(any(), any()) } doReturn anchoringReceiver
        }
        anchoringSpecialTxExtension = AnchoringSpecialTxExtension(anchoringReceiverFactory)

        anchoringSpecialTxExtension.isSigner = { true }
        anchoringSpecialTxExtension.clusterManagement = mock<ClusterManagement>()
        anchoringSpecialTxExtension.blockchainConfigProvider = mock< BlockchainConfigProvider>()
        anchoringSpecialTxExtension.anchoringConfig = AnchoringBlockchainConfigData(100, 1000, 100)

        anchoringSpecialTxExtension.createReceiver(blockchainRidMock)

        anchoringSpecialTxExtension.init(module, 0L, blockchainRidMock, mock<CryptoSystem>())

        Mockito.`when`(module.query(any(), eq("get_last_anchored_block"), any())).thenReturn(gtv(
                "block_rid" to gtv(BlockchainRid.buildRepeat(0)),
                "block_height" to gtv(0)
        ))
    }

    @Test
    fun `read until size limit is reached`() {

        anchoringSpecialTxExtension.maxTxSize = TX_SIZE_MARGIN + 200L
        mockFetchNext(1)

        val ops = anchoringSpecialTxExtension.createSpecialOperations(SpecialTransactionPosition.Begin, mock<BlockEContext>())

        // 3 packages will fit in total size (with this package content)
        assertThat(ops.size).isEqualTo(3)

        // We will fetch next 4 times before we realize the size it too large
        verify(pipe, times(4)).fetchNextRange(any(), any())
    }

    private fun mockFetchNext(nbrOfPackets: Int) {

        val packets = mutableListOf<AnchoringPacket>()
        for (i in 0 until nbrOfPackets) {
            packets.add(AnchoringPacket(0, "".toByteArray(), GtvEncoder.encodeGtv(gtv("header")), GtvEncoder.encodeGtv(GtvArray(arrayOf()))))
        }
        Mockito.`when`(pipe.fetchNextRange(any(), any())).doReturn(packets)
    }
}
