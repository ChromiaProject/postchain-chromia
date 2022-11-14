# Postchain-server

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

| Variable                     | Default                        | Description                                                       |
|:-----------------------------|:-------------------------------|:------------------------------------------------------------------|
| POSTCHAIN_CONF               | /config/node-config.properties | File for node configuration                                       |
| POSTCHAIN_SERVER_PORT        | 50051                          | Port used for rpc communication                                   |
| POSTCHAIN_DEBUG              | false                          | Enables debug functionalities such as `/_debug` rest api endpoint |   
| POSTCHAIN_SERVER_CERTIFICATE |                                | Path to mounted certificate file                                  |   
| POSTCHAIN_SERVER_PRIVKEY     |                                | Path to server certificate private key                            |   

### Ports

A few ports need to be exposed from the container to communicate properly

| Type        | Set from               | Description                             |
|:------------|:-----------------------|:----------------------------------------|
| node<->node | node-config.properties | Port used for node<->node communication |
| RPC         | environment            | Port used for admin communication       |
| Rest api    | node-config.properties | Exposed if this node exposes a rest api |

### Database

This image will assume that you have a postgres database running.
Specify the path to this using either `database.url=` in the node config or `POSTCHAIN_DB_URL` environment variable.
If you are using docker compose, the environment variable to set on the postchain service is
`POSTCHAIN_DB_URL: jdbc:postgresql://postgres/postchain`

## Example usage

Put the files [private.properties](../dapp/README.md#configprivateproperties) and [node-config.properties](../dapp/README.md#confignode-configproperties) in a folder called `config`.

Start the server using
```commandline
docker run -it --rm \
    --name postchain
    -h postchain \
    -p 50051:50051 \
    -p 9870:9870   \
    -p 7740:7740   \
    -v $(pwd)/config:/config/ \
    registry.gitlab.com/chromaway/postchain-distribution/chromaway/postchain-server:3.6.0
```
