#!/bin/sh

trap 'exit' INT TERM EXIT

sh ./entrypoint.sh $@
