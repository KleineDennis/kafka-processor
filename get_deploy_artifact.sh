#!/bin/bash

set -exuo pipefail

SERVICE_NAME=listing-images-kafka-processor

BUCKET_REGION=${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-1}}
ARTIFACTS_BUCKET=as24-artifacts-$BUCKET_REGION
ARTIFACT_VERSION=${GO_PIPELINE_LABEL:-SNAPSHOT}

ME=`basename $0`
OS=`uname`
if [ "$OS" = "Darwin" ] ; then
    MYFULL="$0"
else
    MYFULL=`readlink -sm $0`
fi
MYDIR=`dirname ${MYFULL}`

DEPLOY_ARTIFACT="${SERVICE_NAME}-${ARTIFACT_VERSION}-deploy.tgz"
aws s3 cp "s3://${ARTIFACTS_BUCKET}/${SERVICE_NAME}/${DEPLOY_ARTIFACT}" "${MYDIR}/"
tar -xvf "${MYDIR}/${DEPLOY_ARTIFACT}"
rm -f "${MYDIR}/${DEPLOY_ARTIFACT}"
