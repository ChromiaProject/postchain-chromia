package net.postchain.crypto.webauthn

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import net.postchain.StorageBuilder
import net.postchain.base.BaseBlockEContext
import net.postchain.base.BaseTxEContext
import net.postchain.base.data.DatabaseAccess
import net.postchain.base.data.testDbConfig
import net.postchain.base.snapshot.SnapshotDatum
import net.postchain.base.withWriteConnection
import net.postchain.common.BlockchainRid
import net.postchain.common.wrap
import net.postchain.config.app.AppConfig
import net.postchain.crypto.webauthn.WebAuthnRepositoryImpl.Companion.COLUMN_DELETED
import net.postchain.crypto.webauthn.WebAuthnRepositoryImpl.Companion.COLUMN_ID
import net.postchain.crypto.webauthn.WebAuthnRepositoryImpl.Companion.COLUMN_OP_INDEX
import net.postchain.crypto.webauthn.WebAuthnRepositoryImpl.Companion.COLUMN_TRANSACTION
import net.postchain.crypto.webauthn.WebAuthnRepositoryImpl.Companion.TABLE_NAME_CREDENTIAL
import net.postchain.devtools.testinfra.TestTransaction
import net.postchain.gtx.SnapshotContext
import org.jooq.impl.DSL.table
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import java.util.concurrent.TimeUnit

@Timeout(60, unit = TimeUnit.SECONDS)
class WebAuthnRepositoryImplIT {

    val appConfig: AppConfig = testDbConfig("webauthn_repository_it")

    val snapshotContext: SnapshotContext = mock()

    @Test
    fun `should initialize database and insert, fetch, update, and delete credentials with snapshot emission`() {
        val repository = WebAuthnRepositoryImpl()
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            withWriteConnection(storage, 100) { ctx ->
                val db = DatabaseAccess.of(ctx)
                db.initializeBlockchain(ctx, BlockchainRid.buildRepeat(1))
                repository.initializeDB(ctx)
                repository.initializeSnapshotContext(snapshotContext)

                val blockIid = db.insertBlock(ctx, 0)
                val blockCtx = BaseBlockEContext(ctx, 0, blockIid, 0, mapOf()) { _, _, _ -> }
                val tx = TestTransaction(1)
                val txIid = db.insertTransaction(blockCtx, tx, 0)
                val txCtx = BaseTxEContext(blockCtx, txIid, tx)

                val jooq = repository.dslContext(ctx)

                val credentialId = "test_credential_id".toByteArray()
                val publicKey = "test_public_key".toByteArray()
                val transports = "usb,nfc"

                val tableCredential = db.tableName(ctx, TABLE_NAME_CREDENTIAL)
                assertThat(jooq.fetchCount(table(tableCredential))).isEqualTo(0)
                assertThat(repository.fetchCredential(ctx, credentialId)).isNull()

                // Insert credential
                val credential = CredentialData(
                        id = credentialId.wrap(),
                        publicKey = publicKey.wrap(),
                        signCount = 2L,
                        transports = transports,
                        uvInitialized = true,
                        backupEligible = true,
                        backupState = false,
                )
                val opIndex = 1
                reset(snapshotContext)
                repository.persistCredential(txCtx, opIndex, credential)
                verify(snapshotContext).emitDatum(txCtx, 0, credential.copy(
                        txRid = tx.getRID().wrap(),
                        opIndex = opIndex.toLong(),
                ).toGtv(), false)

                val result = requireNotNull(jooq.selectFrom(table(tableCredential))
                        .where(COLUMN_ID.eq(credentialId))
                        .fetchOne())
                assertThat(result[COLUMN_TRANSACTION]).isEqualTo(txIid)
                assertThat(result[COLUMN_OP_INDEX]).isEqualTo(opIndex)

                val fetchedCredential = repository.fetchCredential(ctx, credentialId)
                requireNotNull(fetchedCredential)
                assertThat(fetchedCredential).isEqualTo(credential.copy(
                        txRid = tx.getRID().wrap(),
                        opIndex = opIndex.toLong(),
                ))

                reset(snapshotContext)
                repository.updateCredential(txCtx, credentialId, 5, uvInitialized = false, backupState = true)
                verify(snapshotContext).emitDatum(txCtx, 0, credential.copy(
                        txRid = tx.getRID().wrap(),
                        opIndex = opIndex.toLong(),
                        signCount = 5,
                        uvInitialized = false,
                        backupState = true,
                ).toGtv(), false)

                val fetchedUpdatedCredential = repository.fetchCredential(ctx, credentialId)
                requireNotNull(fetchedUpdatedCredential)
                assertThat(fetchedUpdatedCredential.signCount).isEqualTo(5L)
                assertThat(fetchedUpdatedCredential.uvInitialized).isFalse()
                assertThat(fetchedUpdatedCredential.backupState).isTrue()

                reset(snapshotContext)
                repository.deleteCredential(txCtx, credentialId)
                verify(snapshotContext).emitDatum(txCtx, 0, credential.copy(
                        deleted = true,
                        txRid = tx.getRID().wrap(),
                        opIndex = opIndex.toLong(),
                        signCount = 5,
                        uvInitialized = false,
                        backupState = true,
                ).toGtv(), false)

                val deletedResult = requireNotNull(jooq.selectFrom(table(tableCredential))
                        .where(COLUMN_ID.eq(credentialId))
                        .fetchOne())
                assertThat(deletedResult[COLUMN_DELETED]).isTrue()

                assertThat(repository.fetchCredential(ctx, credentialId)).isNull()

                true
            }
        }
    }

    @Test
    fun `should restore database from snapshot`() {
        val repository = WebAuthnRepositoryImpl()
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            withWriteConnection(storage, 100) { ctx ->
                val db = DatabaseAccess.of(ctx)
                db.initializeBlockchain(ctx, BlockchainRid.buildRepeat(1))
                repository.initializeDB(ctx)
                repository.initializeSnapshotContext(snapshotContext)

                val blockIid = db.insertBlock(ctx, 0)
                val blockCtx = BaseBlockEContext(ctx, 0, blockIid, 0, mapOf()) { _, _, _ -> }
                val tx = TestTransaction(1)
                val txIid = db.insertTransaction(blockCtx, tx, 0)

                val jooq = repository.dslContext(ctx)

                val credentialId = "test_credential_id".toByteArray()
                val publicKey = "test_public_key".toByteArray()
                val transports = "usb,nfc"

                val tableCredential = db.tableName(ctx, TABLE_NAME_CREDENTIAL)
                assertThat(jooq.fetchCount(table(tableCredential))).isEqualTo(0)
                assertThat(repository.fetchCredential(ctx, credentialId)).isNull()

                val opIndex = 1
                val credential = CredentialData(
                        id = credentialId.wrap(),
                        deleted = false,
                        txRid = tx.getRID().wrap(),
                        opIndex = opIndex.toLong(),
                        publicKey = publicKey.wrap(),
                        signCount = 2L,
                        transports = transports,
                        uvInitialized = true,
                        backupEligible = true,
                        backupState = false,
                )

                repository.constructDatum(ctx, listOf(
                        SnapshotDatum(0, credential.toGtv(), false)
                ))

                val result = requireNotNull(jooq.selectFrom(table(tableCredential))
                        .where(COLUMN_ID.eq(credentialId))
                        .fetchOne())
                assertThat(result[COLUMN_TRANSACTION]).isEqualTo(txIid)
                assertThat(result[COLUMN_OP_INDEX]).isEqualTo(opIndex)

                val fetchedCredential = repository.fetchCredential(ctx, credentialId)
                requireNotNull(fetchedCredential)
                assertThat(fetchedCredential).isEqualTo(credential)
                true
            }
        }
    }

    @Test
    fun `should handle multiple credentials independently`() {
        val repository = WebAuthnRepositoryImpl()
        StorageBuilder.buildStorage(appConfig, wipeDatabase = true).use { storage ->
            withWriteConnection(storage, 100) { ctx ->
                val db = DatabaseAccess.of(ctx)
                db.initializeBlockchain(ctx, BlockchainRid.buildRepeat(1))
                repository.initializeDB(ctx)

                val blockIid = db.insertBlock(ctx, 0)
                val blockCtx = BaseBlockEContext(ctx, 0, blockIid, 0, mapOf()) { _, _, _ -> }
                val tx1 = TestTransaction(1)
                val txIid1 = db.insertTransaction(blockCtx, tx1, 1)
                val txCtx1 = BaseTxEContext(blockCtx, txIid1, tx1)
                val tx2 = TestTransaction(2)
                val txIid2 = db.insertTransaction(blockCtx, tx2, 2)
                val txCtx2 = BaseTxEContext(blockCtx, txIid2, tx2)

                val jooq = repository.dslContext(ctx)

                val credentialId1 = "credential_1".toByteArray()
                val credentialId2 = "credential_2".toByteArray()
                val publicKey1 = "public_key_1".toByteArray()
                val publicKey2 = "public_key_2".toByteArray()

                val tableCredential = DatabaseAccess.of(ctx).tableName(ctx, TABLE_NAME_CREDENTIAL)

                val credential1 = CredentialData(
                        id = credentialId1.wrap(),
                        publicKey = publicKey1.wrap(),
                        signCount = 10L,
                        transports = "usb",
                        uvInitialized = true,
                        backupEligible = false,
                        backupState = false,
                )
                repository.persistCredential(txCtx1, 0, credential1)

                val credential2 = CredentialData(
                        id = credentialId2.wrap(),
                        publicKey = publicKey2.wrap(),
                        signCount = 20L,
                        transports = "nfc",
                        uvInitialized = false,
                        backupEligible = true,
                        backupState = true,
                )
                repository.persistCredential(txCtx2, 1, credential2)

                // Verify both credentials exist
                assertThat(jooq.fetchCount(table(tableCredential))).isEqualTo(2)

                val fetched1 = repository.fetchCredential(ctx, credentialId1)
                requireNotNull(fetched1)
                assertThat(fetched1.signCount).isEqualTo(10L)
                assertThat(fetched1.transports).isEqualTo("usb")

                val fetched2 = repository.fetchCredential(ctx, credentialId2)
                requireNotNull(fetched2)
                assertThat(fetched2.signCount).isEqualTo(20L)
                assertThat(fetched2.transports).isEqualTo("nfc")

                repository.updateCredential(blockCtx, credentialId1, 15, uvInitialized = false, backupState = true)

                val updatedFetched1 = requireNotNull(repository.fetchCredential(ctx, credentialId1))
                assertThat(updatedFetched1.signCount).isEqualTo(15L)
                assertThat(updatedFetched1.uvInitialized).isFalse()
                assertThat(updatedFetched1.backupState).isTrue()

                // Verify the second credential unchanged
                val unchangedFetched2 = requireNotNull(repository.fetchCredential(ctx, credentialId2))
                assertThat(unchangedFetched2.signCount).isEqualTo(20L)
                assertThat(unchangedFetched2.uvInitialized).isFalse()
                assertThat(unchangedFetched2.backupState).isTrue()

                repository.deleteCredential(blockCtx, credentialId1)

                // Delete one credential
                jooq.update(table(tableCredential))
                        .set(COLUMN_DELETED, true)
                        .where(COLUMN_ID.eq(credentialId1))
                        .execute()

                assertThat(repository.fetchCredential(ctx, credentialId1)).isNull()
                assertThat(repository.fetchCredential(ctx, credentialId2)).isNotNull()

                true
            }
        }
    }
}
