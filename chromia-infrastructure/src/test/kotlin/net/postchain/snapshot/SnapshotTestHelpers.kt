package net.postchain.snapshot

import assertk.Assert
import assertk.assertions.support.expected
import net.postchain.base.data.DatabaseAccess
import net.postchain.common.toHex
import net.postchain.config.app.AppConfig
import net.postchain.core.EContext
import org.apache.commons.configuration2.BaseConfiguration
import java.util.StringJoiner
import kotlin.use

/**
 * Creates an [AppConfig] wired for the local PostgreSQL test database with sensible defaults
 * for the snapshot manual tests.
 *
 * The defaults point at `localhost:5432` with the standard `postchain`/`postchain`
 * credentials and enable the D1 infrastructure factory.  Any property can be overridden via
 * [overrides], which is most commonly used to set `database.schema` and messaging keys so
 * that multiple nodes can share the same PostgreSQL instance without interfering.
 *
 * @param overrides Key-value pairs that override (or extend) the base configuration.
 *   Keys use Apache Commons Configuration dot-notation, e.g. `"database.schema"`.
 */
fun createAppConfig(overrides: Map<String, Any>): AppConfig {
    val baseConfig = mapOf(
            "infrastructure" to "net.postchain.d1.D1InfrastructureFactory",
            "api.enable_tls" to "false",
            "configuration.provider" to "managed",
            "database.driverclass" to "org.postgresql.Driver",
            "database.username" to "postchain",
            "database.password" to "postchain",
            "database.url" to "jdbc:postgresql://localhost:5432/postchain",
            // -1 disables the REST API and debug HTTP server so nodes don't compete for ports.
            "api.port" to -1,
            "debug.port" to -1,
            // 2000 ms exit delay gives fastsync a moment to settle before the node stops.
            "fastsync.exit_delay" to 2000,
            // Disable rate limiting so block production is not throttled during tests.
            "rate-limit.blocks" to Long.MAX_VALUE,
            // Allow up to 100 concurrent DB reads to support fast parallel snapshot sync.
            "database.readConcurrency" to "100",
    )
    val nodeConfiguration = BaseConfiguration()
    baseConfig.forEach { (key, value) -> nodeConfiguration.addProperty(key, value) }
    overrides.forEach { (key, value) -> nodeConfiguration.addProperty(key, value) }
    return AppConfig(nodeConfiguration)
}


/**
 * Returns a content provider that executes an arbitrary SQL query against a connection
 * context and returns the result rows as a map keyed by an empty string.
 *
 * The outer key (`""`) is a placeholder so the result can be fed into
 * [hasIdenticalDbContentAs] alongside [basicTablesContentProvider] results without
 * changing the comparison interface.  Each row is represented as a `Map<String, Any>`
 * of column-name → value.  `bytea` columns are hex-encoded so they can be compared as
 * plain strings.
 *
 * Usage pattern:
 * ```kotlin
 * basicSQLContentProvider { ctx ->
 *     val table = DatabaseAccess.of(ctx).tableName(ctx, "sys.x.my_table")
 *     "SELECT col_a, col_b FROM $table ORDER BY col_a"
 * }
 * ```
 *
 * @param sql Lambda that receives an [EContext] (so it can resolve schema-qualified table
 *   names via [DatabaseAccess.tableName]) and returns the SQL string to execute.
 */
fun basicSQLContentProvider(sql: (EContext) -> String): (EContext) -> Map<String, Any> = { ctx ->
    ctx.conn.createStatement().use { stmt ->
        stmt.executeQuery(sql(ctx)).use { rs ->
            val columns = (1..rs.metaData.columnCount).map {
                rs.metaData.getColumnName(it) to rs.metaData.getColumnTypeName(it)
            }
            val results = mutableListOf<Map<String, Any>>()
            while (rs.next()) {
                val row = columns.map { (name, type) ->
                    name to if (type == "bytea") {
                        // Hex-encode binary blobs so they compare as readable strings.
                        rs.getBytes(name).toHex()
                    } else {
                        rs.getString(name)
                    }
                }
                results.add(row.toMap())
            }
            mapOf("" to results.toList())
        }
    }
}

/**
 * Returns a content provider that fetches full row contents from one or more named tables
 * and returns them keyed by logical table name.
 *
 * Table names are resolved via [DatabaseAccess.tableName] to handle PostgreSQL schema
 * prefixes (e.g. `"sys.snapshot_contexts"` becomes `"myschema.sys.snapshot_contexts"`).
 * Rows are sorted deterministically by concatenating all column values alphabetically so
 * that two databases whose rows were inserted in different orders still compare as equal.
 * `bytea` columns are hex-encoded for string comparison.
 *
 * @param tables Logical table name list using Postchain's dot-notation
 *   (e.g. `"icmf.anchor_height"`).
 * @param sql Optional SQL template that takes the resolved table name and returns the query.
 *   Defaults to `SELECT * FROM <table>`.  Override to add WHERE clauses or column filters.
 */
fun basicTablesContentProvider(
        tables: List<String>,
        sql: (String) -> String = { "SELECT * FROM $it" },
): (EContext) -> Map<String, Any> = { ctx ->
    tables.associateWith { table ->
        val tableName = DatabaseAccess.of(ctx).tableName(ctx, table)
        ctx.conn.createStatement().use { stmt ->
            stmt.executeQuery(sql(tableName)).use { rs ->
                val columns = (1..rs.metaData.columnCount).map {
                    rs.metaData.getColumnName(it) to rs.metaData.getColumnTypeName(it)
                }
                val results = mutableListOf<Map<String, Any>>()
                while (rs.next()) {
                    val row = columns.map { (name, type) ->
                        name to if (type == "bytea") {
                            rs.getBytes(name).toHex()
                        } else {
                            rs.getString(name)
                        }
                    }
                    results.add(row.toMap())
                }
                // Sort rows by the lexicographic concatenation of all column values so that
                // insertion-order differences between schemas do not cause false failures.
                results.toList().sortedBy {
                    val sj = StringJoiner(",")
                    it.keys
                            .sorted()
                            .forEach { key ->
                                sj.add(it[key] as String)
                            }
                    sj.toString()
                }
            }
        }
    }
}

/**
 * AssertK extension that asserts the receiver [EContext]'s database content matches
 * [sourceCtx]'s content as produced by [contentProvider].
 *
 * [contentProvider] is called with both contexts and the results are compared for equality.
 * If they differ, the assertion fails with a structured diff showing expected vs actual.
 *
 * Example usage:
 * ```kotlin
 * assertThat(replicaCtx).hasIdenticalDbContentAs(validatorCtx,
 *     basicTablesContentProvider(listOf("sys.snapshot_contexts")))
 * ```
 *
 * @param sourceCtx The reference context (expected values).
 * @param contentProvider A function that extracts comparable content from an [EContext].
 *   Use [basicSQLContentProvider] for custom queries or [basicTablesContentProvider] for
 *   full-table dumps.
 */
fun Assert<EContext>.hasIdenticalDbContentAs(
        sourceCtx: EContext,
        contentProvider: (EContext) -> Map<String, Any>
) = given { ctx ->
    val expected = contentProvider(sourceCtx)
    val actual = contentProvider(ctx)
    if (expected != actual) {
        expected("to have identical table content for node ", expected, actual)
    }
}
