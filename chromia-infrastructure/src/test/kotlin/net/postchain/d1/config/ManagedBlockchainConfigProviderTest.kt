package net.postchain.d1.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.postchain.base.gtv.BlockHeaderData
import net.postchain.chromia.nm_api.BlockchainConfigurationInfo
import net.postchain.chromia.nm_api.NmGetPendingBlockchainConfigurationByHashResult
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.hexStringToByteArray
import net.postchain.common.hexStringToWrappedByteArray
import net.postchain.crypto.devtools.KeyPairHelper
import net.postchain.d1.cluster.ClusterManagement
import net.postchain.d1.nm_api.NodeManagement
import net.postchain.gtv.GtvFactory.gtv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class ManagedBlockchainConfigProviderTest {

    private val peer0 = KeyPairHelper.keyPair(0)
    private val peer1 = KeyPairHelper.keyPair(1)
    private val peer2 = KeyPairHelper.keyPair(2)

    @Test
    fun `get relevant peers of management chain`() {
        // Given
        val nodeManagement: NodeManagement = mock {
            on { getManagementChain() } doReturn BlockchainRid.ZERO_RID
        }
        val clusterManagement: ClusterManagement = mock {
            on { getBlockchainPeers(any(), any()) } doReturn listOf(peer0.pubKey, peer1.pubKey)
        }
        val headerData: BlockHeaderData = mock {
            on { getBlockchainRid() } doReturn BlockchainRid.ZERO_RID.data
            on { getHeight() } doReturn 0L
        }
        val sut = ManagedBlockchainConfigProvider(nodeManagement, clusterManagement)

        // When
        val result = sut.getRelevantPeers(headerData)

        // Then
        verify(clusterManagement).getBlockchainPeers(any(), eq(0L))
        assertThat(result).isEqualTo(listOf(peer0.pubKey, peer1.pubKey))
    }

    @Test
    fun `get relevant peers when there is no extra config_hash`() {
        // Given
        val nodeManagement: NodeManagement = mock {
            on { getManagementChain() } doReturn BlockchainRid.ZERO_RID
        }
        val clusterManagement: ClusterManagement = mock {
            on { getBlockchainPeers(any(), any()) } doReturn listOf(peer0.pubKey, peer1.pubKey)
        }
        val headerData: BlockHeaderData = mock {
            on { getBlockchainRid() } doReturn BlockchainRid.buildRepeat(1).data
            on { getHeight() } doReturn 0L
        }
        val sut = ManagedBlockchainConfigProvider(nodeManagement, clusterManagement)

        // When
        val result = sut.getRelevantPeers(headerData)

        // Then
        verify(clusterManagement).getBlockchainPeers(any(), eq(0L))
        assertThat(result).isEqualTo(listOf(peer0.pubKey, peer1.pubKey))
    }

    @Test
    fun `get relevant peers of pending config`() {
        // Given
        val pendingConfig: NmGetPendingBlockchainConfigurationByHashResult = mock {
            on { signers } doReturn listOf(peer1.pubKey.wData, peer2.pubKey.wData)
        }
        val nodeManagement: NodeManagement = mock {
            on { getManagementChain() } doReturn BlockchainRid.ZERO_RID
            on { getPendingBlockchainConfigByHash(any(), any()) } doReturn pendingConfig
        }
        val clusterManagement: ClusterManagement = mock {
            on { getBlockchainPeers(any(), any()) } doReturn listOf(peer0.pubKey, peer1.pubKey)
        }
        val headerData: BlockHeaderData = mock {
            on { getBlockchainRid() } doReturn BlockchainRid.buildRepeat(1).data
            on { getHeight() } doReturn 0L
            on { getExtra() } doReturn mapOf("config_hash" to gtv("00".hexStringToByteArray()))
        }
        val sut = ManagedBlockchainConfigProvider(nodeManagement, clusterManagement)

        // When
        val result = sut.getRelevantPeers(headerData)

        // Then
        verify(clusterManagement, never()).getBlockchainPeers(any(), any())
        assertThat(result).isEqualTo(listOf(peer1.pubKey, peer2.pubKey))
    }

    @Test
    fun `get relevant peers of committed config when extra config_hash and config hash match`() {
        // Given
        val bcConfig: BlockchainConfigurationInfo = mock {
            on { configHash } doReturn "00".hexStringToWrappedByteArray()
            on { signers } doReturn listOf(peer1.pubKey.wData, peer2.pubKey.wData)
        }
        val nodeManagement: NodeManagement = mock {
            on { getManagementChain() } doReturn BlockchainRid.ZERO_RID
            on { getPendingBlockchainConfigByHash(any(), any()) } doReturn null
            on { getBlockchainConfiguration(any(), any()) } doReturn bcConfig
        }
        val clusterManagement: ClusterManagement = mock {
            on { getBlockchainPeers(any(), any()) } doReturn listOf(peer0.pubKey, peer1.pubKey)
        }
        val headerData: BlockHeaderData = mock {
            on { getBlockchainRid() } doReturn BlockchainRid.buildRepeat(1).data
            on { getHeight() } doReturn 0L
            on { getExtra() } doReturn mapOf("config_hash" to gtv("00".hexStringToByteArray()))
        }
        val sut = ManagedBlockchainConfigProvider(nodeManagement, clusterManagement)

        // When
        val result = sut.getRelevantPeers(headerData)

        // Then
        verify(clusterManagement, never()).getBlockchainPeers(any(), any())
        assertThat(result).isEqualTo(listOf(peer1.pubKey, peer2.pubKey))
    }

    @Test
    fun `if extra config_hash and config hash mismatch then UserMistake will be thrown`() {
        // Given
        val bcConfig: BlockchainConfigurationInfo = mock {
            on { configHash } doReturn "00".hexStringToWrappedByteArray()
            on { signers } doReturn listOf(peer1.pubKey.wData, peer2.pubKey.wData)
        }
        val nodeManagement: NodeManagement = mock {
            on { getManagementChain() } doReturn BlockchainRid.ZERO_RID
            on { getPendingBlockchainConfigByHash(any(), any()) } doReturn null
            on { getBlockchainConfiguration(any(), any()) } doReturn bcConfig
        }
        val clusterManagement: ClusterManagement = mock {
            on { getBlockchainPeers(any(), any()) } doReturn listOf(peer0.pubKey, peer1.pubKey)
        }
        val brid = BlockchainRid.buildRepeat(1).data
        val headerData: BlockHeaderData = mock {
            on { getBlockchainRid() } doReturn brid
            on { getHeight() } doReturn 0L
            on { getExtra() } doReturn mapOf("config_hash" to gtv("11".hexStringToByteArray()))
        }
        val sut = ManagedBlockchainConfigProvider(nodeManagement, clusterManagement)

        // When
        val error = "Can't find peers for chain $brid at height 0. " +
                "Block header extra config_hash: ${"11".hexStringToWrappedByteArray()}, " +
                "expected config hash: ${"00".hexStringToWrappedByteArray()}"
        assertThrows<UserMistake>(error) {
            sut.getRelevantPeers(headerData)
        }
    }
}