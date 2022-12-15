// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

interface IcmfReceiver<out RT : Route, PtrT, PktT, IdT> {
    fun getRelevantPipes(): List<IcmfPipe<RT, PtrT, PktT, IdT>>
}
