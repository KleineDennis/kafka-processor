#!/bin/bash

set -euo pipefail

ARTIFACT_VERSION=${GO_PIPELINE_LABEL:-SNAPSHOT}
TARGET_DIR=./service/target/scala-2.12
SERVICE_NAME=listing-images-kafka-processor

ME=`basename $0`
OS=`uname`
if [ "$OS" = "Darwin" ] ; then
    MYFULL="$0"
else
    MYFULL=`readlink -sm $0`
fi
MYDIR=`dirname ${MYFULL}`
echo MYDIR=${MYDIR}

echo PWD=`pwd`

mkdir -p ./resources
echo ${GO_PIPELINE_LABEL} > ./resources/build.txt

cd ${MYDIR}

echo "[${ME}] Preparing Backend application"
echo "[${ME}] Building backend..."

./sbt -Dsbt.log.noformat=true clean coverage test coverageReport coverageAggregate codacyCoverage < /dev/null

./sbt -Dsbt.log.noformat=true clean service/assembly < /dev/null

echo "Create archive"
cd ${TARGET_DIR}
tar -czvf ${SERVICE_NAME}-${ARTIFACT_VERSION}.tgz ${SERVICE_NAME}-${ARTIFACT_VERSION}.jar