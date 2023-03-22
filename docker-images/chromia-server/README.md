# Chromia-server

This image is used to run postchain in a container, with the default behavior of acting as a server.
A postchain running as a server accepts messages over rpc protocol to interact with it. We recommend that this traffic
is encrypted when used in production.

## Setup

To start a postchain server you need
a [node-config.properties](https://gitlab.com/chromaway/postchain/-/wikis/Postchain-Node-Configuration-Properties).
This file is mounted to `/config` folder of the container.

### TLS

When running postchain in production, we recommend encrypting the RPC communication. For this, you need to specify paths for certificate chain file and private key file. This is typically done by mounting the files and using environment variables.

We also recommend encrypting the REST API, which is configured separately in the 
[node-config.properties](https://gitlab.com/chromaway/postchain/-/wikis/Postchain-Node-Configuration-Properties#rest-api) file. 

### Environment

| Variable                     | Default                                 | Description                                                       |
|:-----------------------------|:----------------------------------------|:------------------------------------------------------------------|
| POSTCHAIN_DEBUG              | false                                   | Enables debug functionalities such as `/_debug` rest api endpoint |   
| POSTCHAIN_CONFIG             |                                         | File for node configuration                                       |
| POSTCHAIN_SERVER_PORT        | 50051                                   | Port used for RPC communication                                   |
| POSTCHAIN_SERVER_CERTIFICATE |                                         | Path to mounted certificate file                                  |   
| POSTCHAIN_SERVER_PRIVKEY     |                                         | Path to server certificate private key                            |   
| LOG4J_CONFIGURATION_FILE     | /opt/chromaway/postchain/log/log4j2.yml | Path to Log4j 2 configuration file                                |   

### Ports

A few ports need to be exposed from the container to communicate properly

| Type        | Set from               | Description                             |
|:------------|:-----------------------|:----------------------------------------|
| node<->node | node-config.properties | Port used for node<->node communication |
| RPC         | environment            | Port used for admin communication       |
| Rest api    | node-config.properties | Exposed if this node exposes a rest api |

### Database

This image will assume that you have a postgres database running, see [these instructions](https://gitlab.com/chromaway/postchain/-/blob/dev/postchain-devtools/README_postgres_setup.md) for how to set it up.
Specify the path to this using either `database.url=` in the node config or `POSTCHAIN_DB_URL` environment variable.
If you are using docker compose, the environment variable to set on the postchain service is
`POSTCHAIN_DB_URL: jdbc:postgresql://postgres/postchain`

## Example usage

Put files `private.properties` and `node-config.properties` in a folder called `config`.

### config/private.properties

```shell
messaging.privkey=3132333435363738393031323334353637383930313233343536373839303131
messaging.pubkey=0350fe40766bc0ce8d08b3f5b810e49a8352fdd458606bd5fafe5acdcdc8ff3f57
```

### config/node-config.properties

```shell
include=private.properties

configuration.provider.node=manual
infrastructure=base/ebft

database.driverclass=org.postgresql.Driver
database.url=jdbc:postgresql://host.docker.internal:5432/postchain

database.username=postchain
database.password=postchain
database.schema=postchain_single_dapp_

api.port=7740

messaging.port=9870
```

Start the server using
```commandline
docker run -it --rm \
    --name postchain \
    -h postchain \
    -p 50051:50051 \
    -p 9870:9870   \
    -p 7740:7740   \
    -v $(pwd)/config:/config/ \
    registry.gitlab.com/chromaway/postchain-chromia/chromaway/chromia-server:3.8.0-SNAPSHOT \
    run-server --node-config /config/node-config.properties
```
