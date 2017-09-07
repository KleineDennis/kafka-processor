#!/bin/bash

set -e

OS=`uname`
if [ "$OS" = "Darwin" ] ; then
    MYFULL="$0"
else
    MYFULL=`readlink -sm $0`
fi
MYDIR=`dirname ${MYFULL}`

SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -Dsbt.override.build.repos=true -Dsbt.repository.config=./repositories"
java $SBT_OPTS -jar ${MYDIR}/sbt-launch.jar "$@"
