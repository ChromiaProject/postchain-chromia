# Operations

## Directory1

| Operation       | Permission | Rate limit    | Comment |
|-----------------|------------|---------------|---------|
| init            | any        | no(only once) |         |
| update_provider | self       | actions       |         |

## Common

| Operation                  | Permission                           | Rate limit | Comment                                                                             |
|----------------------------|--------------------------------------|------------|-------------------------------------------------------------------------------------|
| register_provider          | SP > NP > CNP >= CNP                 | actions    | SP can register NP, NP can register CNP, CNP can register CNP                       |
| transfer_action_points     | any                                  | actions    | TODO: should be: SP > NP > CNP >= CNP                                               |
| add_node                   | no                                   | actions    | TODO: Should be only NP? But others can add replicas, right? Limit number of nodes? |
| replace_node               | owned by                             | actions    |                                                                                     |
| remove_node                | no                                   | actions    | TODO: Should be owned by or system permission?                                      |
| update_node_host           | node provider                        | actions    | TODO: Merge with other updates                                                      |
| update_node_port           | node provider                        | actions    | TODO: Merge with other updates                                                      |
| update_node_api_url        | node provider                        | actions    | TODO: Merge with other updates                                                      |
| add_container_replica      | no                                   | actions    | TODO: What is this? Redesign, see comment in !96                                    |
| remove_container_replica   | no                                   | actions    | TODO: What is this? Should be owned by or system permission? Redesign.              |
| add_node_to_cluster        | cluster provider                     | actions    |                                                                                     |
| create_cluster             | no                                   | actions    ||
| add_bc_replica             | node provider                        | actions    ||
| remove_bc_replica          | node provider                        | actions    ||
| make_vote                  | voter set member                     | actions    ||
| retract_vote               | voter set member                     | actions    ||
| propose_cluster_provider   | cluster governor                     | actions    ||
| WIP                        | WIP                                  | WIP        | WIP                                                                                 |
| propose_enable_provider    | system                               | actions    | tier 1 can enable tier 0 without voting                                             |
| propose_disable_provider   | system                               | actions    | will disable all nodes as well                                                      |
| propose_provider_is_system | system                               | actions    | can also demote                                                                     |
| propose_container          | cluster deployer                     | actions    ||
| propose_container_limits   | cluster deployer                     | actions    ||
| propose_remove_container   | cluster deployer and empty container | actions    ||
| propose_cluster_deployer   | cluster governor                     | actions    ||
| propose_cluster_limits     | cluster governor                     | actions    | What does this do?                                                                  |
| propose_remove_cluster     | cluster governor and empty cluster   | actions    ||
| propose_blockchain         | container deployer                   | actions    ||
| propose_configuration      | container deployer                   | actions    ||
| propose_blockchain_action  | container deployer                   | actions    | I can basically stop other blockchains if we use same container                     |
| propose_update_voter_set   | voter set governor                   | actions    |                                                                                     |
| anchor_block               | no                                   | no         | attack vector? Hard to make but perhaps we need some verification?                  |




