package net.postchain.snapshot

import net.postchain.StorageBuilder
import net.postchain.StorageInitializer
import net.postchain.api.internal.PeerApi.findPeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.base.snapshot.RootSnapshotBlockBuilderExtension
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.toHex
import net.postchain.concurrent.util.get
import net.postchain.crypto.KeyPair
import net.postchain.devtools.PostchainTestNode
import net.postchain.devtools.addBlockchainAndStart
import net.postchain.devtools.snapshot.SnapshotTestBase
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.GtvEncoder
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtv.GtvNull
import net.postchain.gtx.GTXModuleAware
import net.postchain.gtx.GTXTransactionFactory
import net.postchain.gtx.GtxBuilder
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import kotlin.collections.plus

class ForceEnableSnapshot : SnapshotTestBase() {

    @Test
    fun buildSnapshotBlockAndReplicate() {

        System.setProperty("FORCE_SNAPSHOT", "true")
        // Devnet1 providers
        val providerKeys = listOf(
                KeyPair.of("03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05", "BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114"),
                null,
                KeyPair.of("03D01591E5466B07AC1D1F77BEBE2164AB0BA31366FBF005907F28FD144D64B871", "AD329F5C4E4DDF226D1A4948D7A2CCB34E76F64D4972B934FDBBDBEF4CA7B905"),
                KeyPair.of("02B6F2967CF9AFC4D289EF475A2C2DDEC9EAB79AC60C1C99683E3134074619E635", "2C3ED78A578575FD9E67996164A6B281C8AEE29D9AAEE9900088749E33C99150"),
        )
        // Devnet1 nodes
        val dcNodeKeys = listOf(
                KeyPair.of("0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57", "3132333435363738393031323334353637383930313233343536373839303131"),
                KeyPair.of("035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9", "3132333435363738393031323334353637383930313233343536373839303132"),
                KeyPair.of("03f811d3e806e6d093a4bcce49c145ba78f9a4b2fbd167753ecab2a13530b081f8", "3132333435363738393031323334353637383930313233343536373839303133"),
                KeyPair.of("03ef3f5be98d499b048ba28b247036b611a1ced7fcf87c17c8b5ca3b3ce1ee23a4", "3132333435363738393031323334353637383930313233343536373839303134"),
        )
        // This node will use generated keys
        val nodeKeys = listOf(cryptoSystem.generateKeyPair())

        val nodes = nodeKeys.mapIndexed { index, keyPair ->
            val appConfig = createAppConfig(mapOf(
                    "database.schema" to "snapshot_replica_node${index}",
                    "messaging.port" to 9870 + index,
                    "messaging.privkey" to keyPair.privKey.hex(),
                    "messaging.pubkey" to keyPair.pubKey.hex(),
            ))
            StorageBuilder.buildStorage(appConfig).use { storage ->
                withWriteConnection(storage, 0) { ctx ->
                    val dba = DatabaseAccess.of(ctx)
                    dba.apply {
                        val height = getLastBlockHeight(ctx)
                        val configBytes = getConfigurationDataForHeight(ctx, height)!!
                        val config = GtvDecoder.decodeGtv(configBytes)
                            .modify(listOf("signers")) { _ ->
                                gtv(gtv(nodeKeys[0].pubKey.data))
                            }
                            .modify(listOf("blockstrategy")) { configEntry ->
                                gtv(configEntry.asDict() + mapOf("maxblocktime" to gtv(5000)))
                            }
                            .modify(listOf("snapshot")) {
                                gtv("interval" to gtv(1))
                            }
                            .modify(listOf("features")) { configEntry ->
                                gtv(configEntry.asDict() + mapOf("snapshot_enabled" to gtv(true)))
                            }
                        val updatedConfigBytes = GtvEncoder.encodeGtv(config)
                        if (getAllConfigurations(ctx).none { (_, existingConfig) ->
                                    existingConfig!!.data.contentEquals(updatedConfigBytes)
                                }) {
                            addConfigurationData(ctx, height + 1, updatedConfigBytes)
                        }
                    }

                    // Remove devnet1 node references
                    ctx.conn.createStatement().use { stmt -> stmt.executeUpdate("update \"c0.node\" set host = 'disabled' where host <> 'localhost'") }
                    ctx.conn.createStatement().use { stmt -> stmt.executeUpdate("delete from peerinfos") }

                    if (findPeerInfo(ctx, null, null, keyPair.pubKey.hex()).isEmpty()) {
                        dba.addPeerInfo( ctx,"localhost", 9870 + index, keyPair.pubKey.hex())
                    }

                    true
                }
            }

            PostchainTestNode(appConfig, false)
        }

        nodes.forEach {
            it.startBlockchain(0)
        }

        val blockchainRid = nodes[0].getBlockchainInstance(0).blockchainEngine.blockchainRid
        val configuration = nodes[0].getBlockchainInstance(0).blockchainEngine.getConfiguration()
        val module = (configuration as GTXModuleAware).module

        val isRunning = nodes[0].getBlockchainInstance(0).isProcessRunning()
        val initialHeight = nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getLastBlockHeight().get()
        println("Running: $isRunning, height: $initialHeight")

        var snapshotHeight: Long? = null
        var replicaNode: PostchainTestNode? = null
        var opHeight = Long.MAX_VALUE
        var opTxRid: ByteArray? = null
        var opConfirmed = false
        val replicaHeightThroughputStack = mutableMapOf<Long, Long>()

        while (true) {
            val height = nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getLastBlockHeight().get()
            val lastSnapshotHeight = nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getLatestSnapshotHeight().get()
            val replicaHeight = replicaNode?.getBlockchainInstance(0)?.blockchainEngine?.getBlockQueries()?.getLastBlockHeight()?.get()
            if (replicaHeight != null) {
                while (replicaHeightThroughputStack.size > 10) {
                    replicaHeightThroughputStack.remove(replicaHeightThroughputStack.keys.min())
                }
                replicaHeightThroughputStack[System.currentTimeMillis()] = replicaHeight
            }
            val replicaThroughput = replicaHeightThroughputStack.let {
                if (it.isEmpty()) "?" else {
                    val seconds = 1000L.coerceAtLeast((it.keys.max() - it.keys.min())) / 1000
                    val blocks = it.values.max() - it.values.min()
                    if (blocks > 0) {
                        "${blocks / seconds} blocks/s"
                    } else "?"
                }
            }
            val nodeSigners = nodes.joinToString(", ") {
                "${it.pubKey.substring(0, 6)}: ${it.retrieveBlockchain(0)?.isSigner() ?: "?"}"
            }
            println("height: $height  snapshot: $lastSnapshotHeight   signers: $nodeSigners  replica height: ${replicaHeight ?: "-"} ($replicaThroughput)")

            if (height == initialHeight + 1 && snapshotHeight == null) {
                snapshotHeight = height + 2
                println("Enabling snapshot root from $snapshotHeight")
                RootSnapshotBlockBuilderExtension.includeRootSnapshotFromHeight.set(snapshotHeight)
            } else if (snapshotHeight != null && height >= snapshotHeight && opHeight == Long.MAX_VALUE) {

                println("Add icmf op on $height")

                val gtxBuilder = GtxBuilder(blockchainRid, listOf(
                        providerKeys[0]!!.pubKey.data,
                ), cryptoSystem, configuration.merkleHashCalculator)

                val update_node_data = gtv(gtv(dcNodeKeys[0].pubKey.data),
                        GtvNull, GtvNull, GtvNull, GtvNull, gtv("SE"), GtvNull)

                gtxBuilder.addOperation("update_node_with_node_data", gtv(providerKeys[0]!!.pubKey.data), update_node_data)
                val tx = GTXTransactionFactory(blockchainRid, module, cryptoSystem, configuration.merkleHashCalculator)
                        .build(gtxBuilder.finish()
                                .sign(cryptoSystem.buildSigMaker(providerKeys[0]!!))
                                .buildGtx())
                opTxRid = tx.getRID()
                nodes[0].transactionQueue(0).enqueue(tx)

                opHeight = height
            } else if (opTxRid != null && !opConfirmed) {
                println("Awaits op confirmation")

                nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getTransaction(opTxRid).get()?.apply {
                    opConfirmed = true
                }
            } else if (replicaNode == null && opConfirmed) {
                println("Enabling replica node")

                val appConfig = createAppConfig(mapOf(
                        "database.schema" to "snapshot_replica_noder",
                        "messaging.port" to 0,
                        "messaging.privkey" to "872D0F412BDD00C01CE5C237360E7B67024F9FF250D96ED3154589E1DDDE9BA1",
                        "messaging.pubkey" to "0327F6EAE0B4A10B55051734179A0A5C5C3C4FA05E728594607E2B92096E29B405",
                        "initial-peer.pubkey" to nodeKeys[0].pubKey.data.toHex(),
                        "initial-peer.host" to "localhost",
                        "initial-peer.port" to "9870",
                        "fastsync.parallelism" to 200, // Speed up fastsync
                ))
                replicaNode = PostchainTestNode(appConfig, true)

                val node0Configs = withReadConnection(nodes[0].postchainContext.blockBuilderStorage, 0) { ctx ->
                    DatabaseAccess.of(ctx).getAllConfigurations(ctx)
                }

                runStorageCommand(appConfig) {
                    StorageInitializer.setupInitialPeers(appConfig, it)
                    nodeKeys.forEachIndexed { index, keyPair ->
                        DatabaseAccess.of(it).apply {
                            if (findPeerInfo(it, null, null, keyPair.pubKey.hex()).isEmpty()) {
                                addPeerInfo(it, "localhost", 9870 + index, keyPair.pubKey.hex())
                            }
                        }
                    }
                }
                replicaNode.addBlockchainAndStart(0, GtvDecoder.decodeGtv(node0Configs[0].second!!.data))

                withWriteConnection(replicaNode.postchainContext.blockBuilderStorage, 0) { dctx ->
                    DatabaseAccess.of(dctx).apply {
                        node0Configs.forEach { (height, config) ->
                            addConfigurationData(dctx, height, config!!.data)
                        }
                    }
                    true
                }
            }

            sleep(1000)
        }
    }
}


fun Gtv.modify(dictPath: List<String>, modifier: (Gtv) -> Gtv): Gtv {
    return if (dictPath.isEmpty()) {
        modifier(this)
    } else if (!this.asDict().containsKey(dictPath[0])) {
        gtv(this.asDict() + mapOf(dictPath[0] to modifier(gtv(emptyMap()))))
    } else {
        gtv(this.asDict().mapValues { dictEntry ->
            if (dictEntry.key == dictPath[0]) {
                dictEntry.value.modify(dictPath.drop(1), modifier)
            } else {
                dictEntry.value
            }
        })
    }
}


