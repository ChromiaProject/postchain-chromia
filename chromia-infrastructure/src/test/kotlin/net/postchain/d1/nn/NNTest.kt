package net.postchain.d1.nn

import net.postchain.core.EContext
import net.postchain.gtv.GtvFactory.gtv
import net.postchain.gtx.SimpleGTXModule
import net.postchain.gtx.gtxOP
import net.postchain.nn.NNModule
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
        val nnModule = NNModule()
        println(nnModule.generateTextWithPyTorchGreedy("Hello fellow kids!"))
    }
}