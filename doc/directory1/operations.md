# Operations

## Directory1

| Operation       | Permission | Rate limit    | Comment |
|-----------------|------------|---------------|---------|
| init            | any        | no(only once) |         |
| update_provider | self       | actions       |         |

## Common

| Operation                  | Permission                            | Rate limit | Comment                                                                           |
|----------------------------|---------------------------------------|------------|-----------------------------------------------------------------------------------|
| register_provider          | tier > 1 adds 0 or system             | actions    |                                                                                   |
| transfer_action_points     | any                                   | actions    | should only be tier > 1 / system?                                                 |
| add_node                   | no                                    | actions    | Should be only tier 1? But others can add replicas, right? Limit number of nodes? |
| replace_node               | owned by                              | actions    |                                                                                   |
| remove_node                | no                                    | actions    | Should be owned by or system permission?                                          |
| update_node_host           | node provider                         | actions    | could be merged with other updates?                                               |
| update_node_port           | node provider                         | actions    | could be merged with other updates?                                               |
| update_node_api_url        | node provider                         | actions    | could be merged with other updates?                                               |
| add_container_replica      | no                                    | actions    | What is this?                                                                     |
| remove_container_replica   | no                                    | actions    | What is this? Should be owned by or system permission?                            |
| add_node_to_cluster        | cluster provider                      | actions    |                                                                                   |
| add_provider_to_cluster    | only tier >1 or system can add tier 0 | actions    | Why can we add tier 0? Should also require governor and voting?                   |
| create_cluster             | no                                    | actions    ||
| add_bc_replica             | node provider                         | actions    ||
| remove_bc_replica          | node provider                         | actions    ||
| make_vote                  | voter set member                      | actions    | only one vote                                                                     |
| retract_vote               | voter set member                      | actions    | must exist..                                                                      |
| propose_cluster_provider   | cluster governor                      | actions    | Supersedes add_provider_to_cluster?                                               |
| propose_enable_provider    | system                                | actions    | tier 1 can enable tier 0 without voting                                           |
| propose_disable_provider   | system                                | actions    | will disable all nodes as well                                                    |
| propose_provider_is_system | system                                | actions    | can also demote                                                                   |
| propose_container          | cluster deployer                      | actions    ||
| propose_container_limits   | cluster deployer                      | actions    ||
| propose_remove_container   | cluster deployer and empty container  | actions    ||
| propose_cluster_deployer   | cluster governor                      | actions    ||
| propose_cluster_limits     | cluster governor                      | actions    | What does this do?                                                                |
| propose_remove_cluster     | cluster governor and empty cluster    | actions    ||
| propose_blockchain         | container deployer                    | actions    ||
| propose_configuration      | container deployer                    | actions    ||
| propose_blockchain_action  | container deployer                    | actions    | I can basically stop other blockchains if we use same container                   |
| propose_update_voter_set   | voter set governor                    | actions    |                                                                                   |
| anchor_block               | no                                    | no         | attack vector? Hard to make but perhaps we need some verification?                |




