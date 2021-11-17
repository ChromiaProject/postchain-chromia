package net.postchain.mc.mcu

import net.postchain.mc.mcu.config.Config
import net.postchain.mc.mcu.ps.Postchain

class Context(
    val config: Config,
    val postchain: Postchain
)