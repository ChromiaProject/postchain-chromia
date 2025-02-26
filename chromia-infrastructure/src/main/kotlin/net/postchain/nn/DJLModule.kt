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

class DJLTextModel() : Closeable {

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

        model = criteria.loadModel()
        predictor = model.newPredictor()
        manager = model.ndManager.newSubManager()
        tokenizer = HuggingFaceTokenizer.newInstance("gpt2")
    }

    /**
     * Generates a text string using PyTorch with greedy search.
     */
    fun generateText(input: String): String {
        val generator = TextGenerator(predictor, "greedy", config)
        val encoding: Encoding = tokenizer.encode(input)
        val inputIds: LongArray = encoding.getIds()
        val inputIdArray: NDArray = manager.create(inputIds).expandDims(0)
        val output: NDArray = generator.generate(inputIdArray)
        return tokenizer.decode(output.toLongArray())
    }

    /**
     * Verifies a generated text by re-encoding it into token IDs and verifying via the verifier,
     * excluding the prompt tokens from verification.
     * 
     * @param generatedText The complete generated text (including prompt)
     * @param prompt The prompt text that was used to generate the text
     * @return True if the model predictions match the generated tokens (excluding prompt tokens)
     */
    fun verifyTextGeneration(generatedText: String, prompt: String): Boolean {
        val encoding: Encoding = tokenizer.encode(generatedText)
        val outputIds: LongArray = encoding.getIds()
        val outputIdArray: NDArray = manager.create(outputIds).expandDims(0)
        val verifier = TextGeneratorVerifier(predictor, "greedy", config, tokenizer)
        return verifier.verify(outputIdArray, prompt)
    }

    override fun close() {
        tokenizer.close()
        manager.close()
        predictor.close()
        model.close()
    }
}
