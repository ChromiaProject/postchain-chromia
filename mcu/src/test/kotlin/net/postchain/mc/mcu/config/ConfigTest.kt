package net.postchain.mc.mcu.config

import net.postchain.mc.mcu.ResourceReader
import org.junit.Test

class ConfigTest {

    @Test
    fun test() {
        val json = ResourceReader.read("/config/config.cfg")
        val config = Config.fromJson(json)

        println(Config.toJson(config))
//        println(config)
    }

}
