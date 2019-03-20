#!/bin/bash

set -e
set -u
set -x

WD=`pwd`
HOME=`echo ~/`

DOCKER=/opt/acc/sbin/exadocker
CITESEQ_COUNT_FILE=$1
HTML_FILE=$2
FINAL_CALLS=$3
RAW_CALLS=$4

RAM_OPTS=""
if [ ! -z $SEQUENCEANALYSIS_MAX_RAM ];then
    RAM_OPTS=" --memory=${SEQUENCEANALYSIS_MAX_RAM}g"
fi

sudo $DOCKER pull bbimber/rnaseq:seurat

sudo $DOCKER run --rm=true $RAM_OPTS -v "${WD}:/work" -v "${HOME}:/homeDir" -u $UID -e USERID=$UID -w /work -e HOME=/homeDir bbimber/rnaseq:seurat Rscript -e "args <- commandArgs(TRUE);barcodeFile <- '"${CITESEQ_COUNT_FILE}"';finalCallFile<- '"${FINAL_CALLS}"';allCallsOutFile<-'"${RAW_CALLS}"';rmarkdown::render('htoClassifier.Rmd', output_file = '"${HTML_FILE}"')"