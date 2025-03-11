package net.postchain.hybridcompute

import net.postchain.core.Shutdownable

interface HybridComputeEngine : Shutdownable {
    /**
     * Name of this engine.
     */
    val name: String

    /**
     * Performs a computation.
     *
     * This method should block until the computation is finished.
     *
     * @param input  input to the computation
     * @return result of the computation, including enough information to validate it later (possibly the full input).
     * @throws net.postchain.common.exception.UserMistake if computation failed
     */
    fun compute(input: ByteArray): ByteArray

    /**
     * Validates a previously performed computation.
     *
     * This method should block until the validation is finished.
     *
     * @param output  the return value from a previous call to `compute`
     *
     * @throws net.postchain.common.exception.UserMistake if not valid
     */
    fun validate(output: ByteArray)
}
