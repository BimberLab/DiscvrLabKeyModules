#!/bin/bash

set -e
set -u
set -x

WD=`pwd`
HOME=`echo ~/`

DOCKER=/opt/acc/sbin/exadocker
LK_ROOT=$1

RAM_OPTS=""
if [ ! -z $SEQUENCEANALYSIS_MAX_RAM ];then
    RAM_OPTS=" --memory=${SEQUENCEANALYSIS_MAX_RAM}g"
fi

sudo $DOCKER pull bbimber/rnaseq:seurat

sudo $DOCKER run --rm=true $RAM_OPTS -v "${WD}:/work" -v "${HOME}:/homeDir" -u $UID -v "${LK_ROOT}:/lkRoot" -e USERID=$UID -w /work -e HOME=/homeDir bbimber/rnaseq:seurat Rscript --vanilla script.R