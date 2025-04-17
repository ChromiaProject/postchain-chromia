package net.postchain.hybridcompute

import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.gtv.Gtv

/**
 * Hybrid compute engine.
 *
 * Methods might be invoked on different threads.
 */
interface HybridComputeEngine : Shutdownable {
    /**
     * Name of this engine.
     */
    val name: String

    /**
     * Will be invoked directly after instantiation, before any other method is invoked.
     *
     * This method should only do basic parsing and validation of configuration and should finish quickly.
     * Any heavy or time-consuming initialization should be performed in the `load` method.
     */
    fun init(blockchainConfig: Gtv, blockchainRID: BlockchainRid)

    /**
     * Will be invoked at some point after `init`, before any other method is invoked.
     *
     * Any heavy or time-consuming initialization should be performed in this method,
     * and it should block until the initialization is finished.
     */
    fun load()

    /**
     * Performs a computation.
     *
     * This method should block until the computation is finished.
     *
     * @param input  input to the computation
     * @return result of the computation, including enough information to validate it later (possibly the full input).
     * @throws net.postchain.common.exception.UserMistake if computation failed
     */
    fun compute(input: Gtv): Gtv

    /**
     * Validates a previously performed computation.
     *
     * This method should block until the validation is finished.
     *
     * @param output  the return value from a previous invocation of `compute`
     *
     * @throws net.postchain.common.exception.UserMistake if not valid
     */
    fun validate(output: Gtv)
}
