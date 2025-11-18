package net.postchain.hybridcompute

import net.postchain.gtv.Gtv

/**
 * Hybrid compute engine.
 *
 * Methods might be invoked on different threads.
 *
 * If the implementation also implements `net.postchain.gtx.PostchainContextAware`,
 * its `initializeContext` method will be invoked directly after instantiation, before any other method is invoked.
 * That method should only do basic parsing and validation of configuration and should finish quickly.
 * Any heavy or time-consuming initialization should be performed in the `load` method.
 *
 * If the implementation also implements `net.postchain.core.Shutdownable`,
 * its `shutdown` method will be invoked before the instance is released.
 */
interface HybridComputeEngine {
    /**
     * Name of this engine.
     */
    val name: String

    /**
     * Will be invoked at some point after `initializeContext`, before any other method is invoked.
     *
     * Any heavy or time-consuming initialization should be performed in this method,
     * and it should block until the initialization is finished.
     */
    fun load()

    /**
     * Estimates the number of rate limit points required for the given computation input.
     *
     * This method will be invoked before `compute` if rate limiting is enabled.
     * This method should finish quickly. If this method throws [net.postchain.common.exception.UserMistake],
     * the computation will not be performed.
     *
     * @param input  input to the computation
     * @return the estimated number of rate limit points
     */
    fun estimatePoints(input: Gtv): Long

    /**
     * Performs a computation.
     *
     * This method should block until the computation is finished.
     *
     * @param input  input to the computation
     * @return result of the computation, including enough information to validate it later,
     *         and the actual number of rate limit points consumed by the computation
     * @throws net.postchain.common.exception.UserMistake if computation failed
     */
    fun compute(input: Gtv): Pair<Gtv, Long>

    /**
     * Validates a previously performed computation.
     *
     * This method should block until the validation is finished.
     *
     * @param input   input to the computation
     * @param output  the return value from a previous invocation of `compute`
     *
     * @throws net.postchain.common.exception.UserMistake if not valid
     */
    fun validate(input: Gtv, output: Gtv)
}
