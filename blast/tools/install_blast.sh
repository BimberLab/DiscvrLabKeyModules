#!/bin/bash
#
#
# Copyright (c) 2012 LabKey Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e
set -u
INSTALL_DIR=

while getopts "d:" arg;
do
  case $arg in
    d)
       INSTALL_DIR=$OPTARG
       INSTALL_DIR=${INSTALL_DIR%/}
       echo "INSTALL_DIR = ${INSTALL_DIR}"
       ;;
    *)
       echo "The following arguments are supported:"
       echo "-d: the path to the labkey install, such as /usr/local/labkey/blast."
       echo "Example command:"
       echo "./install_blast.sh -d /usr/local/labkey/blast"
       exit 1;
      ;;
  esac
done

if [ -z $INSTALL_DIR ];
then
    echo "Must provide the install location using the argument -d"
    exit 1;
fi

if [ ! -d $INSTALL_DIR ];
then
    echo "The install directory does not exist or is not a directory: ${INSTALL_DIR}"
    exit 1;
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install location"
echo ""
echo "INSTALL_DIR: INSTALL_DIR"

cd $INSTALL_DIR;

wget ftp://ftp.ncbi.nlm.nih.gov/blast/executables/blast+/LATEST/ncbi-blast-2.2.29+-x64-linux.tar.gz
gunzip ncbi-blast-2.2.29+-x64-linux.tar.gz
tar -xf ncbi-blast-2.2.29+-x64-linux.tar
gzip ncbi-blast-2.2.29+-x64-linux.tar
