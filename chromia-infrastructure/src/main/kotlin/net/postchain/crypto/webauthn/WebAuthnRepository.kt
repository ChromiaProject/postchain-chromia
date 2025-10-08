package net.postchain.crypto.webauthn

import net.postchain.common.exception.UserMistake
import net.postchain.common.types.WrappedByteArray
import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.Gtv
import net.postchain.gtv.mapper.GtvObjectMapper
import net.postchain.gtx.SnapshotAware

interface WebAuthnRepository : SnapshotAware {
    fun initializeDB(ctx: EContext)

    fun persistCredential(ctx: TxEContext, opIndex: Int, data: CredentialData)

    fun updateCredential(ctx: BlockEContext, id: ByteArray, signCount: Long, backupState: Boolean)

    fun deleteCredential(ctx: BlockEContext, id: ByteArray)

    fun fetchCredential(ctx: EContext, id: ByteArray): CredentialData?
}

sealed interface Entity {
    companion object {
        fun fromGtv(gtv: Gtv): Entity = when (val type = gtv.asDict()["type"]?.asInteger()) {
            CredentialData.TYPE -> CredentialData.fromGtv(gtv)
            else -> throw UserMistake("Unrecognized type: $type")
        }
    }
}

data class CredentialData(
        val type: Long = TYPE,
        val id: WrappedByteArray,
        val deleted: Boolean = false,
        val txRid: WrappedByteArray? = null,
        val opIndex: Long? = null,
        val alg: Long,
        val publicKey: WrappedByteArray,
        val signCount: Long,
        val transports: String,
        val uvInitialized: Boolean,
        val backupEligible: Boolean,
        val backupState: Boolean,
) : Entity {
    companion object {
        const val TYPE = 1L
        fun fromGtv(data: Gtv): CredentialData = GtvObjectMapper.fromGtv(data, CredentialData::class)
    }

    fun toGtv(): Gtv = GtvObjectMapper.toGtvDictionary(this)
}
