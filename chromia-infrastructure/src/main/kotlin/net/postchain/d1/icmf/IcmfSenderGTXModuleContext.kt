package net.postchain.d1.icmf

class IcmfSenderGTXModuleContext {
    val dbOperations: IcmfDatabaseOperations = IcmfDatabaseOperationsImpl()
    var isSystemChain = false
    var messageQueryLimit: Int = DEFAULT_MESSAGE_QUERY_LIMIT
}
