# Copyright (c) 2022 ChromaWay Inc. See README for license information.
FROM gcr.io/distroless/java17-debian11:latest@sha256:6e135f309fb112dcf820fc64b4465772bc045d7bf9fb75fd4cf7e6cf8eed5e47

ARG postchain_dir=/opt/chromaway/postchain
COPY target/lib $postchain_dir/lib
COPY src/main/jib/opt/chromaway/postchain/log4j2.yml $postchain_dir/log/log4j2.yml
ENV LOG4J_CONFIGURATION_FILE=/opt/chromaway/postchain/log/log4j2.yml

WORKDIR $postchain_dir
ENTRYPOINT ["/usr/bin/java" \
, "-classpath", "/opt/chromaway/postchain/lib/*" \
, "net.postchain.mc.Directory1Kt" ]
