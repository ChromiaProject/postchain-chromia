package net.postchain.d1.icmf

import net.postchain.gtx.SnapshotContext

class IcmfSenderGTXModuleContext {
    val dbOperations: IcmfSenderDatabaseOperations = IcmfSenderDatabaseOperationsImpl()
    var isSystemChain = false
    var messageQueryLimit: Int = DEFAULT_MESSAGE_QUERY_LIMIT
    var snapshotContext: SnapshotContext? = null
}
