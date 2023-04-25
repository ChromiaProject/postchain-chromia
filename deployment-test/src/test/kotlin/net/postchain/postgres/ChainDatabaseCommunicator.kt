package net.postchain.postgres

import net.postchain.base.BaseBlockHeader
import net.postchain.base.BaseEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.DatabaseAccessFactory
import net.postchain.base.extension.CONFIG_HASH_EXTRA_HEADER
import net.postchain.common.BlockchainRid
import net.postchain.common.exception.UserMistake
import net.postchain.common.wrap
import net.postchain.crypto.Secp256K1CryptoSystem
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvDecoder
import net.postchain.gtv.merkle.GtvMerkleHashCalculator
import org.apache.commons.dbcp2.BasicDataSource
import org.awaitility.Duration
import org.awaitility.kotlin.await

class ChainDatabaseCommunicator(chainIId: Long, schema: String, dbConfig: DatabaseConfig) {

    private val cs = Secp256K1CryptoSystem()
    private val dbAccess = DatabaseAccessFactory.createDatabaseAccess(dbConfig.driverClassName)
    private val dataSource = BasicDataSource().apply {
        addConnectionProperty("currentSchema", schema)
        driverClassName = dbConfig.driverClassName
        url = dbConfig.jdbcUrl
        username = dbConfig.username
        password = dbConfig.password
        defaultAutoCommit = true
        maxTotal = 5
        defaultReadOnly = true
    }

    private val eContext = BaseEContext(dataSource.connection, chainIId, dbAccess)

    fun getHeight(): Long {
        return DatabaseAccess.of(eContext).getLastBlockHeight(eContext)
    }

    fun awaitBlockHeight(height: Long, timeOut: Duration = Duration.TWO_MINUTES) {
        println("Awaiting height $height on chain ${eContext.chainID}")
        await.pollInterval(Duration.TEN_SECONDS).atMost(timeOut).until {
            getHeight() >= height
        }
    }

    fun awaitNewBlock(timeOut: Duration = Duration.TWO_MINUTES) {
        val currentHeight = getHeight()
        awaitBlockHeight(currentHeight + 1, timeOut)
    }

    fun getGtvConfigurationsUsedForBlockBuildingBeforeHeight(height: Long): List<Map<String, Gtv>> {
        val db = DatabaseAccess.of(eContext)
        val configHashes = db.getBlocksBeforeHeight(eContext, height, Int.MAX_VALUE)
                .map {
                    BaseBlockHeader(it.blockHeader, GtvMerkleHashCalculator(cs))
                            .extraData[CONFIG_HASH_EXTRA_HEADER]?.asByteArray()?.wrap()
                            ?: throw UserMistake("BlockHeader doesn't contain $CONFIG_HASH_EXTRA_HEADER extra field")
                }.toSet()

        return configHashes.map {
            db.getConfigurationData(eContext, it.data)?.let { data ->
                GtvDecoder.decodeGtv(data).asDict()
            } ?: throw UserMistake("Unknown config hash detected: $it")
        }
    }

    fun getChainId(blockchainRid: BlockchainRid): Long =
            DatabaseAccess.of(eContext).getChainId(eContext, blockchainRid)
                    ?: throw UserMistake("Can't find chain: $blockchainRid")

}
