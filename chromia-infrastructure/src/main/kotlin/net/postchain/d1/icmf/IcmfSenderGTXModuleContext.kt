package net.postchain.d1.icmf

class IcmfSenderGTXModuleContext {
    val dbOperations: IcmfDatabaseOperations = IcmfDatabaseOperationsImpl()
    var isSystemChain = false
}
