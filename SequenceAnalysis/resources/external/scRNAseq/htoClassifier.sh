#!/bin/bash

set -e
set -x

WD=`pwd`
HOME=`echo ~/`

DOCKER=/opt/acc/sbin/exadocker
CITESEQ_COUNT_DIR=$1
HTML_FILE=$2
FINAL_CALLS=$3
RAW_CALLS=$4
DO_HTO_FILTER=$5
MIN_READS_PER_CELL=$6
METRICS_FILE=$7
USE_SEURAT=$8
USE_MULTISEQ=$9

WHITELIST="whitelistFile<-NULL;"
if [ $# -ge 10 ];then
    WHITELIST="whitelistFile<-'"${10}"';"
fi

ENV_OPTS=""
RAM_OPTS=""
if [ ! -z $SEQUENCEANALYSIS_MAX_RAM ];then
    RAM_OPTS=" --memory=${SEQUENCEANALYSIS_MAX_RAM}g"

    ENV_OPTS=" -e SEQUENCEANALYSIS_MAX_RAM"
fi

if [ ! -z SEQUENCEANALYSIS_MAX_THREADS ];then
    ENV_OPTS=${ENV_OPTS}" -e SEQUENCEANALYSIS_MAX_THREADS="${SEQUENCEANALYSIS_MAX_THREADS}
fi

sudo $DOCKER pull bimberlab/oosap

sudo $DOCKER run --rm=true $RAM_OPTS $ENV_OPTS -v "${WD}:/work" -v "${HOME}:/homeDir" -u $UID -e USERID=$UID -w /work -e HOME=/homeDir bimberlab/oosap Rscript -e "barcodeDir <- '"${CITESEQ_COUNT_DIR}"';finalCallFile <- '"${FINAL_CALLS}"';doHtoFilter <- "${DO_HTO_FILTER}";maxValueForColSumFilter <- "${MIN_READS_PER_CELL}";allCallsOutFile <- '"${RAW_CALLS}"';metricsFile <- '"${METRICS_FILE}"';useSeurat <- ${USE_SEURAT};useMultiSeq <- ${USE_MULTISEQ};"${WHITELIST}"rmarkdown::render('htoClassifier.Rmd', output_file = '"${HTML_FILE}"');print('Rmarkdown complete');"

echo 'script complete'