package net.postchain.hybridcompute

import net.postchain.common.BlockchainRid
import net.postchain.core.Shutdownable
import net.postchain.gtv.Gtv

/**
 * Hybrid compute engine.
 */
interface HybridComputeEngine : Shutdownable {
    /**
     * Name of this engine.
     */
    val name: String

    /**
     * Will be invoked after instantiation, before any other method is invoked.
     */
    fun init(blockchainConfig: Gtv, blockchainRID: BlockchainRid)

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
