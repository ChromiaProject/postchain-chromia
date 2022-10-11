# Setup

Setting up a new network consist of the following steps:

1. Starting the genesis (initial) node
2. Configure the Management console
3. Initializing the network
4. Add provider N+1
5. Add node N+1

## Starting the genesis node

See [Start a node](start-a-node.md) for instructions on how to start the first node in the network

## Configure the Management Console

If the management console has never been used, you can use `pmc setup --global` to create a new keypair and configuration file.
Otherwise use `pmc config --global` to edit the global configuration.
Your configuration must contain at least a keypair, api.url and brid properties.
Example:
```properties
privkey=BBBDFE956021912512E14BB081B27A35A0EABC4098CB687E973C434006BCE114
pubkey=03ECD350EEBC617CBBFBEF0A1B7AE553A748021FD65C7C50C5ABB4CA16D4EA5B05
brid=CC5263403D9A07928842D9B8BF67A5C4B7B8A7CAE1BDE35E49BFB4BEF28E420F
api.url=http://localhost:7740
```
The `brid` is found when you started the node in the step before, and the api.url must point to the rest-api of that node.


## Initializing the network

Find your external ip address. On unix machines, the following command can be used:
```shell
ifconfig | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}'
```

Now you can initialize the network using:
```shell
pmc network initialize --host <ip> --port <node-messaging-port>
```

## Add provider N+1

Adding a new provider can be done using
```shell
pmc provider add --pubkey <pubkey> --tier <tier>
```

For a system provider, tier 1 must be used.

Provider 1 can now add the new provider to the system voter set and system cluster:
```shell
pmc voterset update --name SYSTEM_P --pubkey <pubkey> --add
pmc cluster provider --pubkey <pubkey> --add
```

## Add node N+1


The newly added provider can now start their node by configuring its genesis node to be node 1. See [Start a Node](start-a-node.md) for more information.
They can then add the node to the system cluster by making the following commands towards node 1:
```shell
pmc node add --pubkey <node-pubkey> --host <ip> --port <node-messaging-port> --api-url <api-url> --cluster system
```
