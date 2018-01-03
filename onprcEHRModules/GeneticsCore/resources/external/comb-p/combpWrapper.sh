#!/bin/bash

set -e
set -u
set -x

DATA_DIR=$1
INPUT_FILE=$2
OUT_DIR=$3
OUT_PREFIX=$4
DIST=$5
STEP=$6
SEED=$7
DOCKER=/opt/acc/sbin/exadocker

# NOTE: switch to using dockerhub for the build
# https://github.com/bbimber/combpdocker
#docker build -q -t combp $1
#docker pull bbimber/combpdocker

sudo $DOCKER run --rm=true -v "${DATA_DIR}:/data" -v "${OUT_DIR}:/outDir" bbimber/combpdocker comb-p pipeline -c 5 --dist $DIST --step $STEP --seed $SEED -p /outDir/${OUT_PREFIX} /data/${INPUT_FILE}
