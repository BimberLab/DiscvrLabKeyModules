#!/bin/bash

set -e
set -u
set -x

WD=`pwd`
HOME=`echo ~/`

DOCKER=/opt/acc/sbin/exadocker
LK_ROOT=$1

RAM_OPTS=""
ENV_OPTS=""
if [ ! -z $SEQUENCEANALYSIS_MAX_RAM ];then
    RAM_OPTS=" --memory=${SEQUENCEANALYSIS_MAX_RAM}g"

    ENV_OPTS=" -e SEQUENCEANALYSIS_MAX_RAM"
fi

if [ ! -z SEQUENCEANALYSIS_MAX_THREADS ];then
    ENV_OPTS=${ENV_OPTS}" -e SEQUENCEANALYSIS_MAX_THREADS="${SEQUENCEANALYSIS_MAX_THREADS}
fi

sudo $DOCKER pull bimberlab/oosap

sudo $DOCKER run --rm=true $RAM_OPTS $ENV_OPTS -v "${WD}:/work" -v "${HOME}:/homeDir" -u $UID -e USERID=$UID -w /work -e HOME=/homeDir bimberlab/oosap Rscript --vanilla script.R