package net.postchain.crypto.webauthn

import com.webauthn4j.data.attestation.authenticator.AAGUID
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.common.wrap
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtx.SnapshotContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DSL.constraint
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.using
import org.jooq.impl.SQLDataType

class WebAuthnRepositoryImpl : WebAuthnRepository {
    companion object {
        const val PRIMARY_KEY_PREFIX: String = "PK_"
        const val FOREIGN_KEY_SUFFIX: String = "_FK"

        const val TABLE_NAME_TRANSACTIONS = "transactions"
        val COLUMN_TX_IID = field("tx_iid", SQLDataType.BIGINT.nullable(false))
        val COLUMN_TX_RID = field("tx_rid", SQLDataType.BLOB.nullable(false))

        const val PREFIX: String = "sys.x.webauthn" // This name should not clash with Rell

        const val ALIAS_CREDENTIAL_DATA = "credential_data"

        val COLUMN_DATUM_ID = field("datum_id", SQLDataType.BIGINT.nullable(false).identity(true))

        const val TABLE_NAME_DATUM_ID = "${PREFIX}.datum_id"

        const val TABLE_NAME_CREDENTIAL = "${PREFIX}.credential"
        val COLUMN_ID = field("id", SQLDataType.BLOB.nullable(false))
        val COLUMN_DELETED = field("deleted", SQLDataType.BOOLEAN.nullable(false))
        val COLUMN_TRANSACTION = field("transaction", SQLDataType.BIGINT.nullable(false))
        val COLUMN_OP_INDEX = field("op_index", SQLDataType.INTEGER.nullable(false))
        val COLUMN_AAGUID = field("aaguid",
                SQLDataType.BLOB.nullable(false).defaultValue(AAGUID.ZERO.bytes))
        val COLUMN_PUBLIC_KEY = field("public_key", SQLDataType.BLOB.nullable(false))
        val COLUMN_SIGN_COUNT = field("sign_count", SQLDataType.BIGINT.nullable(false))
        val COLUMN_TRANSPORTS = field("transports", SQLDataType.CLOB.nullable(false))
        val COLUMN_UV_INITIALIZED = field("uv_initialized", SQLDataType.BOOLEAN.nullable(false))
        val COLUMN_BACKUP_ELIGIBLE = field("backup_eligible", SQLDataType.BOOLEAN.nullable(false))
        val COLUMN_BACKUP_STATE = field("backup_state", SQLDataType.BOOLEAN.nullable(false))
        val COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED = field("suspicious_sign_count_presented",
                SQLDataType.BIGINT.nullable(false).defaultValue(-1L))
        val COLUMN_SUSPICIOUS_SIGN_COUNT_STORED = field("suspicious_sign_count_stored",
                SQLDataType.BIGINT.nullable(false).defaultValue(-1L))

        const val TABLE_NAME_CHALLENGE = "${PREFIX}.challenge"
        val COLUMN_CHALLENGE = field("challenge", SQLDataType.BLOB.nullable(false))
    }

    @Volatile
    private var snapshotContext: SnapshotContext? = null

    private fun DatabaseAccess.tableTransactions(ctx: EContext) = tableName(ctx, TABLE_NAME_TRANSACTIONS)
    private fun DatabaseAccess.tableDatumId(ctx: EContext) = tableName(ctx, TABLE_NAME_DATUM_ID)
    private fun DatabaseAccess.tableCredential(ctx: EContext) = tableName(ctx, TABLE_NAME_CREDENTIAL)
    private fun DatabaseAccess.tableChallenge(ctx: EContext) = tableName(ctx, TABLE_NAME_CHALLENGE)

    override fun initializeDB(ctx: EContext) {
        DatabaseAccess.of(ctx).apply {
            val jooq = dslContext(ctx)

            jooq.createTableIfNotExists(table(tableDatumId(ctx), TABLE_NAME_DATUM_ID))
                    .column(COLUMN_DATUM_ID)
                    .execute()

            val simCredentialTableName = tableName(ctx, TABLE_NAME_CREDENTIAL).replace("\"", "")
            jooq.createTableIfNotExists(table(tableCredential(ctx), TABLE_NAME_CREDENTIAL))
                    .column(COLUMN_DATUM_ID)
                    .column(COLUMN_ID)
                    .column(COLUMN_DELETED)
                    .column(COLUMN_TRANSACTION)
                    .column(COLUMN_OP_INDEX)
                    .column(COLUMN_AAGUID)
                    .column(COLUMN_PUBLIC_KEY)
                    .column(COLUMN_SIGN_COUNT)
                    .column(COLUMN_TRANSPORTS)
                    .column(COLUMN_UV_INITIALIZED)
                    .column(COLUMN_BACKUP_ELIGIBLE)
                    .column(COLUMN_BACKUP_STATE)
                    .column(COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED)
                    .column(COLUMN_SUSPICIOUS_SIGN_COUNT_STORED)
                    .constraints(
                            constraint("${PRIMARY_KEY_PREFIX}$simCredentialTableName").primaryKey(COLUMN_ID.name),
                            constraint("${simCredentialTableName}_${COLUMN_TRANSACTION.name}${FOREIGN_KEY_SUFFIX}")
                                    .foreignKey(COLUMN_TRANSACTION.name)
                                    .references(tableTransactions(ctx).replace("\"", ""), COLUMN_TX_IID.name),
                            constraint("${simCredentialTableName}_${COLUMN_DATUM_ID.name}_unique").unique(COLUMN_DATUM_ID.name)
                    )
                    .execute()

            jooq.alterTable(table(tableCredential(ctx)))
                    .addColumnIfNotExists(COLUMN_AAGUID)
                    .execute()

            jooq.alterTable(table(tableCredential(ctx)))
                    .addColumnIfNotExists(COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED)
                    .execute()

            jooq.alterTable(table(tableCredential(ctx)))
                    .addColumnIfNotExists(COLUMN_SUSPICIOUS_SIGN_COUNT_STORED)
                    .execute()

            val simChallengeTableName = tableName(ctx, TABLE_NAME_CHALLENGE).replace("\"", "")
            jooq.createTableIfNotExists(table(tableChallenge(ctx), TABLE_NAME_CHALLENGE))
                    .column(COLUMN_DATUM_ID)
                    .column(COLUMN_CHALLENGE)
                    .constraints(
                            constraint("${PRIMARY_KEY_PREFIX}$simChallengeTableName").primaryKey(COLUMN_CHALLENGE.name),
                            constraint("${simChallengeTableName}_${COLUMN_DATUM_ID.name}_unique").unique(COLUMN_DATUM_ID.name)
                    )
                    .execute()
        }
    }

    override fun persistCredential(ctx: TxEContext, opIndex: Int, data: CredentialData) {
        val datumId = DatabaseAccess.of(ctx).run {
            val nextDatumId = nextDatumId(ctx)
            dslContext(ctx).insertInto(table(tableCredential(ctx)))
                    .set(COLUMN_DATUM_ID, nextDatumId)
                    .set(COLUMN_ID, data.id.data)
                    .set(COLUMN_DELETED, false)
                    .set(COLUMN_TRANSACTION, ctx.txIID)
                    .set(COLUMN_OP_INDEX, opIndex)
                    .set(COLUMN_AAGUID, data.aaguid.data)
                    .set(COLUMN_PUBLIC_KEY, data.publicKey.data)
                    .set(COLUMN_SIGN_COUNT, data.signCount)
                    .set(COLUMN_TRANSPORTS, data.transports)
                    .set(COLUMN_UV_INITIALIZED, data.uvInitialized)
                    .set(COLUMN_BACKUP_ELIGIBLE, data.backupEligible)
                    .set(COLUMN_BACKUP_STATE, data.backupState)
                    .set(COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED, -1)
                    .set(COLUMN_SUSPICIOUS_SIGN_COUNT_STORED, -1)
                    .execute()
            nextDatumId
        }

        snapshotContext?.emitDatum(
                ctx,
                datumId,
                data.copy(
                        txRid = ctx.tx.getRID().wrap(),
                        opIndex = opIndex.toLong(),
                ).toGtv(),
                false
        )
    }

    private fun nextDatumId(ctx: BlockEContext): Long = DatabaseAccess.of(ctx).run {
        val lastDatumId = dslContext(ctx).select(
                COLUMN_DATUM_ID,
        )
                .from(tableDatumId(ctx))
                .fetchOne()?.get(COLUMN_DATUM_ID)

        if (lastDatumId == null) {
            dslContext(ctx).insertInto(table(tableDatumId(ctx)))
                    .set(COLUMN_DATUM_ID, 0L)
                    .execute()
            0
        } else {
            dslContext(ctx).update(table(tableDatumId(ctx)))
                    .set(COLUMN_DATUM_ID, lastDatumId + 1)
                    .execute()
            lastDatumId + 1
        }
    }

    override fun updateCredential(
            ctx: BlockEContext, id: ByteArray,
            signCount: Long, uvInitialized: Boolean, backupState: Boolean,
            suspiciousSignCountPresented: Long?, suspiciousSignCountStored: Long?,
    ): Unit = DatabaseAccess.of(ctx).run {
        dslContext(ctx).update(table(tableCredential(ctx)))
                .set(COLUMN_SIGN_COUNT, signCount)
                .set(COLUMN_UV_INITIALIZED, uvInitialized)
                .set(COLUMN_BACKUP_STATE, backupState)
                .set(COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED, suspiciousSignCountPresented ?: -1)
                .set(COLUMN_SUSPICIOUS_SIGN_COUNT_STORED, suspiciousSignCountStored ?: -1)
                .where(COLUMN_ID.eq(id))
                .execute()

        fetchCredentialInternal(ctx, id)?.let { (datumId, data) ->
            snapshotContext?.emitDatum(
                    ctx,
                    datumId,
                    data.toGtv(),
                    false
            )
        }
    }

    override fun deleteCredential(ctx: BlockEContext, id: ByteArray): Unit = DatabaseAccess.of(ctx).run {
        dslContext(ctx).update(table(tableCredential(ctx)))
                .set(COLUMN_DELETED, true)
                .where(COLUMN_ID.eq(id))
                .execute()

        fetchCredentialInternal(ctx, id)?.let { (datumId, data) ->
            snapshotContext?.emitDatum(
                    ctx,
                    datumId,
                    data.toGtv(),
                    false
            )
        }
    }

    override fun fetchCredential(ctx: EContext, id: ByteArray): CredentialData? =
            fetchCredentialInternal(ctx, id)?.let { if (!it.second.deleted) it.second else null }

    override fun persistChallenge(ctx: BlockEContext, challenge: ByteArray) {
        val datumId = DatabaseAccess.of(ctx).run {
            val nextDatumId = nextDatumId(ctx)
            dslContext(ctx).insertInto(table(tableChallenge(ctx)))
                    .set(COLUMN_DATUM_ID, nextDatumId)
                    .set(COLUMN_CHALLENGE, challenge)
                    .execute()
            nextDatumId
        }

        snapshotContext?.emitDatum(
                ctx,
                datumId,
                ChallengeData(challenge = challenge.wrap()).toGtv(),
                true
        )
    }

    override fun challengeExists(ctx: EContext, challenge: ByteArray) = DatabaseAccess.of(ctx).run {
        dslContext(ctx).select(
                COLUMN_CHALLENGE,
        )
                .from(tableChallenge(ctx))
                .where(COLUMN_CHALLENGE.eq(challenge))
                .count() > 0
    }

    override fun initializeSnapshotContext(context: SnapshotContext) {
        snapshotContext = context
    }

    override fun getPermanentDatumIdMax(ctx: EContext): Long? = DatabaseAccess.of(ctx).run {
        dslContext(ctx).select(DSL.max(COLUMN_DATUM_ID))
                .from(tableChallenge(ctx))
                .fetchOne()?.value1()
    }

    override fun getPermanentDatums(ctx: EContext, datumIdFrom: Long, datumHandler: (datum: SnapshotDatum?) -> Boolean) {
        var continueStreaming = true
        streamChallengesFromDatumId(ctx, datumIdFrom) { row ->
            if (!continueStreaming) return@streamChallengesFromDatumId false
            val datumGtv = row.second.toGtv()
            val datum = SnapshotDatum(row.first, datumGtv, true)
            continueStreaming = datumHandler(datum)
            continueStreaming
        }
        // Signal end of stream
        if (continueStreaming) {
            datumHandler(null)
        }
    }

    private fun streamChallengesFromDatumId(ctx: EContext, from: Long, rowHandler: (Pair<Long, ChallengeData>) -> Boolean) {
        DatabaseAccess.of(ctx).run {
            val jooq = dslContext(ctx)
            val cursor = jooq.select(COLUMN_DATUM_ID, COLUMN_CHALLENGE)
                    .from(tableChallenge(ctx))
                    .where(COLUMN_DATUM_ID.ge(from))
                    .orderBy(COLUMN_DATUM_ID)
                    .fetchSize(1)
                    .fetchLazy()
            cursor.use { c ->
                for (rec in c) {
                    val row = rec[COLUMN_DATUM_ID] to ChallengeData(
                            challenge = rec[COLUMN_CHALLENGE].wrap(),
                    )
                    if (!rowHandler(row)) break
                }
            }
        }
    }

    override fun constructDatum(ctx: EContext, datumList: List<SnapshotDatum>) {
        val datums = datumList.map {
            it.id to Entity.fromGtv(it.data)
        }

        val credentials = datums.filter { it.second is CredentialData }
        if (credentials.isNotEmpty()) {
            DatabaseAccess.of(ctx).run {
                val jooq = dslContext(ctx)

                val valuesRows = credentials.map { (datumId, entity) ->
                    val credential = entity as CredentialData // safe since we filter on is CredentialData above
                    DSL.row(
                            datumId,
                            credential.id.data,
                            credential.deleted,
                            credential.txRid?.data,
                            credential.opIndex,
                            credential.aaguid.data,
                            credential.publicKey.data,
                            credential.signCount,
                            credential.transports,
                            credential.uvInitialized,
                            credential.backupEligible,
                            credential.backupState,
                            credential.suspiciousSignCountPresented ?: -1,
                            credential.suspiciousSignCountStored ?: -1,
                    )
                }

                val valuesTable = DSL.values(*valuesRows.toTypedArray())
                        .`as`(
                                ALIAS_CREDENTIAL_DATA,
                                COLUMN_DATUM_ID.name,
                                COLUMN_ID.name,
                                COLUMN_DELETED.name,
                                COLUMN_TX_RID.name,
                                COLUMN_OP_INDEX.name,
                                COLUMN_AAGUID.name,
                                COLUMN_PUBLIC_KEY.name,
                                COLUMN_SIGN_COUNT.name,
                                COLUMN_TRANSPORTS.name,
                                COLUMN_UV_INITIALIZED.name,
                                COLUMN_BACKUP_ELIGIBLE.name,
                                COLUMN_BACKUP_STATE.name,
                                COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED.name,
                                COLUMN_SUSPICIOUS_SIGN_COUNT_STORED.name,
                        )

                // Insert with join to get tx_iid from tx_rid
                jooq.insertInto(table(tableCredential(ctx)))
                        .columns(
                                COLUMN_DATUM_ID,
                                COLUMN_ID,
                                COLUMN_DELETED,
                                COLUMN_TRANSACTION,
                                COLUMN_OP_INDEX,
                                COLUMN_AAGUID,
                                COLUMN_PUBLIC_KEY,
                                COLUMN_SIGN_COUNT,
                                COLUMN_TRANSPORTS,
                                COLUMN_UV_INITIALIZED,
                                COLUMN_BACKUP_ELIGIBLE,
                                COLUMN_BACKUP_STATE,
                                COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED,
                                COLUMN_SUSPICIOUS_SIGN_COUNT_STORED,
                        ).select(
                                DSL.select(
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_DATUM_ID.name}", SQLDataType.BIGINT),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_ID.name}", SQLDataType.BLOB),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_DELETED.name}", SQLDataType.BOOLEAN),
                                        COLUMN_TX_IID,
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_OP_INDEX.name}", SQLDataType.INTEGER),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_AAGUID.name}", SQLDataType.BLOB),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_PUBLIC_KEY.name}", SQLDataType.BLOB),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_SIGN_COUNT.name}", SQLDataType.BIGINT),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_TRANSPORTS.name}", SQLDataType.CLOB),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_UV_INITIALIZED.name}", SQLDataType.BOOLEAN),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_BACKUP_ELIGIBLE.name}", SQLDataType.BOOLEAN),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_BACKUP_STATE.name}", SQLDataType.BOOLEAN),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED.name}", SQLDataType.BIGINT),
                                        field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_SUSPICIOUS_SIGN_COUNT_STORED.name}", SQLDataType.BIGINT),
                                )
                                        .from(valuesTable)
                                        .join(table(tableTransactions(ctx)).asTable("t"))
                                        .on(field("t.${COLUMN_TX_RID.name}", SQLDataType.BLOB).eq(field("${ALIAS_CREDENTIAL_DATA}.${COLUMN_TX_RID.name}", SQLDataType.BLOB)))
                        )
                        .execute()
            }
        }

        val challenges = datums.filter { it.second is ChallengeData }
        if (challenges.isNotEmpty()) {
            DatabaseAccess.of(ctx).run {
                val jooq = dslContext(ctx)

                val valuesRows = challenges.map { (datumId, entity) ->
                    val challenge = entity as ChallengeData // safe since we filter on is ChallengeData above
                    DSL.row(
                            datumId,
                            challenge.challenge.data,
                    )
                }
                jooq.insertInto(table(tableChallenge(ctx)))
                        .columns(
                                COLUMN_DATUM_ID,
                                COLUMN_CHALLENGE,
                        ).valuesOfRows(valuesRows)
                        .execute()
            }
        }
    }

    private fun fetchCredentialInternal(ctx: EContext, id: ByteArray): Pair<Long, CredentialData>? = DatabaseAccess.of(ctx).run {
        dslContext(ctx).select(
                COLUMN_DATUM_ID,
                COLUMN_ID,
                COLUMN_DELETED,
                COLUMN_TX_RID,
                COLUMN_OP_INDEX,
                COLUMN_AAGUID,
                COLUMN_PUBLIC_KEY,
                COLUMN_SIGN_COUNT,
                COLUMN_TRANSPORTS,
                COLUMN_UV_INITIALIZED,
                COLUMN_BACKUP_ELIGIBLE,
                COLUMN_BACKUP_STATE,
                COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED,
                COLUMN_SUSPICIOUS_SIGN_COUNT_STORED,
        )
                .from(tableCredential(ctx))
                .join(table(tableTransactions(ctx)))
                .on(COLUMN_TX_IID.eq(COLUMN_TRANSACTION))
                .where(COLUMN_ID.eq(id))
                .fetchOne()
    }?.map {
        it[COLUMN_DATUM_ID] to CredentialData(
                id = it[COLUMN_ID].wrap(),
                deleted = it[COLUMN_DELETED],
                txRid = it[COLUMN_TX_RID].wrap(),
                opIndex = it[COLUMN_OP_INDEX].toLong(),
                aaguid = it[COLUMN_AAGUID].wrap(),
                publicKey = it[COLUMN_PUBLIC_KEY].wrap(),
                signCount = it[COLUMN_SIGN_COUNT],
                transports = it[COLUMN_TRANSPORTS],
                uvInitialized = it[COLUMN_UV_INITIALIZED],
                backupEligible = it[COLUMN_BACKUP_ELIGIBLE],
                backupState = it[COLUMN_BACKUP_STATE],
                suspiciousSignCountPresented = nullIfMinusOne(it[COLUMN_SUSPICIOUS_SIGN_COUNT_PRESENTED]),
                suspiciousSignCountStored = nullIfMinusOne(it[COLUMN_SUSPICIOUS_SIGN_COUNT_STORED]),
        )
    }

    private fun nullIfMinusOne(value: Long): Long? = if (value == -1L) null else value

    internal fun dslContext(ctx: EContext) = using(ctx.conn, SQLDialect.POSTGRES)
}
