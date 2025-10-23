#!/bin/bash

if [ "$1" == "drop-schema" ] && [ -n "$2" ]; then
  schema="$2"

  echo "drop schema $schema cascade;" | PGPASSWORD=postchain psql -U postchain -h localhost postchain

elif [ "$1" == "create" ] && [ -n "$3" ]; then

  schema="$2"
  output="$3"

  if [ -f "$output" ]; then
      echo "File $output exists"
      exit 1
  fi

  echo "Writes db to $output"

  PGPASSWORD=postchain pg_dump -U postchain -h localhost postchain --schema=$schema -f $output
  ls -lh "$output"

elif [ "$1" == "restore" ] && [ -n "$3" ]; then

  input="$2"
  schema="$3"

  echo "Restoring $input"

  echo "drop schema $schema cascade;" | PGPASSWORD=postchain psql -U postchain -h localhost postchain

  if [[ "$input" == *.gz ]]; then
    zcat "$input" | sed "s/snapshot_replica/$schema/g" | PGPASSWORD=postchain psql -U postchain -h localhost -d postchain -f -
  else
    cat "$input" | sed "s/snapshot_replica/$schema/g" | PGPASSWORD=postchain psql -U postchain -h localhost -d postchain -f -
  fi

else
  echo "Usage: $0 ..."
  echo
  echo "$0 drop-schema <schema>"
  echo "$0 create snapshot_replica snapshot_replica-description.sql"
  echo "$0 restore snapshot_replica_noder-256k.sql snapshot_replica_noder"
fi
