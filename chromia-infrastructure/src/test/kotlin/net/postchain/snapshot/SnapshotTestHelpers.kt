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

fun createAppConfig(overrides: Map<String, Any>): AppConfig {
    val baseConfig = mapOf(
            "infrastructure" to "net.postchain.d1.D1InfrastructureFactory",
            "api.enable_tls" to "false",
            "configuration.provider" to "managed",
            "database.driverclass" to "org.postgresql.Driver",
            "database.username" to "postchain",
            "database.password" to "postchain",
            "database.url" to "jdbc:postgresql://172.17.0.1:5432/postchain",
            "api.port" to -1,
            "debug.port" to -1,
            "fastsync.exit_delay" to 2000,
            "rate-limit.blocks" to Long.MAX_VALUE,
            "database.readConcurrency" to "100",
    )
    val nodeConfiguration = BaseConfiguration()
    baseConfig.forEach { (key, value) -> nodeConfiguration.addProperty(key, value) }
    overrides.forEach { (key, value) -> nodeConfiguration.addProperty(key, value) }
    return AppConfig(nodeConfiguration)
}


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
