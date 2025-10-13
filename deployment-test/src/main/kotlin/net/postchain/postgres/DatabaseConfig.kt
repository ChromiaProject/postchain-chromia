package net.postchain.postgres

data class DatabaseConfig(
        val driverClassName: String,
        val jdbcUrl: String,
        val username: String,
        val password: String
)
