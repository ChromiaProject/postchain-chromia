#!/usr/bin/env bash


set -eu

PGDATA_CURRENT_VERSION=14
PGDATA_NEW_VERSION="$(pg_config --version | cut -d' ' -f2 | cut -d. -f1)"
CONTAINER_DIR="$POSTCHAIN_DIR/target"                      # Hosts container directory, the parent for pgdata/
PGDATA_PATH="$CONTAINER_DIR/pgdata"                        # Path to PGDATA by using the parent mount point
PGDATA_CURRENT_PATH="$CONTAINER_DIR/pgdata-current"        # Temporary directory for current PGDATA
PGDATA_NEW_PATH="$CONTAINER_DIR/pgdata$PGDATA_NEW_VERSION" # Temporary directory for migrated/new PGDATA
WORKING_DIR="/tmp/"
MIGRATION_LOG=/tmp/migration.log

function try_to_rollback() {
  echo "Trying to recover by rollback"
  if [ -d "$PGDATA_CURRENT_PATH" ]; then
    echo "Reverting to v$PGDATA_CURRENT_VERSION"
    mv "$PGDATA_CURRENT_PATH" "$PGDATA_PATH"
  fi
  if [ -d "$PGDATA_NEW_PATH" ]; then
    echo "Removing v$PGDATA_NEW_VERSION"
    rm -rf "$PGDATA_NEW_PATH"
  fi
}

on_exit() {
  exit_code=$?
  echo trap
  if [ $exit_code -ne 0 ]; then
    try_to_rollback
    echo
    echo
    echo "Command failed with exit code $exit_code"
    if [ -f "$MIGRATION_LOG" ]; then
      echo "Output from log:"
      cat $MIGRATION_LOG
    fi
    exit $exit_code
  fi
}

if [ "$(cat "$PGDATA/PG_VERSION")" = "$PGDATA_NEW_VERSION" ]; then
  echo "No migration needed, postgres is already on version $PGDATA_NEW_VERSION"
  exit 0
fi

trap on_exit EXIT


echo "Migrating database from version $PGDATA_CURRENT_VERSION to $PGDATA_NEW_VERSION"

echo " - Moving $PGDATA_CURRENT_VERSION data"
mv "$PGDATA_PATH" "$PGDATA_CURRENT_PATH"


cd "$WORKING_DIR"
mkdir -p "$PGDATA_NEW_PATH"
initdb --username=postgres -D "$PGDATA_NEW_PATH" > "$MIGRATION_LOG" 2>&1
touch "$PGDATA_NEW_PATH/$ALREADY_INITED_FILE"

echo " - Migrating data"
pg_upgrade --old-datadir "$PGDATA_CURRENT_PATH" --new-datadir "$PGDATA_NEW_PATH" --old-bindir "$PG_BIN_14" --new-bindir /usr/local/bin/ -U postgres > "$MIGRATION_LOG" 2>&1


echo " - Verifying and cleaning up data"
pg_ctl -D "$PGDATA_NEW_PATH" start > "$MIGRATION_LOG" 2>&1
vacuumdb -U postgres --all --analyze-in-stages > "$MIGRATION_LOG" 2>&1
pg_ctl -D "$PGDATA_NEW_PATH" stop > "$MIGRATION_LOG" 2>&1


echo " - Data v$PGDATA_CURRENT_VERSION size: $(du -hs "$PGDATA_CURRENT_PATH" | awk '{print $1}')"
echo " - Data v$PGDATA_NEW_VERSION size: $(du -hs "$PGDATA_NEW_PATH" | awk '{print $1}')"

echo " - Activating v$PGDATA_NEW_VERSION data"
mv "$PGDATA_NEW_PATH" "$PGDATA_PATH"

echo " - Removing v$PGDATA_CURRENT_VERSION data"
rm -rf "$PGDATA_CURRENT_PATH"

trap - EXIT
echo "Successfully migrated"

