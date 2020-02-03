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
METRICS_FILE=$6
DO_CELL_FILTER=$7
WHITELIST="whitelistFile<-NULL;"
if [ $# -ge 8 ];then
    WHITELIST="whitelistFile<-'"${8}"';"
fi

RAM_OPTS=""
if [ ! -z $SEQUENCEANALYSIS_MAX_RAM ];then
    RAM_OPTS=" --memory=${SEQUENCEANALYSIS_MAX_RAM}g"
fi

sudo $DOCKER pull bimberlab/oosap

sudo $DOCKER run --rm=true $RAM_OPTS -v "${WD}:/work" -v "${HOME}:/homeDir" -u $UID -e USERID=$UID -w /work -e HOME=/homeDir bimberlab/oosap Rscript -e "barcodeDir <- '"${CITESEQ_COUNT_DIR}"';finalCallFile <- '"${FINAL_CALLS}"';doHtoFilter <- "${DO_HTO_FILTER}";doCellFilter <- "${DO_CELL_FILTER}";allCallsOutFile <- '"${RAW_CALLS}"';metricsFile <- '"${METRICS_FILE}"';"${WHITELIST}"rmarkdown::render('htoClassifier.Rmd', output_file = '"${HTML_FILE}"')"