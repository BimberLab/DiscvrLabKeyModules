#!/bin/bash

scl enable rh-python36 bash
virtualenv /home/groups/prime-seq/pipeline_tools/bin/primeseq-python
source /home/groups/prime-seq/pipeline_tools/bin/primeseq-python/bin/activate
pip install --upgrade pip
pip install CITE-seq-Count


