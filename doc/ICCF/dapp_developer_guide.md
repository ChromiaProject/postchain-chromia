# DApp developer guide

This is documentation for dApp developers that want to use ICCF.

## Configuration
You will need to include the ICCF GTX module into your blockchain configuration:
```
config:
  gtx:
    modules:
      - "net.postchain.d1.iccf.IccfGTXModule"
```

## Rell code
See instructions on how to install the ICCF library here: https://gitlab.com/chromaway/core/directory-chain

The library has only one function:

`check_iccf_proof(blockchain_rid: byte_array, tx_hash: byte_array, require_intra_network_iccf_op: boolean = false): boolean`

The parameters are the blockchain RID and transaction hash of the transaction that needs to be proven. There is also an optional parameter to exclude optimized intra cluster proofs. If the function returns true it means we can trust that this transaction has occurred on the specified blockchain.

### Why would I not allow intra cluster proofs?
The intra cluster proofs are an optimization that comes with a drawback. Replica nodes wont be able to verify that the transaction to prove has been anchored in the cluster anchoring chain. Signers will of course still verify this. If you as a dApp developer think that this trade-off is not acceptable you can opt out from using them.

## DApp example repository
https://gitlab.com/chromaway/example-projects/iccf-example
