package net.postchain.snapshot

import net.postchain.StorageBuilder
import net.postchain.StorageInitializer
import net.postchain.api.internal.PeerApi.findPeerInfo
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.runStorageCommand
import net.postchain.base.snapshot.RootSnapshotBlockBuilderExtension
import net.postchain.base.withReadConnection
import net.postchain.base.withWriteConnection
import net.postchain.common.exception.ProgrammerMistake
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
import org.junit.jupiter.api.fail
import java.lang.Thread.sleep

/**
 * Manual integration test that force-enables snapshot on an existing real-world chain
 * (devnet1 directory chain) and verifies that a brand-new replica node can sync its state
 * via snapshot sync rather than replaying every block from genesis.
 *
 * ## Background
 * Snapshot sync lets a new node download a compact summary of chain state (the Merkle-tree
 * "snapshot") at a known height instead of replaying all historical blocks. This is crucial
 * for chains that already have hundreds of thousands of blocks.
 *
 * The challenge tested here is retrofitting snapshot support onto a chain that was running
 * *before* snapshot was enabled: the chain has no snapshot root hashes in any of its
 * historical block headers, so we need to:
 *   1. Replay the chain locally with `FORCE_SNAPSHOT=true` to rebuild snapshot data from
 *      raw block history (done by the preceding `snapshot-replica.sh` step).
 *   2. Patch the chain config to enable snapshot going forward and build at least one new
 *      block that *does* include a snapshot root hash in its header.
 *   3. Start a fresh replica and confirm it syncs state via snapshot sync, not full replay.
 *
 * ## Prerequisites
 * Before running this test you must:
 *   - Have a local PostgreSQL instance running at localhost:5432 (user/pass: postchain/postchain).
 *   - Restore the pre-built snapshot dump into schema `snapshot_replica_node0`:
 *       `./db.sh restore dc_dump.sql snapshot_replica_node0`
 *     (See `doc/snapshot-testing/force-snapshot.md` for the full workflow.)
 *
 * ## Test workflow (happens inside the main loop)
 *   1. **DB surgery** – Patch the existing chain config to replace devnet1 signers with a
 *      locally-generated key and enable snapshot features.  Clear external peer records so
 *      the node only talks to itself.
 *   2. **Node start** – Boot the validator node.  It resumes building blocks from the
 *      restored height as the sole signer.
 *   3. **Enable snapshot root** – After the first new block confirms the node is live, flip
 *      `RootSnapshotBlockBuilderExtension.includeRootSnapshotFromHeight` so that upcoming
 *      blocks embed the Merkle root of snapshot state in their headers.
 *   4. **Inject state change** – Submit an `update_node_with_node_data` DC operation to
 *      mutate some Rell entity state and trigger an ICMF message emission, giving the
 *      snapshot something meaningful to capture.
 *   5. **Start replica** – Once the state-change tx is confirmed, spin up a second node
 *      pointing at the validator. Its `snapshotsync.threshold` is set low enough that it
 *      will prefer snapshot sync over full block replay.
 *   6. **Wait for sync completion** – Poll until the replica reaches the latest snapshot
 *      height, then tear down and return.
 *
 * After the test exits, run [SnapshotDbCompare.verifyLeafsAndICMF] to assert that the
 * replica's database matches the original node's.
 */
class ForceEnableSnapshot : SnapshotTestBase() {

    @Test
    fun buildSnapshotBlockAndReplicate() {

        // FORCE_SNAPSHOT instructs RootSnapshotBlockBuilderExtension to build snapshot
        // Merkle-tree data from existing block history even without snapshot root hashes in
        // the historical block headers. Without this flag the node would skip snapshot
        // computation for pre-snapshot blocks.
        System.setProperty("FORCE_SNAPSHOT", "true")

        // ---------------------------------------------------------------------------------
        // Devnet1 identity material
        //
        // These keys are the real devnet1 provider and directory-chain node identities.
        // They are needed to:
        //   - Sign the `update_node_with_node_data` DC operation (requires a provider key).
        //   - Reference a specific DC node entry whose metadata we will update.
        // Note: providerKeys[1] is null because that provider's private key is unavailable;
        // the test only needs provider[0] to sign the operation.
        // ---------------------------------------------------------------------------------

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

        // Fresh ephemeral keypair for the local validator node.  We replace devnet1's
        // multi-signer quorum with this single key so the node can build blocks alone.
        // Note: nodeKeys contains only one element - the single local validator node key.
        val nodeKeys = listOf(cryptoSystem.generateKeyPair())

        // ---------------------------------------------------------------------------------
        // Phase 1: DB surgery – patch the existing chain config and peer table
        //
        // This block opens the pre-restored database, reads the latest chain configuration,
        // and writes a new configuration entry effective at (lastHeight + 1) that:
        //   - Replaces the original devnet1 signers with just our local node key, allowing
        //     this single node to build consensus blocks unilaterally.
        //   - Sets maxblocktime to 5000 ms so the node builds blocks at a reasonable pace.
        //   - Sets snapshot.interval = 1 so a snapshot is computed on every block.
        //   - Sets features.snapshot_enabled = true to activate the snapshot subsystem.
        // It also purges devnet1 peer entries from peerinfos and the c0.node table so the
        // node does not attempt to reach unreachable remote hosts, and registers itself.
        //
        // Note: nodeKeys contains only one element - the single local validator node key.
        //
        // ---------------------------------------------------------------------------------
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
                        // Deep-patch the GTV config dict without losing unrelated keys.
                        val config = GtvDecoder.decodeGtv(configBytes)
                                .modify(listOf("signers")) { _ ->
                                    // Single signer: our ephemeral local key.
                                    gtv(gtv(nodeKeys[0].pubKey.data))
                                }
                                .modify(listOf("blockstrategy")) { configEntry ->
                                    // Keep all existing blockstrategy keys; add/override maxblocktime.
                                    gtv(configEntry.asDict() + mapOf("maxblocktime" to gtv(5000)))
                                }
                                .modify(listOf("snapshot")) {
                                    // Build a snapshot on every single block (interval = 1).
                                    gtv("interval" to gtv(1))
                                }
                                .modify(listOf("features")) { configEntry ->
                                    // Enable the snapshot feature flag alongside any existing ones.
                                    gtv(configEntry.asDict() + mapOf("snapshot_enabled" to gtv(true)))
                                }
                        val updatedConfigBytes = GtvEncoder.encodeGtv(config)
                        // Guard against re-running the setup block: only insert the patched
                        // config if it is not already present in the configurations table.
                        if (
                                getAllConfigurations(ctx).none { (_, existingConfig) ->
                                    existingConfig!!.data.contentEquals(updatedConfigBytes)
                                }
                        ) {
                            addConfigurationData(ctx, height + 1, updatedConfigBytes)
                        } else {
                            fail("Config already exists")
                        }
                    }

                    // Remove devnet1 node references from the Rell `node` entity so the
                    // local node doesn't try (and fail) to connect to remote devnet1 hosts.
                    ctx.conn.createStatement().use { stmt -> stmt.executeUpdate("update \"c0.node\" set host = 'disabled' where host <> 'localhost'") }
                    // Clear all peer infos inherited from the dump; we will add only our node.
                    ctx.conn.createStatement().use { stmt -> stmt.executeUpdate("delete from peerinfos") }

                    // Register the local node as the sole peer so it can serve block data to
                    // the replica node that will be started later.
                    if (findPeerInfo(ctx, null, null, keyPair.pubKey.hex()).isEmpty()) {
                        dba.addPeerInfo(ctx, "localhost", 9870 + index, keyPair.pubKey.hex())
                    }

                    true
                }
            }

            PostchainTestNode(appConfig, false)
        }

        // ---------------------------------------------------------------------------------
        // Phase 2: Start the validator node
        // The node resumes block production from the restored height as sole signer.
        // Note: `nodes` contains only one element - the single local validator node key.
        // ---------------------------------------------------------------------------------
        nodes.forEach {
            it.startBlockchain(0)
        }

        val blockchainRid = nodes[0].getBlockchainInstance(0).blockchainEngine.blockchainRid
        val configuration = nodes[0].getBlockchainInstance(0).blockchainEngine.getConfiguration()
        // We need the GTX module to construct and validate transactions below.
        val module = (configuration as GTXModuleAware).module

        val isRunning = nodes[0].getBlockchainInstance(0).isProcessRunning()
        val initialHeight = nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getLastBlockHeight().get()
        println("Running: $isRunning, height: $initialHeight")

        // State variables driving the main monitoring loop.
        // Each phase gate is a nullable/flag that transitions exactly once.
        var snapshotHeight: Long? = null       // Height from which snapshot roots are included
        var replicaNode: PostchainTestNode? = null  // Created once the state-change op is confirmed
        var opHeight = Long.MAX_VALUE          // Height at which the DC op was submitted
        var opTxRid: ByteArray? = null         // RID of the submitted update_node_with_node_data tx
        var opConfirmed = false                // True once the tx appears in a confirmed block
        // Sliding window of (timestamp → replicaHeight) samples used to compute throughput.
        val replicaHeightThroughputStack = mutableMapOf<Long, Long>()

        // ---------------------------------------------------------------------------------
        // Main monitoring loop – polls every second and transitions through phases:
        //   A → Enable snapshot root header inclusion (first new block)
        //   B → Submit DC state-change operation (once snapshot root is active)
        //   C → Wait for op confirmation
        //   D → Start replica node (once op is confirmed)
        //   E → Wait for replica to reach snapshot height, then exit
        // ---------------------------------------------------------------------------------
        while (true) {
            val height = nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getLastBlockHeight().get()
            val lastSnapshotHeight = nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getLatestSnapshotHeight().get()
                    ?: throw ProgrammerMistake("Snapshot height is null")
            val replicaHeight = replicaNode?.getBlockchainInstance(0)?.blockchainEngine?.getBlockQueries()?.getLastBlockHeight()?.get()

            // ---- Phase E: Replica has caught up – clean up and exit ----
            if (replicaHeight != null) {
                if (replicaHeight >= lastSnapshotHeight) {
                    // Before stopping, apply the same devnet1-peer cleanup to the replica DB
                    // so that its state is comparable to the validator for SnapshotDbCompare.
                    replicaNode.postchainContext.blockBuilderStorage.withWriteConnection { ctx ->
                        // Remove devnet1 node references so DB state matches original node
                        ctx.conn.createStatement().use { stmt -> stmt.executeUpdate("update \"c0.node\" set host = 'disabled' where host <> 'localhost'") }
                        ctx.conn.createStatement().use { stmt -> stmt.executeUpdate("delete from peerinfos") }
                    }
                    println("Snapshot is synced, stopping nodes...")
                    replicaNode.shutdown()
                    return
                }

                // Keep at most 10 samples in the sliding window.
                while (replicaHeightThroughputStack.size > 10) {
                    replicaHeightThroughputStack.remove(replicaHeightThroughputStack.keys.min())
                }
                replicaHeightThroughputStack[System.currentTimeMillis()] = replicaHeight
            }

            // Compute approximate replica block sync throughput from the sliding window.
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
                // Short pubkey prefix for readability; isSigner() confirms consensus role.
                "${it.pubKey.substring(0, 6)}: ${it.retrieveBlockchain(0)?.isSigner() ?: "?"}"
            }
            // Status line printed every second: validator height, latest snapshot height,
            // signer status, replica sync progress and throughput.
            println("height: $height  snapshot: $lastSnapshotHeight   signers: $nodeSigners  replica height: ${replicaHeight ?: "-"} ($replicaThroughput)")

            // ---- Phase A: First new block confirms the node is live ----
            // We wait for height initialHeight+1 (the first block built under the new config)
            // before enabling snapshot root inclusion, giving the config activation a chance
            // to settle. Snapshot roots start appearing two blocks later (+2) to avoid a
            // race where the config change and the first snapshot root land on the same block.
            if (height == initialHeight + 1 && snapshotHeight == null) {
                snapshotHeight = height + 2
                println("Enabling snapshot root from $snapshotHeight")
                // This static flag tells RootSnapshotBlockBuilderExtension to start embedding
                // the Merkle root of snapshot state in block headers from this height onward.
                RootSnapshotBlockBuilderExtension.includeRootSnapshotFromHeight.set(snapshotHeight)

                // ---- Phase B: Submit state-change transaction ----
                // We wait until we are at or past snapshotHeight before submitting so that at
                // least one snapshot-rooted block exists before we mutate state.
            } else if (snapshotHeight != null && height >= snapshotHeight && opHeight == Long.MAX_VALUE) {

                println("Add icmf op on $height")

                // Build an `update_node_with_node_data` DC operation signed by provider[0].
                // This operation updates node[0]'s country code to "SE", which:
                //   a) Mutates a Rell entity (dc node) that will appear in the snapshot.
                //   b) Triggers an ICMF message to be emitted, giving the ICMF sender tables
                //      something to verify in SnapshotDbCompare.
                // The GTV argument layout matches the DC operation signature:
                //   update_node_with_node_data(provider_pubkey, node_data)
                // where node_data = (node_pubkey, null, null, null, null, country_code, null).
                val gtxBuilder = GtxBuilder(blockchainRid, listOf(
                        providerKeys[0]!!.pubKey.data,
                ), cryptoSystem, configuration.merkleHashCalculator)

                val update_node_data = gtv(gtv(dcNodeKeys[0].pubKey.data),
                        GtvNull, GtvNull, GtvNull, GtvNull, gtv("SE"), GtvNull)

                gtxBuilder.addOperation("update_node_with_node_data", gtv(providerKeys[0]!!.pubKey.data), update_node_data)
                val tx = GTXTransactionFactory(blockchainRid, module, cryptoSystem, configuration.merkleHashCalculator)
                        .build(
                                gtxBuilder.finish()
                                        .sign(cryptoSystem.buildSigMaker(providerKeys[0]!!))
                                        .buildGtx()
                        )
                opTxRid = tx.getRID()
                nodes[0].transactionQueue(0).enqueue(tx)

                opHeight = height

                // ---- Phase C: Wait for the DC operation to be included in a block ----
            } else if (opTxRid != null && !opConfirmed) {
                println("Awaits op confirmation")

                nodes[0].getBlockchainInstance(0).blockchainEngine.getBlockQueries().getTransaction(opTxRid).get()?.apply {
                    opConfirmed = true
                }

                // ---- Phase D: Start the replica node ----
                // The replica starts with an empty database and must discover and sync the chain
                // from the validator node via snapshot sync + fastsync.
            } else if (replicaNode == null && opConfirmed) {
                println("Enabling replica node")

                // The replica uses a fixed hardcoded keypair (not devnet1) purely for local
                // P2P messaging; its identity does not matter for consensus correctness.
                // key note: snapshotsync.threshold = 5000 means the node will attempt snapshot
                // sync only if it is more than 5000 blocks behind the latest snapshot height.
                // On devnet1 (~450k blocks) this is trivially satisfied.  fastsync.parallelism
                // is cranked up to 200 to speed up block download during fastsync.
                val appConfig = createAppConfig(mapOf(
                        "database.schema" to "snapshot_replica_noder",
                        "messaging.port" to 0,
                        "messaging.privkey" to "872D0F412BDD00C01CE5C237360E7B67024F9FF250D96ED3154589E1DDDE9BA1",
                        "messaging.pubkey" to "0327F6EAE0B4A10B55051734179A0A5C5C3C4FA05E728594607E2B92096E29B405",
                        // Bootstrap peer: the validator node listening at localhost:9870.
                        "initial-peer.pubkey" to nodeKeys[0].pubKey.data.toHex(),
                        "initial-peer.host" to "localhost",
                        "initial-peer.port" to "9870",
                        "fastsync.parallelism" to 200, // Speed up fastsync
                        "snapshotsync.threshold" to 5000
                ))
                replicaNode = PostchainTestNode(appConfig, true)

                // Read all blockchain configurations from the validator so we can seed the
                // replica's configuration table before it starts.  This is necessary because
                // the replica's DB is empty and the managed configuration provider requires
                // at least the genesis config to bootstrap.
                val node0Configs = withReadConnection(nodes[0].postchainContext.blockBuilderStorage, 0) { ctx ->
                    DatabaseAccess.of(ctx).getAllConfigurations(ctx)
                }

                // Write the validator's peer info into the replica's peer table so P2P
                // connections can be established.
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
                // Start the replica blockchain using the genesis config (index 0).
                // It will discover additional config updates from the validator via fastsync.
                replicaNode.addBlockchainAndStart(0, GtvDecoder.decodeGtv(node0Configs[0].second!!.data))

                // Pre-populate the replica's configurations table with all known configs so
                // it can apply configuration changes at the right heights without needing to
                // wait for each one to arrive over the network.
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


/**
 * Recursively modifies a GTV dict by navigating [dictPath] and applying [modifier] at the
 * leaf entry.  Creates intermediate dict entries if they do not yet exist.
 *
 * This is used to surgically patch individual keys inside a deeply-nested blockchain
 * configuration GTV without needing to reconstruct the entire structure manually.
 *
 * @param dictPath A list of dict keys forming the navigation path, e.g.
 *   `listOf("blockstrategy")` to modify the top-level "blockstrategy" dict entry.
 * @param modifier Transformation applied to the existing GTV at the leaf.  Receives the
 *   current value (or an empty dict GTV if the key is absent) and returns the replacement.
 */
fun Gtv.modify(dictPath: List<String>, modifier: (Gtv) -> Gtv): Gtv {
    return if (dictPath.isEmpty()) {
        modifier(this)
    } else if (!this.asDict().containsKey(dictPath[0])) {
        // Key is absent – create it with an empty dict and apply the modifier.
        gtv(this.asDict() + mapOf(dictPath[0] to modifier(gtv(emptyMap()))))
    } else {
        // Key exists – recurse into it and leave all sibling keys untouched.
        gtv(this.asDict().mapValues { dictEntry ->
            if (dictEntry.key == dictPath[0]) {
                dictEntry.value.modify(dictPath.drop(1), modifier)
            } else {
                dictEntry.value
            }
        })
    }
}

