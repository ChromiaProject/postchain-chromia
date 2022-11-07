# Deploying a Dapp to the network

Deploying a dapp requires you to have a deployment key. This is a reference to a container which you have permissions to.
From a rell project with the following structure:
```shell
rell
├── config
│   └── deploy.xml
└── src
    └── main.rell
```

You can run 
```shell
chromia-deploy.sh deploy --config <config> --container <id>
```

where `<config>` is the client configuration pointing to a node in the network and `<id>` is the container id to deploy to.
