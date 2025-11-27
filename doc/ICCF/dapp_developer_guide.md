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

To include the ICCF module in your rell project, specify in your config file:
```yaml
libs:
  com.chromia.iccf:
    version: 1.90.2
```

Then use `chr install` to install the ICCF library.

The above version requires Rell version >= 0.14.5. If you need to use a lower Rell version then install:

```yaml
libs:
  iccf:
    registry: https://gitlab.com/chromaway/core/directory-chain
    path: src/lib/iccf
    tagOrBranch: 1.66.1
    rid: x"23BB9A9B967EED88642E08FFA336D7E35A6A49BFA7E86776FFBDCDA047D018F4"
```

**Note**: With this version it's not possible to verify transactions on chains that is using merkle hash version 2.

### Why would I not allow intra cluster proofs?
The intra cluster proofs are an optimization that comes with a drawback. Replica nodes won't be able to verify that the transaction to prove has been anchored in the cluster anchoring chain. Signers will, of course, still verify this. If you as a dApp developer think that this trade-off is not acceptable, you can opt out from using them.

## DApp example repository
https://gitlab.com/chromaway/example-projects/iccf-example
