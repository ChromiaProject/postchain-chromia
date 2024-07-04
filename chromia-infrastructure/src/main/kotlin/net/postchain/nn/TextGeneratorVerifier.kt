package net.postchain.nn

import ai.djl.inference.Predictor
import ai.djl.modality.nlp.generate.CausalLMOutput
import ai.djl.modality.nlp.generate.SearchConfig
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDScope
import ai.djl.ndarray.index.NDIndex
import ai.djl.ndarray.types.DataType
import ai.djl.ndarray.types.Shape
import java.util.Arrays

/**
 * `TextGenerator` is an LMSearch (language model search) which contains multiple
 * autoregressive search methods.
 *
 *
 * It has a Predictor from NDList to CausalLMOutput, which is called inside an autoregressive
 * inference loop.
 */
class TextGeneratorVerifier(val predictor: Predictor<NDList, CausalLMOutput>?,
                            val searchName: String?,
                            val config: SearchConfig?) {
    /**
     * Returns the value of the positionOffset.
     *
     * @return the value of positionOffset
     */
    var positionOffset: NDArray? = null
        private set

    fun verify(inputIds: NDArray): Boolean {
        // Initialize the end position of each sentence
        val endPosition = LongArray(Math.toIntExact(inputIds.shape[0]))
        Arrays.fill(endPosition, config!!.maxSeqLength.toLong())

        val attentionMask = prepareAttentionMaskOffset(inputIds, config)
        val manager = inputIds.manager
        while (true) {
            NDScope().use { ignore ->
                var pastOutputIds = null
                var nextInputIds = inputIds
                var pastAttentionMask = attentionMask
                var pastKeyValues = null
                val pastSeqLength = 0L
                val modelInput = prepareInput(nextInputIds, pastAttentionMask, pastSeqLength, 1)

                val modelOutput = predictor!!.predict(modelInput)
                val outputIds = modelOutput.logits.argMax(2)

                println(Arrays.toString(inputIds.toLongArray()))
                println(Arrays.toString(outputIds.toLongArray()))
                println("HOGOO")
            }
        }
    }

    private fun prepareAttentionMaskOffset(inputIds: NDArray, config: SearchConfig?): NDArray {
        // prepare attentionMask and positionOffset
        // Used to initialize the search
        val suffixPadding = config!!.isSuffixPadding
        val manager = inputIds.manager
        val numBatch = Math.toIntExact(inputIds.shape[0])
        val initSeqSize = Math.toIntExact(inputIds.shape[1])
        val attentionMask =
                manager.ones(Shape(1, inputIds.shape.lastDimension), DataType.INT64)
                        .reshape(1, -1)
                        .repeat(0, numBatch.toLong())

        // Linear search from left to find the first position that's not padTokenId.
        val offset = Array(numBatch) { LongArray(1) }
        for (i in 0 until numBatch) {
            val aSequence = inputIds["{},:", i].toLongArray()
            var idx = 0
            while (idx < initSeqSize) {
                if (suffixPadding && aSequence[idx] == config.padTokenId
                        || !suffixPadding && aSequence[idx] != config.padTokenId) {
                    break
                }
                idx++
            }
            attentionMask[NDIndex(
                    "{},{}:{}",
                    i,
                    if (suffixPadding) idx else 0,
                    if (suffixPadding) initSeqSize else idx)] = 0
            if (!suffixPadding) {
                offset[i][0] = idx.toLong()
            }
        }
        positionOffset = manager.create(offset)
        return attentionMask
    }

    private fun prepareInput(
            inputIds: NDArray, attentionMask: NDArray, pastSeqLength: Long, repeat: Int): NDList {
        // Pack the model input
        var positionIds =
                inputIds.manager
                        .arange(
                                pastSeqLength.toFloat(),
                                (pastSeqLength + inputIds.shape.lastDimension).toFloat(),
                                1f,
                                DataType.INT64)
                        .expandDims(0)
                        .repeat(0, inputIds.shape[0])

        val positionIdsShifted = positionIds.subi(positionOffset!!.repeat(0, repeat.toLong()))
        positionIds = positionIdsShifted.maximum(positionIdsShifted.zerosLike())

        return NDList(inputIds, positionIds, attentionMask)
    }


}
