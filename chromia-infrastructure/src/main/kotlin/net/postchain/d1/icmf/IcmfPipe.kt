// Copyright (c) 2022 ChromaWay AB. See README for license information.

package net.postchain.d1.icmf

import net.postchain.core.BlockEContext
import net.postchain.core.Shutdownable

interface IcmfPipe<out RT : Route, PtrT, PktT, IdT> : Shutdownable {
    val route: RT
    val id: IdT

    fun mightHaveNewPackets(): Boolean

    /**
     * Fetches next packets with pointer greater than currentPointer
     */
    fun fetchNext(currentPointer: PtrT): IcmfPackets<PtrT, PktT>?
    fun markTaken(currentPointer: PtrT, bctx: BlockEContext)
}
