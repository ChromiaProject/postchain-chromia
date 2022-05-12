package net.postchain.images.directory1

import net.postchain.dapp.PostchainContainer
import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory
import java.io.File
import java.nio.file.Files

internal fun PostchainContainer.proposeChain0(provider: Gtv) {
    val containerConfig0 = "${envMap["RELL_OUT"] ?: "${PostchainContainer.RELL_PATH}/out"}/blockchains/0/0.gtv"
    val hostConfig0 = Files.createTempDirectory("").toAbsolutePath().toString() + "0.gtv"
    copyFileFromContainer(containerConfig0, hostConfig0)
    val configGtv = GtvFactory.gtv(File(hostConfig0).readBytes())

    val containerGtv = client(0).query(
            "get_container", GtvFactory.gtv("name" to GtvFactory.gtv("system"))).get()

    tx(0, "propose_blockchain", provider, configGtv, containerGtv)
}
