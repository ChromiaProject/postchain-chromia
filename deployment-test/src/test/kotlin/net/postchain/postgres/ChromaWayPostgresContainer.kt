package net.postchain.postgres

import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.temporal.ChronoUnit

class ChromaWayPostgresContainer(dockerImageName: DockerImageName = DockerImageName.parse("postgres:14.14-alpine3.20@sha256:2bc30c8766a199d04c65abac9b4c08d0498cf96ebd256a02c31e8cc6ad95a4d6"))
    : JdbcDatabaseContainer<ChromaWayPostgresContainer>(dockerImageName) {

    companion object {
        const val POSTGRESQL_PORT = 5432
    }

    init {
        waitStrategy = LogMessageWaitStrategy()
                .withRegEx(".*database system is ready to accept connections.*\\s")
                .withTimes(2)
                .withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS))
        setCommand("postgres", "-c", "fsync=off")
        addExposedPort(POSTGRESQL_PORT)
        withTmpFs(mapOf("/pgtmpfs" to "rw,size=1000m"))
        addEnv("PGDATA", "/pgtmpfs")
        addEnv("POSTGRES_PASSWORD", "postchain")
        addEnv("POSTGRES_USER", "postchain")
        addEnv("POSTGRES_DB", "postchain")
    }

    override fun getUsername() = "postchain"
    override fun getPassword() = "postchain"
    override fun getDatabaseName() = "postchain"
    override fun getTestQueryString() = "SELECT 1;"
    override fun getDriverClassName() = "org.postgresql.Driver"

    override fun getJdbcUrl(): String {
        return databaseUrl("$host:${getMappedPort(POSTGRESQL_PORT)}")
    }

    fun networkJdbcUrl(): String = network?.let { databaseUrl(networkAliases.first()) } ?: jdbcUrl

    private fun databaseUrl(ip: String): String {
        val additionalUrlParams = constructUrlParameters("?", "&")
        return "jdbc:postgresql://$ip/$databaseName$additionalUrlParams"
    }

    fun createChainDatabaseCommunicator(chainId: Long, schema: String): ChainDatabaseCommunicator {
        val dbConfig = DatabaseConfig(driverClassName, jdbcUrl, username, password)
        return ChainDatabaseCommunicator(chainId, schema, dbConfig)
    }
}