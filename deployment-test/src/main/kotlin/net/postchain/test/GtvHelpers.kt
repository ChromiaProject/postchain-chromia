package net.postchain.test

import net.postchain.gtv.Gtv
import net.postchain.gtv.GtvFactory.gtv

fun Gtv.modify(dictPath: List<String>, modifier: (Gtv) -> Gtv): Gtv {
    return if (dictPath.isEmpty()) {
        modifier(this)
    } else if (!this.asDict().containsKey(dictPath[0])) {
        gtv(this.asDict() + mapOf(dictPath[0] to modifier(gtv(emptyMap()))))
    } else {
        gtv(this.asDict().mapValues { dictEntry ->
            if (dictEntry.key == dictPath[0]) {
                dictEntry.value.modify(dictPath.drop(1), modifier)
            } else {
                dictEntry.value
            }
        })
    }
}
