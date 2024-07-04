package net.postchain.nn

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.djl.inference.Predictor
import ai.djl.modality.nlp.generate.CausalLMOutput
import ai.djl.modality.nlp.generate.SearchConfig
import ai.djl.modality.nlp.generate.TextGenerator
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.repository.zoo.Criteria
import ai.djl.repository.zoo.ZooModel
import ai.djl.translate.DeferredTranslatorFactory
import java.io.Closeable


class NNModule() : Closeable {

    val model: ZooModel<NDList, CausalLMOutput>
    val predictor: Predictor<NDList, CausalLMOutput>
    val manager: NDManager
    val tokenizer: HuggingFaceTokenizer
    val config = SearchConfig()

    init {
        config.setMaxSeqLength(60)
        val url = "https://djl-misc.s3.amazonaws.com/test/models/gpt2/gpt2_pt.zip"
        val criteria: Criteria<NDList, CausalLMOutput> = Criteria.builder()
                .setTypes(NDList::class.java, CausalLMOutput::class.java)
                .optModelUrls(url)
                .optEngine("PyTorch")
                .optTranslatorFactory(DeferredTranslatorFactory())
                .build()

        //val clz = Class.forName("ai.djl.pytorch.engine.PtEngineProvider")
        model = criteria.loadModel()
        predictor = model.newPredictor()
        manager = model.ndManager.newSubManager()
        tokenizer = HuggingFaceTokenizer.newInstance("gpt2")
    }

    fun generateTextWithPyTorchGreedy(input: String): String {
        val generator = TextGenerator(predictor, "greedy", config)
        val verifier = TextGeneratorVerifier(predictor, "greedy", config)
        val encoding: Encoding = tokenizer.encode(input)
        val inputIds: LongArray = encoding.getIds()
        val inputIdArray: NDArray = manager.create(inputIds).expandDims(0)
        val output: NDArray = generator.generate(inputIdArray)
        verifier.verify(output)
        val outputIds: LongArray = output.toLongArray()
        return tokenizer.decode(outputIds)
    }

    override fun close() {
        tokenizer.close()
        manager.close()
        predictor.close()
        model.close()
    }

}