# Keypairs on the chromia network

On the chromia network, different keypairs will have different meaning and responsibilities.
As a provider, you will typically handle two different types of keypairs. 
A keypair can be generated using `pmc keygen -s <path>`

## Provider Keypair

The provider keypair is *your* personal keypair. It will be used to *sign transactions*  and *proposals* on the 
network. This will later be connected to a wallet and thus be the target for your revenue stream. It is really 
important that this keypair is kept safe as it is not possible to recover this. Therefore, you should configure 
`pmc` to use this keypair:

```shell
$ pmc keygen -s .pmc/config # Generates a keypair that pmc will use when executes commands from this directory
$ pmc keygen -s ~/.pmc/config # Generates a keypair that pmc will use when commands are executed from this host (global config)
```

> **Note:** You will only generate this key once. We recommend setting this up on global level for convenience if 
> you will participate on several networks. Write down the privkey/mnemonic to make it recoverable

## Node Keypair

All nodes on the network will have its own unique keypair. This is used by the nodes consensus algorithm to sign 
blocks between its peers. Generate one keypair for each node you will start:

```shell
$ pmc keygen -s n0.private.properties
$ pmc keygen -s n1.private.properties
etc
```

and include them in your node-properties file
```properties
include=n0.private.properties
```
