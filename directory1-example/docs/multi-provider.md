# Setting up and managing multiple providers

In this example we will set up and use a few nodes and providers with different roles.

The providers are called `alpha`, `beta`, `gamma` and `delta` and their configurations are found in the `provider` folder. `alpha$` means the command is executed from `provider/alpha` folder.

The goal is to illustrate the different roles a provider can have on the network.

```
alpha - system provider and owner of node 0
beta  - system provider and owner of node 1
gamma - node provider and owner of node 2
delta - dapp provider/community node provider who does not own a node
```

## Initializing the network

Start the genesis node and two other nodes

```shell
$ pmc node start -nc config/config.0.properties -bc out/manager.xml --name postchain0 --port 50050 --debug
$ pmc node start -nc config/config.1.properties -bc out/manager.xml --name postchain1 --port 50051 --debug --genesis-pubkey 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57 --genesis-peer $(find-ip):9870
$ pmc node start -nc config/config.2.properties -bc out/manager.xml --name postchain2 --port 50052 --debug --genesis-pubkey 0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57 --genesis-peer $(find-ip):9870
```

Alpha will then initialize the network on node 0
```shell
alpha$ pmc network initialize
```

Verify that the network is initialized by noting non-zero values from
```shell
alpha$ pmc network summary
```

## Inviting providers

Alpha then invites Beta and Gamma as node providers

```shell
alpha$ pmc provider add --pubkey $(pmc config --get pubkey --file ../beta/.pmc/config) -sp
alpha$ pmc provider add --pubkey $(pmc config --get pubkey --file ../gamma/.pmc/config) -np
alpha$ pmc provider add --pubkey $(pmc config --get pubkey --file ../delta/.pmc/config) -cnp
```

> **Note**: When adding a provider, it is only enabled if it fullfills certain conditions.
> - SP enables NP and CNP
> - NP enables CNP
> - SP *proposes* SP
> 
> In this case, Alpha is the only SP which makes Beta enabled immediately. If a seconds SP is added, both Alpha and Beta must reach consensus in a voting.

Verify the states of the providers

```shell
alpha$ pmc providers
```

## Adding a node to the system cluster

As a newly joined system provider, Beta has the right (and should) to add a node to the system cluster. This is done by registering the node to the network

```shell
beta$ pmc config --set api.url=http://localhost:7740 # points to the rest api of node 0
beta$ pmc node add --pubkey 035676109c54b9a16d271abeb4954316a40a32bcce023ac14c8e26e958aa68fba9 --host $(find-ip) --port 9871 --api-url http://localhost:7741 --cluster system
beta$ pmc config --set api.url=http://localhost:7741 # Change back to point to node 1
```

Verify that the node is indeed added to the cluster (may take more than 10 seconds since this has a delay of 5 blocks)

```shell
beta$ pmc cluster info --name system
```

## Promoting a System Provider through voting

Now that we have two system providers, we are going to promote Gamma to also become a system provider through voting.

### Making a proposal

A system provider can do some operations directly on the network, such as creating a cluster, but some requires consensus. Promoting a NP to SP is one of them

```shell
beta$ pmc provider promote --pubkey $(pmc config --get pubkey --file ../gamma/.pmc/config) --system
```

Verify that gamma is still not a system provider

```shell
beta$ pmc provider info --pubkey $(pmc config --get pubkey --file ../gamma/.pmc/config)
```

### Voting

Alpha must now vote on the proposal to accept it. Check which proposals are present and show more information about it

```shell
alpha$ pmc proposals
alpha$ pmc proposal info --verbose # shows latest proposal, use --id to see info about a specific
```

Alpha can now vote on the proposal

```shell
alpha$ pmc proposal vote --id <id> --accept # or --reject
```

Verify that gamma has been promoted to system and let gamma add its node:

```shell
alpha$ pmc provider info --pubkey $(pmc config --get pubkey --file ../gamma/.pmc/config)
gamma$ pmc config --set api.url=http://localhost:7740 # points to the rest api of node 0
gamma$ pmc node add --pubkey 03f811d3e806e6d093a4bcce49c145ba78f9a4b2fbd167753ecab2a13530b081f8 --host $(find-ip) --port 9872 --api-url http://localhost:7742 --cluster system
gamma$ pmc config --set api.url=http://localhost:7742 # Change back to point to node 1
```

All users should now see that the system cluster has three nodes and providers

```shell
gamma$ pmc cluster info --name system
```
