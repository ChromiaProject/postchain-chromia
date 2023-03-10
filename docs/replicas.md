# Replicas

### Standalone replica node in manual mode

Suppose we have a Directory1 network that runs a node and a blockchain we want to replicate on another standalone node that operates in manual mode.

```properties
# Directory1 node
pubkey=0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57
host=myhost
port=9870
# blockchain to replicate:
brid=1FEE4B2B2A0AD08DA4B3B110CCCFF38FDA2C32EA5AF24DE7C1A96376663AC148
```
To achieve this, do the following steps.
1. Check out [postchain-chromia](https://gitlab.com/chromaway/postchain-chromia)
2. Run `mvn clean package -DskipTest` and use `chromia-node` under `chromia-dist/target/chromia-dist-<version>-dist` folder
3. Create new folder called `run-replica` or whatever name that you want.
4. Copy `chromia-node` at step#2 under new folder `run-replica`
5. Prepare a `node-config.properties` and `key.properties` file and put it to `run-replica` dir

- node-config.properties
```properties
# Keys
include=keys.properties
# Configurations
configuration.provider.node=manual
infrastructure=base-ebft
# Storage
database.url=jdbc:postgresql://localhost:5432/postchain
database.username=postchain
database.password=postchain
database.schema=replica_test
# API
api.port=7740
# fastsync
fastsync.exit_delay=0
```

- key.properties
 ```properties
messaging.privkey=<private-key>
messaging.pubkey=<public-key>
```

6. Go to `run-replica` folder
7. Add Directory1 node as a peer to the replica node 

```shell
./chromia-node/postchain.sh peerinfo-add \
    -nc ./target/node-config.properties \
    -h myhost \
    -p 9870 \
    -pk 0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57
```

8. Add blockchain replica to the replica node

```shell
./chromia-node/postchain.sh blockchain-replica-add \
    -nc ./target/node-config.properties \
    -brid 1FEE4B2B2A0AD08DA4B3B110CCCFF38FDA2C32EA5AF24DE7C1A96376663AC148 \
    -pk 0350FE40766BC0CE8D08B3F5B810E49A8352FDD458606BD5FAFE5ACDCDC8FF3F57 
```

9. Get the initial blockchain config and dump it to the file `run-replica/blockchains/123/0.xml` (123 is a chainId).

```shell
pmc blockchain get -brid 1FEE4B2B2A0AD08DA4B3B110CCCFF38FDA2C32EA5AF24DE7C1A96376663AC148 -h 0 --save 0.xml
```

10. Add `chainId to brid` mapping to the `node-config.properties` file:

```properties
brid.chainid.123=1FEE4B2B2A0AD08DA4B3B110CCCFF38FDA2C32EA5AF24DE7C1A96376663AC148
```

11. Run `run-node-auto` command. Replica node will start replicating the blockchain.

```shell
./chromia-node/postchain.sh run-node-auto -d ./target
```


### Directory1 replica node

To be continued ...

