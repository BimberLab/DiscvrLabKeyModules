#!/bin/bash

set -e
set -u
set -x

SCRIPT=$1
CITE_SEQ_COUNT=$2
OUTPUT_PREFIX=$3

Rscript $SCRIPT $CITE_SEQ_COUNT $OUTPUT_PREFIX
