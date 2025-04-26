#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-build.sh

# Patch ci-build.sh to insert server credentials into Maven settings
sed -i 's|</servers>|<server><id>github</id><username>\${env.GITHUB_ACTOR}</username><password>\${env.BONEJ_PLUS_REPO}</password></server></servers>|' ci-build.sh

sh ci-build.sh
