package net.postchain.d1

import mu.KLogging
import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import java.sql.Statement

/**
 * This is a test module with the purpose to detect deadlocks during chain startup. Place it as second in the
 * module list (after rell) to make it lock all entity tables to simulate rell updating them.
 */
class ExclusiveTableLockTestGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf()
) {
    companion object : KLogging() {
        // These tables already cause deadlock on update - ignore for now to be able to test for new deadlocks
        val TABLE_IGNORE_LIST = listOf(
                // Postchain tables
                "c[0-9]+.blocks",
                "c[0-9]+.configurations",
                // Entity tables
                "c0.blockchain",
                "c0.blockchain_replica_node",
                "c0.cluster",
                "c0.cluster_anchoring_chain",
                "c0.cluster_node",
                "c0.cluster_replica_node",
                "c0.container",
                "c0.container_blockchain",
                "c0.faulty_blockchain_configuration",
                "c0.importing_foreign_blockchain",
                "c0.moving_blockchain",
                "c0.node",
                "c0.signer_excluded_from_pending_configuration",
                "c0.system_anchoring_chain",
                "c0.unarchiving_blockchain",
                // TXS - shouldBuildBlock()
                "c[0-9]+.evm_submit_transaction",
                "c[0-9]+.evm_submit_transaction_taken_by",
        ).map(String::toRegex)
    }

    override fun initializeDB(ctx: EContext) {

        DatabaseAccess.of(ctx).apply {
            ctx.conn.createStatement().use { statement ->

                val pidQuery = statement.executeQuery("SELECT pg_backend_pid()")
                pidQuery.next()
                logger.warn { "Thread ${Thread.currentThread().name} starts to lock tables on chain ${ctx.chainID} in transaction pid ${pidQuery.getInt(1)}" }

                getTables(statement, ctx.chainID)
                        .filter { tableName ->
                            if (TABLE_IGNORE_LIST.any { it.matches(tableName) }) {
                                logger.info { " - Ignored table: $tableName" }
                                false
                            } else {
                                true
                            }
                        }
                        .forEach { tableName ->
                            try {
                                logger.info(" - Locking table $tableName")
                                statement.execute("LOCK TABLE \"$tableName\" IN ACCESS EXCLUSIVE MODE")
                            } catch (e: Exception) {
                                logger.error { " - Failed to lock table $tableName: ${e.message}" }
                                throw e
                            }
                        }
                logger.info("Thread ${Thread.currentThread().name} has locked all tables, now keeping the lock alive...")
            }
        }
    }

    private fun getTables(statement: Statement, chainID: Long): List<String> {
        val executeQuery = statement.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() AND " +
                "table_type = 'BASE TABLE'")
        val tables = mutableListOf<String>()
        val entityTablePattern = "c${chainID}.[^.]*".toRegex()

        while (executeQuery.next()) {
            val tableName = executeQuery.getString("table_name")
            if (entityTablePattern.matches(tableName)) {
                tables.add(tableName)
            }
        }

        return tables
    }
}