# Anchoring blockchain configuration properties

Configuration under the key `anchoring`.

| Name                                    | Description                                                                                                                    | Type    | Default |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|---------|---------|
| `max_blocks_per_chain`                  | Maximum amount of blocks to anchor per chain per anchoring chain block                                                         | int     | 100     |
| `max_anchoring_delay`                   | Maximum delay after a block is available to be anchored until anchoring chain block building will be triggered in milliseconds | int     | 1000    |
| `max_anchoring_blocks_per_anchor_block` | Maximum amount of blocks available to be anchored until anchoring chain block building will be triggered                       | int     | 100     |
| `batch_mode`                            | Activates batch mode anchoring, i.e. all blocks are anchored in a single operation                                             | boolean | false   |
