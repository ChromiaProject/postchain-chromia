# Copyright (c) 2023 ChromaWay Inc. See README for license information.
FROM alpine:3.17.3@sha256:124c7d2707904eea7431fffe91522a01e5a861a624ee31d03372cc1d138a3126

RUN apk update && apk add openjdk17 quota-tools && rm -rf /var/cache/apk/*

ARG postchain_dir=/opt/chromaway/postchain
COPY target/lib $postchain_dir/lib
COPY src/main/resources/log4j2.yml $postchain_dir/log/log4j2.yml

ENV POSTCHAIN_DEBUG=false
ENV POSTCHAIN_RUNNING_IN_CONTAINER=true
ENV LOG4J_CONFIGURATION_FILE=/opt/chromaway/postchain/log/log4j2.yml

WORKDIR $postchain_dir
ENTRYPOINT ["/usr/bin/java", "-XX:+CrashOnOutOfMemoryError" \
, "-classpath", "/opt/chromaway/postchain/lib/*" \
, "net.postchain.server.AppKt" ]
