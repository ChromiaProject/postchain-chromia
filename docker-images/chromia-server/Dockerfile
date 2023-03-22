# Copyright (c) 2023 ChromaWay Inc. See README for license information.
FROM alpine:3.17.2@sha256:69665d02cb32192e52e07644d76bc6f25abeb5410edc1c7a81a10ba3f0efb90a

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
, "net.postchain.AppKt" ]
