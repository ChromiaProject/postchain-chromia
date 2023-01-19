package net.postchain.mc.compat

import net.postchain.chain0.common.Codename
import net.postchain.client.transaction.TransactionBuilder
import net.postchain.mc.network.Version

fun TransactionBuilder.delta(version: Version, op: TransactionBuilder.() -> Unit) {
    if (version.version.codename == Codename.Delta) {
        this.op()
    }
}

fun TransactionBuilder.sigma(version: Version, op: TransactionBuilder.() -> Unit) {
    if (version.version.codename == Codename.Sigma) {
        this.op()
    }
}