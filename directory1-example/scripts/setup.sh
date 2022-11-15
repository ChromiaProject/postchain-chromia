#!/bin/bash

set -eu

D=$(dirname "${BASH_SOURCE[0]}")
C0_SOURCES=$(find "$D"/.. -maxdepth 1 -regex '.*directory1-[0-9]*.[0-9]*.[0-9]*.*-sources.tar.gz')
PMC=$(find "$D"/.. -maxdepth 1 -regex '.*pmc-directory-[0-9]*.[0-9]*.[0-9]*.*-dist.tar.gz')
DEPLOY=$(find "$D"/.. -maxdepth 1 -regex '.*chromia-deployment-tool-cli-[0-9]*.[0-9]*.[0-9]*.*-dist.tar.gz')
CLIENT=$(find "$D"/.. -maxdepth 1 -regex '.*postchain-client-[0-9]*.[0-9]*.[0-9]*.*-dist.tar.gz')
tar xf "$C0_SOURCES"
tar xf "$PMC"
tar xf "$DEPLOY"
tar xf "$CLIENT"
sed -i'' -e "s/{hostName}/$(bash $D/find-ip)/g" "$D/../config/c0-deploy.xml"
sed -i'' -e "s/{apiUrl}/http:\/\/localhost:7740/g" "$D/../config/c0-deploy.xml"
BRID=$(bash "$D/../chromia-deploy/bin/chromia-deploy.sh" compile --source-dir "$D/../directory1/rell" --output-dir "$D/../out" --show-brid config/c0-deploy.xml)
bash "$D/../chromia-deploy/bin/chromia-deploy.sh" compile --source-dir "$D/../app/src" --output-dir "$D/../app-out" "$D/../app/config/deploy.xml"

bash "$D/../postchain-mc/bin/pmc" config --file "$D/../provider/alpha/.pmc/config" --set brid="$BRID"
bash "$D/../postchain-mc/bin/pmc" config --file "$D/../provider/beta/.pmc/config" --set brid="$BRID"
bash "$D/../postchain-mc/bin/pmc" config --file "$D/../provider/gamma/.pmc/config" --set brid="$BRID"
bash "$D/../postchain-mc/bin/pmc" config --file "$D/../provider/delta/.pmc/config" --set brid="$BRID"
