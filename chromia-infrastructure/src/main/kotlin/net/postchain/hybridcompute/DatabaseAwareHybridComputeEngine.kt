package net.postchain.hybridcompute

import net.postchain.core.BlockEContext
import net.postchain.core.EContext
import net.postchain.gtv.Gtv

/**
 * Database-aware hybrid compute engine.
 *
 * Methods might be invoked on different threads. All methods except `load()` and `validate()` might be invoked
 * concurrently with others and themselves, so they need to be thread-safe.
 */
interface DatabaseAwareHybridComputeEngine : HybridComputeEngine {
    /**
     * Will be invoked at some point after `initializeContext`, before any other method is invoked.
     *
     * @param ctx    read-only database connection
     *
     * Any heavy or time-consuming initialization should be performed in this method.
     * This method is executed asynchronously and should block until the initialization is finished. Should honor
     * [thread interruption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#interrupt()).
     */
    fun load(ctx: EContext)

    /**
     * Performs a computation with database access.
     *
     * This method is executed asynchronously and should block until the computation is finished. Should honor
     * [thread interruption](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#interrupt()).
     *
     * @param ctx    read-only database connection
     * @param input  input to the computation
     * @return result of the computation, including enough information to validate it later,
     *         and the actual number of rate limit points consumed by the computation
     * @throws net.postchain.common.exception.UserMistake if computation failed
     */
    fun compute(ctx: EContext, input: Gtv): Pair<Gtv, Long>

    /**
     * Validates a previously performed computation with database access.
     *
     * This method is executed synchronously on the block builder thread and needs to have some kind of timeout or other
     * safeguard to avoid blocking indefinitely.
     *
     * @param bctx    block context with read-only database connection
     * @param input   input to the computation
     * @param output  the return value from a previous invocation of `compute`
     *
     * @throws net.postchain.common.exception.UserMistake if not valid
     */
    fun validate(bctx: BlockEContext, input: Gtv, output: Gtv)

}
