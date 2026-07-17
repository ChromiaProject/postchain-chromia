package net.postchain.d1

import net.postchain.core.EContext
import net.postchain.core.TxEContext
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.GTXOperation
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.data.ExtOpData

/**
 * Test-only module that lets a *system* chain (in practice the directory chain, chain0) emit an
 * arbitrary ICMF message.
 *
 * Used by [net.postchain.images.directory1.Directory1DeadlockIT] to build a live intra-cluster
 * ANCHORED ICMF backlog (topic `G_deadlock_test`). The backlog must be a GLOBAL (`G_`) topic: the
 * cluster anchoring chain's `process_icmf` indexes only `G_*` topics into `icmf_messages_height`
 * (local topics are delivered directly, never via the anchored path). chain0 emits it because `G_`
 * topics are only accepted from system chains (`IcmfBlockBuilderExtension`) and the test deploys
 * none of the usual prod G_ senders (economy chain; anchoring chains); chain0 itself emits only
 * local topics in prod.
 *
 * The CAC consumes the topic through a TEST-ONLY `global` receiver in directory1.yml (real CACs
 * have no global receiver — deliberate divergence, documented there). Without the backlog and that
 * receiver the anchoring chain has nothing to fetch at the migration block, the fatal self-read
 * never executes, and the deadlock test is a false green (verified: passes on unfixed 3.49.16
 * without the receiver). With them, the anchoring chain's first block build after a
 * schema-migrating config update issues the anchored-ICMF self-read against its own — exclusively
 * locked by [ExclusiveTableLockTestGTXModule] — `anchor_block` / `icmf_messages_height` tables,
 * reproducing the config-migration deadlock (hangs on 3.49.16, passes on 3.49.18).
 *
 * Operation: `emit_global_icmf(provider_pubkey: byte_array, topic: text, body: gtv)`.
 *
 * The leading `provider_pubkey` arg is required only by the directory chain's transaction
 * prioritization (`dc_priority_check`), which rejects any regular operation whose `args[0]` is not a
 * registered provider pubkey that also signed the tx. The operation body itself ignores it.
 */
class GlobalIcmfEmitterTestGTXModule : SimpleGTXModule<Unit>(
        Unit,
        mapOf(OP_EMIT_GLOBAL_ICMF to { conf, opData: ExtOpData -> EmitGlobalIcmfOp(conf, opData) }),
        mapOf()
) {
    companion object {
        const val OP_EMIT_GLOBAL_ICMF = "emit_global_icmf"

        // Mirrors net.postchain.d1.icmf.ICMF_MESSAGE_TYPE. Kept as a literal to avoid pulling a
        // chromia-infrastructure dependency into chromia-devtools.
        const val ICMF_MESSAGE_TYPE = "icmf_message"
    }

    override fun initializeDB(ctx: EContext) {}
}

class EmitGlobalIcmfOp(private val conf: Unit, extOpData: ExtOpData) : GTXOperation(extOpData) {
    override fun checkCorrectness() {
        require(data.args.size >= 3) { "${GlobalIcmfEmitterTestGTXModule.OP_EMIT_GLOBAL_ICMF} requires (provider_pubkey, topic, body)" }
    }

    override fun apply(ctx: TxEContext): Boolean {
        // args[0] is the provider pubkey required by dc_priority_check; ignored here.
        val topic = data.args[1].asString()
        val body = data.args[2]
        // SentIcmfMessage.fromGtv expects {topic, body, receiver?}; omit receiver for a global broadcast.
        ctx.emitEvent(GlobalIcmfEmitterTestGTXModule.ICMF_MESSAGE_TYPE, gtv(mapOf("topic" to gtv(topic), "body" to body)))
        return true
    }
}
