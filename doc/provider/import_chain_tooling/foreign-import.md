## Import Chain Tooling – Foreign Blockchain Import

1. Suppose we have two Directory1 networks testnet1 and testnet2. Let's call testnet2 a _foreign network_. We want to import a _foreign blockchain_ named `dapp1` from foreign testnet2 to container `C1` of testnet1. Importing consists of two steps:      
   1. Importing of blockchain configurations. At this step, a new blockchain in `IMPORTING` state will be added to the container, and all its configurations will be fetched from testnet2 and uploaded to testnet1. Note that every configuration should be voted on separately. To simplify this step, one can (i) import blockchain to a container with a deployer voter set consisting of a single provider or (ii) update the `threshold` parameter of the container deployer voter set to the value 1 (1 vote). Once the import is done, cluster governance can be strengthened by (i) adding more providers or (ii) setting the initial value of `threshold`. This step can be run multiple times, adjusting `--from-height / --up-to-height` options (might be needed if foreign blockchain gets new configurations). Also, make sure that `C1` signers and foreign cluster signers don't overlap. 
   2. Importing (synchronization) of blocks up to specified height `H`. At this step, a new configuration with all cluster signers will be proposed at a height `H + 1`. As soon as it is approved, the blockchain state will be changed from `IMPORTING` to `RUNNING`, and the blockchain will start block synchronization on all nodes of the cluster `C1`.

Let's go through all steps with an example.

2. Importing of blockchain configurations:
```shell
export DAPP=0DA9B560464A39CAB2E8CE6FEF877A568B517C7373DC24833BB025AB9879331E

pmc blockchain import-foreign-configurations \
    -pk 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9 \
    --host node1.devnet2.chromia.dev\
    --port 9870 \
    --api-url https://node1.devnet2.chromia.dev:7740 \
    --chain0-blockchain-rid 10A8310BBB872804654F0751E066617558816C19A5901D1CC6609EBF9F047ACE \
    -brid $DAPP \
    --name dapp \
    --from-height 0 \
    --up-to-height 1000 \
    --container c1
```

3. Importing (synchronization) of blocks:
```shell
pmc blockchain import-foreign-blocks -brid $DAPP3 --up-to-height 1100
```

4. _Monitoring of syncing and anchoring._ Syncing and anchoring might take a long time for big blockchains. To monitor the progress, one can use the following commands:   

```shell
# block synchronization
curl -X GET https://node0.devnet1.chromia.dev:7740/blockchain/$DAPP/height

# anchoring, where CAC is a blockchain RID of the cluster anchoring chain
export CAC=33D4511A8CDD4AF0C0D30A36F0AF8FB40FDC50CF25F507865AE23E32DDFC8E10
docker run --rm \
    registry.gitlab.com/chromaway/core-tools/chromia-cli/chr:0.9.1 \
    query --api-url https://node0.devnet2.chromia.dev:7740/ \
    --blockchain-rid "$CAC" \
    get_last_anchored_block -- blockchain_rid="$DAPP" \
    | grep -o 'block_height=[0-9]*'
```
