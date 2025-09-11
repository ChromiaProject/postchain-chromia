# DApp developer guide

This is documentation for dApp developers that want to use ICCF.

## Configuration
You will need to include the ICCF GTX module in your blockchain configuration:
```
config:
  gtx:
    modules:
      - "net.postchain.d1.iccf.IccfGTXModule"
```

## Rell code
See instructions on how to install the [ICCF library](https://gitlab.com/chromaway/core/directory-chain/-/blob/1.97.1/src/lib/iccf/module.rell) here: https://gitlab.com/chromaway/core/directory-chain

### Why would I not allow intra cluster proofs?
The intra cluster proofs are an optimization that comes with a drawback. Replica nodes won't be able to verify that the transaction to prove has been anchored in the cluster anchoring chain. Signers will, of course, still verify this. If you as a dApp developer think that this trade-off is not acceptable, you can opt out from using them.

## DApp example repository
https://gitlab.com/chromaway/example-projects/iccf-example
