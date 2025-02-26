package net.postchain.d1.nn

import net.postchain.core.EContext
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.gtxOP
import net.postchain.nn.DJLTextModel
import org.junit.Assert.assertTrue
import org.junit.Test


class NNTestModule: SimpleGTXModule<Unit>(Unit,
        mapOf(
                "__nn_take_request" to gtxOP { _ -> true },
                "__nn_response" to gtxOP { _ -> true },
        ),
        mapOf(
                "nn.get_requests" to { _, ctxt, args -> gtv(0) }
        )
) {
    override fun initializeDB(ctx: EContext) {}
}


class NNTest {
    @Test
    fun testNN() {
        val module = NNTestModule()
        val DJLTextModel = DJLTextModel()

        try {
            val input = "Confessions: I"
            val generatedText: String = DJLTextModel.generateText(input)
            println("Generated text: $generatedText")
            assertTrue("Generated text should not be empty", generatedText.isNotEmpty())
            val isVerified = DJLTextModel.verifyTextGeneration(generatedText, input)
            assertTrue("Verification should succeed for the generated text", isVerified)
        } finally {
            DJLTextModel.close()
        }


    }
}