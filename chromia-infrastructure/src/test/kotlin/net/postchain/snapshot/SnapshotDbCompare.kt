package net.postchain.snapshot

import assertk.assertThat
import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.d1.icmf.IcmfReceiverDatabaseOperationsImpl
import net.postchain.d1.icmf.IcmfSenderDatabaseOperationsImpl
import org.junit.jupiter.api.Test

/**
 * Manual verification test run *after* [ForceEnableSnapshot.buildSnapshotBlockAndReplicate]
 * to assert that the replica node's database is semantically equivalent to the original
 * validator node's database.
 *
 * ## What is compared
 * "Equivalent" does not mean byte-for-byte identical – some metadata columns (e.g. internal
 * page sequence IDs, block heights in accumulated leaf tables) will legitimately differ
 * because the replica built its state via snapshot sync rather than full block replay.
 * The comparisons below target the *meaningful* state: the latest snapshot leaf values,
 * the Merkle tree root pages, and the ICMF messaging tables.
 *
 * ## Database schemas
 *   - **Expected** (source of truth): `snapshot_replica_node0`
 *     The validator node that ran [ForceEnableSnapshot], originally restored from the
 *     pre-built `dc_dump.sql` dump.
 *   - **Actual** (under test): `snapshot_replica_noder`
 *     The replica node that synced state via snapshot sync.
 *
 * ## Running this test
 * Run immediately after [ForceEnableSnapshot] finishes.  Both database schemas must be
 * present in the local PostgreSQL instance at localhost:5432.
 */
class SnapshotDbCompare {

    @Test
    fun verifyLeafsAndICMF() {
        val expectedConfig = createAppConfig(mapOf(
                // Validator node schema – the reference / source of truth.
                "database.schema" to "snapshot_replica_node0"
        ))
        val actualConfig = createAppConfig(mapOf(
                // Replica node schema – synced via snapshot sync, being verified.
                "database.schema" to "snapshot_replica_noder"
        ))

        StorageBuilder.buildStorage(expectedConfig).use { expectedStorage ->
            StorageBuilder.buildStorage(actualConfig).use { actualStorage ->

                withReadConnection(expectedStorage, 0) { expectedContext ->
                    withReadConnection(actualStorage, 0) { actualContext ->

                        // Fetch the list of registered snapshot module context IDs from the
                        // expected (validator) node.  Each context corresponds to one GTX
                        // module that participates in snapshot state.
                        val expectedDA = DatabaseAccess.of(expectedContext)
                        val expectedContextIds = expectedDA.getSnapshotModuleContextIds(expectedContext)

                        // ----------------------------------------------------------------
                        // 1. Snapshot context registry
                        // The sys.snapshot_contexts table records which modules have opted
                        // into snapshot state tracking.  Both nodes must have the exact
                        // same set of registered contexts.
                        // ----------------------------------------------------------------
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicTablesContentProvider(listOf("sys.snapshot_contexts")))

                        // ----------------------------------------------------------------
                        // 2. Module state leafs (latest value per state slot)
                        // Each snapshot context has its own state_leafs table:
                        //   sys.x.gtx_module_<contextId>_state_leafs
                        // Rows accumulate over time (one row per (state_n, block_height)
                        // pair), so we compare only the *latest* leaf value for each
                        // state_n, ignoring block_height entirely.  The replica may have
                        // a different block_height for the same leaf value because it
                        // restored via snapshot rather than replaying block-by-block.
                        // ----------------------------------------------------------------
                        expectedContextIds.forEach { contextId ->
                            assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                    basicSQLContentProvider { ctx ->
                                        val table = DatabaseAccess.of(ctx).tableName(ctx, "sys.x.gtx_module_${contextId}_state_leafs")
                                        """
                                    select t1.state_n, t1.data from $table t1
                                    inner join (select state_n, max(block_height) from $table group by state_n) t2
                                    ON t1.state_n = t2.state_n and t1.block_height = t2.max
                                    ORDER by t1.state_n
                                    """
                                    })
                        }

                        // ----------------------------------------------------------------
                        // 3. Root snapshot Merkle page (latest entry only)
                        // sys.x.gtx_module_root_snapshot_pages stores the pages of the
                        // Merkle tree that summarises all module leafs.  We verify that
                        // the most recent root page matches – meaning the replica computed
                        // an identical Merkle root to the validator.
                        // page_iid is a DB-internal sequence and is intentionally excluded
                        // from the comparison; it will differ between schemas.
                        // ----------------------------------------------------------------
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicSQLContentProvider { ctx ->
                                    val table = DatabaseAccess.of(ctx).tableName(ctx, "sys.x.gtx_module_root_snapshot_pages")
                                    """
                                        select block_height, level, left_index, child_hashes from $table
                                        order by block_height desc limit 1;
                                    """
                                })

                        // ----------------------------------------------------------------
                        // 4. ICMF sent messages (sender side)
                        // The update_node_with_node_data operation emitted an ICMF message.
                        // The replica must have reconstructed the same sent_icmf_message
                        // rows from snapshot data.  We compare topic, height, body, and
                        // datum_id (the stable ordering key).
                        // ----------------------------------------------------------------
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicSQLContentProvider { ctx ->
                                    val table = DatabaseAccess.of(ctx).tableName(ctx, "${IcmfSenderDatabaseOperationsImpl.PREFIX}.sent_icmf_message")
                                    "select topic, height, body, datum_id from $table order by datum_id"
                                })

                        // ----------------------------------------------------------------
                        // 5. ICMF receiver-side tables
                        // The receiver tracks which messages have been processed:
                        //   anchor_height     – last anchored block height per topic
                        //   message_height    – last processed message height per topic
                        //   spilled_message   – messages that overflowed the page buffer
                        //   dapp_provided_receiver_topic – topics registered by dapps
                        // These are compared in full (SELECT *) via basicTablesContentProvider
                        // which normalises row ordering for deterministic comparison.
                        // ----------------------------------------------------------------
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicTablesContentProvider(
                                        listOf("${IcmfReceiverDatabaseOperationsImpl.PREFIX}.anchor_height",
                                                "${IcmfReceiverDatabaseOperationsImpl.PREFIX}.message_height",
                                                "${IcmfReceiverDatabaseOperationsImpl.PREFIX}.spilled_message",
                                                "${IcmfReceiverDatabaseOperationsImpl.PREFIX}.dapp_provided_receiver_topic")
                                ))

                        // ----------------------------------------------------------------
                        // 6. All Rell entity tables (c0.*)
                        // Enumerate every table in the c0 chain namespace and compare
                        // content row by row.  System/infrastructure tables that are not
                        // Rell entities are excluded:
                        //   c0.sys.*         – snapshot infrastructure tables (already
                        //                      covered in checks 1-3 above)
                        //   c0.transactions  – block-level metadata, not Rell state
                        //   c0.rowid_gen     – sequence tracking table, diverges by design
                        //   c0.blocks        – block headers, differ after snapshot sync
                        //   c0.configurations – config history, differs (replica received
                        //                       configs pre-seeded, not via block replay)
                        //   c0.gtx_module_version – module version bookkeeping, not Rell
                        // Rows are ordered by the Postchain `rowid` column.  Because of the
                        // FORCE_SNAPSHOT rowid allocation behaviour, rowids may shift for
                        // objects created after the snapshot point; tests should therefore
                        // only be run up to the first vote operation (~9k blocks on devnet1).
                        // ----------------------------------------------------------------
                        val expectedC0Tables = mutableListOf<String>()
                        expectedContext.conn.metaData.getTables(null, expectedContext.conn.schema, "c0%", arrayOf("TABLE")).use { rs ->
                            while (rs.next()) {
                                expectedC0Tables.add(rs.getString("TABLE_NAME"))
                            }
                        }
                        val nonRellTables = setOf("c0.transactions", "c0.rowid_gen", "c0.blocks", "c0.configurations", "c0.gtx_module_version")
                        expectedC0Tables
                                .filterNot { it.startsWith("c0.sys") || it in nonRellTables }
                                .forEach { tableName ->
                                    assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                            basicSQLContentProvider { _ ->
                                                "select * from \"$tableName\" order by rowid"
                                            })
                                }
                    }
                }
            }
        }
    }
}
