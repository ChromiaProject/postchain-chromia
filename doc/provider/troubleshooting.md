# Troubleshooting

## How to revive chain0

Suppose chain0 got stuck at `height = 1000` (can't build block 1000), and a new chain0 configuration is required to revive it. To revive chain0 do the following:

1. Add a new configuration `C` at height 1000 manually on each node by means of `postchain-cli`:
```shell
./chromia-node/postchain-cli.sh add-configuration -nc ./config/config.0.properties -cid 0 -h 1000 -bc ./directory1/manager.xml
```

2. Restart all nodes of `system` cluster.

