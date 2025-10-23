package net.postchain.snapshot

import assertk.assertThat
import net.postchain.StorageBuilder
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.withReadConnection
import net.postchain.d1.icmf.IcmfReceiverDatabaseOperationsImpl
import net.postchain.d1.icmf.IcmfSenderDatabaseOperationsImpl
import org.junit.jupiter.api.Test

class SnapshotDbCompare {

    @Test
    fun verifyLeafsAndICMF() {
        val expectedConfig = createAppConfig(mapOf(
                "database.schema" to "snapshot_replica_node0"
        ))
        val actualConfig = createAppConfig(mapOf(
                "database.schema" to "snapshot_replica_noder"
        ))

        StorageBuilder.buildStorage(expectedConfig).use { expectedStorage ->
            StorageBuilder.buildStorage(actualConfig).use { actualStorage ->

                withReadConnection(expectedStorage, 0) { expectedContext ->
                    withReadConnection(actualStorage, 0) { actualContext ->

                        val expectedDA = DatabaseAccess.of(expectedContext)
                        val expectedContextIds = expectedDA.getSnapshotModuleContextIds(expectedContext)

                        // Verify module context ids
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicTablesContentProvider(listOf("sys.snapshot_contexts")))

                        // Verify module leafs - ignore block height column
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

                        // Verify last root hash - ignore: page_iid
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicSQLContentProvider { ctx ->
                                    val table = DatabaseAccess.of(ctx).tableName(ctx, "sys.x.gtx_module_root_snapshot_pages")
                                    """
                                        select block_height, level, left_index, child_hashes from $table
                                        order by block_height desc limit 1;
                                    """
                                })

                        // ICMF sender module is identical
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicSQLContentProvider { ctx ->
                                    val table = DatabaseAccess.of(ctx).tableName(ctx, "${IcmfSenderDatabaseOperationsImpl.PREFIX}.sent_icmf_message")
                                    "select topic, height, body, datum_id from $table order by datum_id"
                                })

                        // ICMF receiver module is identical
                        assertThat(actualContext).hasIdenticalDbContentAs(expectedContext,
                                basicTablesContentProvider(
                                        listOf("${IcmfReceiverDatabaseOperationsImpl.PREFIX}.anchor_height",
                                                "${IcmfReceiverDatabaseOperationsImpl.PREFIX}.message_height",
                                                "${IcmfReceiverDatabaseOperationsImpl.PREFIX}.spilled_message",
                                                "${IcmfReceiverDatabaseOperationsImpl.PREFIX}.dapp_provided_receiver_topic")
                                ))
                    }
                }
            }
        }
    }
}

