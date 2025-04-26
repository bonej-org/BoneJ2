#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-build.sh

cat ~/.m2/settings.xml

sh ci-build.sh
