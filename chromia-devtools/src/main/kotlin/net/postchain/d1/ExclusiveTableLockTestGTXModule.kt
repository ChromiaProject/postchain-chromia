package net.postchain.d1

import net.postchain.base.data.DatabaseAccess
import net.postchain.core.EContext
import net.postchain.gtx.SimpleGTXModule
import java.sql.Statement
import kotlin.apply
import kotlin.collections.any
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.text.toRegex
import kotlin.use

/**
 * This is a test module with the purpose to detect deadlocks during chain startup. Place it as second in the
 * module list (after rell) to make it lock all entity tables to simulate rell updating them.
 */
class ExclusiveTableLockTestGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(),
        mapOf()
) {
    companion object : mu.KLogging() {
        val TABLE_IGNORE_LIST = listOf(
                // blocks and configurations tables are never altered by chain configurations,
                // they are managed by postchain and should not be considered as deadlock candidates
                "c[0-9]+.blocks",
                "c[0-9]+.configurations",
                "c[0-9]+.transactions",
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