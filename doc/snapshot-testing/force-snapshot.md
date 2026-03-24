# Force enabling snapshot on existing chain

This guide will describe how to test a snapshot on an existing chain. The overall plan is this:

1. Build a snapshot of existing chain:
   1. Start a replica node with snapshot enabled (enforced)
   2. Run to the end
   3. Create a database dump (optional, but lets us re-run the test)
2. Build a new block with a snapshot root header
   1. Load previous db dump into a new schema
   2. Add a new config with snapshot enabled and our new node as signer
   3. Run an operation to trigger any module to emit datum updates
   4. Build a block with the new snapshot root header
   5. Keep node alive
3. Sync snapshot
   1. Start a new node (replica or signer)
   2. Sync blocks and snapshot data

## Prerequisites

Make sure to build a Postchain server image on correct branches:

```bash
cd ~/git/postchain
git checkout 1957-force-snapshot
mvn clean source:jar install -DskipTests

cd ~/git/postchain-chromia
git checkout 1957-force-snapshot
mvn clean install -DskipTests
```

Commands assume your current working directory is `doc/snapshot-testing/`.

## Step by step guide

This example will use `directory_chain` on `devnet1`.

### 1. Build a snapshot of existing chain

```bash
cd ~/git/postchain-chromia/doc/snapshot-testing/
./db.sh drop-schema snapshot_replica
./snapshot-replica.sh start-node
```

In a second terminal:

```bash
./snapshot-replica.sh init
```

This will replicate `directory_chain` and also build all snapshot data (`-e FORCE_SNAPSHOT=true`) except adding the snapshot header to the block header (since this breaks replication).

At desired point (sync enough blocks so that snapshot sync will be triggered, please see the note below for further details!), create a db dump:

```bash
./db.sh create snapshot_replica dc_dump.sql
```

Now we have a copy of `directory_chain` with snapshot data created. The snapshot data is however not yet part of the
block header.

> **Note:** Because of Rell snapshot implementation allocating row ids for objects, rowids will mismatch between the
> snapshot synced chain and the original chain. There needs to be consensus on rowids for voting on proposals in DC, so
> for devnet1 DC you can only sync up to first vote operation which is approx 9k blocks. So sync a bit less than that,
> also make sure to sync enough blocks to trigger snapshot sync, you can adjust this with `snapshotsync.threshold`
> node config.

### 2-3. Build a new block with a snapshot root header and start a replica node

Restore the db dump into a new schema (optional):

```bash
./db.sh restore dc_dump.sql snapshot_replica_node0
```

Now take a look at `ForceEnableSnapshot`, verify schemas, db urls etc and run it. Eventually you should see log entries similar to this:

```
height: 452230  snapshot: 452202   signers: 026006: true  replica height: 39305 (338 blocks/s)
```

Logged every second. What it tells us:
 - `height` is the current height of the validator node.
 - `snapshot` is the height of the last snapshot block.
 - `signers` is the list of signers and their status, should always be true for our node.
 - `replica height` is the height of the replica node when started.
 - `replica throughput` is the approximate throughput of the replica node build

### 3. Verify database content

Once the snapshot is done synchronized on the replica node we can verify the database content by using a modified version of `SnapshotDbCompare`. The current implementation takes two schemas and compared snapshot tables for available modules and also verifies ICMF table content (rebuilt from snapshot data)
