# Single provider setup

In this example we will use a single provider setup to manage the configuration updates of a dapp. This is useful when developing a dapp locally.

## Initialization

Start a node using:

```shell
$ pmc node start --node-config config/config.0.properties --blockchain-config out/0.xml --name postchain0 --port 50050 --debug
```

This will start a node using docker with maintenance port 50050. This port can be used to send rpc requests directly to the node. In this guide we will only communicate with the node through the rest api configured on port 7740 as defined in `config.0.properties`.

Check that your node is running

```shell
docker ps --filter name=postchain0
```

Then, initialize the one-node-network

```shell
$ pmc network initialize --host $(find-ip) --port 9870
```

The host and port used is the external ip and port that other nodes can communicate with the initial node. In this example, we only use a single node, so this can be set to any non-null values, but it is better to use real values if more nodes are added at a later stage.

Check that the network is indeed initialized 

```shell
pmc network summary
```

This command should show non-zero values.

You will now have a `system` cluster running on a single node and a `system` container:

```shell
$ pmc clusters                    // list all clusters
$ pmc cluster info --name system  // Show information about cluster
$ pmc containers                  // list all conatiners
```

## Launch a blockchain

You can deploy your dapp directly to the system container, but in this tutorial we will create a new container to contain resource usage of the dapp.

Create a new container

```shell
$ pmc container add --name cities --cluster system --pubkeys $(pmc config --get pubkey)
```

and see that is was added using

```shell
$ pmc containers
```

Launch a new blockchain using the `pmc blockchain add` command. A test blockchain configuration should be found in `app-out` folder after running the setup script. 

```shell
$ pmc blockchain add --name city_tracker --container cities --blockchain-config app-out/0.xml
```

> **Note**: The output will now say "Blockchain city_tracker has been proposed", but since you are the only provider in the network, you have indeed also added it.

Check that your blockchain is running

```shell
$ pmc blockchains
```

## Updating a blockchain

Say that you are developing your dapp and wants to add a query "get_hometown" to the city tracker while the app is running. First, make sure this query does not exist by attempting to make this query.
Find the blockchain rid of the dapp and create a new pmc config. We create one that points to the dapp chain `client.properties` and one that points to BC0.

```shell
$ cp -r provider/alpha/.pmc/config app/config/client.properties
$ cp -r provider/alpha/.pmc app/.pmc
$ cd app
app$ pmc config --file config/client.properties --set brid=$(pmc blockchain list | grep city_tracker | awk '{ print $3 }')
```

and make the query towards the city tracker app and see how it fails with a `400 Bad Request Unknown query: get_hometown`

```shell
app$ client query --config .pmc/config get_hometown
```

Then, add the query to the dapp (`app/main.rell`) and recompile the dapp

```shell
app$ echo 'query get_hometown() = "Lulea";' >> src/main.rell
app$ chromia-deploy.sh compile --source-dir src --output-dir build config/deploy.xml
```

You can now update the dapp using pmc

```shell
app$ pmc blockchain update -bc build/0.xml --blockchain-rid $(pmc config --file config/client.properties --get brid)
```

Now, the new query should be accessible from the dapp chain

```shell
app$ client.sh query --config config/client.properties get_hometown
Query get_hometown returned
"Lulea"
```

> **Note**: You can also use  `chromia-deploy.sh deploy` to compile and publish the configuration in one go. Then you don't have to specify the brid each time since it can be included in the deploy.xml
