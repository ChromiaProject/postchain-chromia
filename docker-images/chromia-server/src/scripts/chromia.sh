#!/bin/bash
set -eu

app_path=$0

while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

APP_HOME=$( cd "${APP_HOME:-./}.." && pwd -P ) || exit

export LOG4J_CONFIGURATION_FILE=${LOG4J_CONFIGURATION_FILE:=$APP_HOME/config/log4j2.yml}

JVM_FLAGS="-XX:+CrashOnOutOfMemoryError"

exec "${RELL_JAVA:-java}" $JVM_FLAGS -classpath "$APP_HOME/lib/*" net.postchain.server.AppKt "$@"
