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

fail()
{
  echo "[$ME] FAIL: $*"
  exit 1
}

TARGET_DIR="${MYDIR}/service/target/scala-2.12"

[ -d "${TARGET_DIR}" ] || fail "Does not look like a build has happened here. Directory ${TARGET_DIR} doesn't exist"

SERVICE_ARTIFACT="${TARGET_DIR}/${SERVICE_NAME}-${ARTIFACT_VERSION}.tgz"
[ -f "${SERVICE_ARTIFACT}" ] || fail "Artifact doesn't exist: ${SERVICE_ARTIFACT}"

echo "[${ME}] Packaging deployment artifacts"
cp "${MYDIR}/metadata.yaml" "${MYDIR}/deploy"
DEPLOY_ARTIFACT="${MYDIR}/target/${SERVICE_NAME}-${ARTIFACT_VERSION}-deploy.tgz"
tar -czf "${DEPLOY_ARTIFACT}" "${MYDIR}/deploy"

echo "[$ME] Uploading artifacts to S3"
aws s3 cp "${SERVICE_ARTIFACT}" "s3://${ARTIFACTS_BUCKET}/${SERVICE_NAME}/"
aws s3 cp "${DEPLOY_ARTIFACT}" "s3://${ARTIFACTS_BUCKET}/${SERVICE_NAME}/"
