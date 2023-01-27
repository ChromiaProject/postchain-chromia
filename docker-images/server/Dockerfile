# Copyright (c) 2022 ChromaWay Inc. See README for license information.
FROM gcr.io/distroless/java17-debian11:debug@sha256:23f32ba072842ea8438ae3b6e203a3f842fe7d172fc246b7e91f7a552684cfca

ARG postchain_dir=/opt/chromaway/postchain
COPY target/lib $postchain_dir/lib
COPY src/main/resources/log4j2.yml $postchain_dir/log/log4j2.yml

ENV POSTCHAIN_DEBUG=false
ENV POSTCHAIN_RUNNING_IN_CONTAINER=true

WORKDIR $postchain_dir
ENTRYPOINT ["/usr/bin/java", "-Dlog4j.configurationFile=/opt/chromaway/postchain/log/log4j2.yml" \
, "-classpath", "/opt/chromaway/postchain/lib/*" \
, "net.postchain.AppKt" ]
