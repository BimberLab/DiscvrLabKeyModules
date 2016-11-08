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

# This script is designed to assist with the initial installation of the external informatics tools
# used by the SequenceAnalysis module pipeline.  It is a fairly utilitarian script, which has undergone only
# very limited testing.  It is designed to provide a template to assist with the install, not a one-click installer.
# This script was written for RHEL6, and has been adapted for Ubuntu.
#
# Prior to using this script, you should run and configure CPAN and the package manager for your OS (ie. yum or apt-get).
# This script is designed to be run as root, using a command similar to:
#
# bash sequence_tools_install.sh -d /usr/local/labkey/ | tee sequence_tools_install.log
#
# NOTE: this script will delete any previously downloaded versions of these tools, assuming they were downloaded to the location
# expected by this script.  This is deliberate so that the script can be re-run to perform incremental upgrades of these tools.
#
#
# Variables
#
set -e
set -u
FORCE_REINSTALL=
SKIP_PACKAGE_MANAGER=
LK_HOME=

while getopts "d:fp" arg;
do
  case $arg in
    d)
       LK_HOME=$OPTARG
       LK_HOME=${LK_HOME%/}
       echo "LK_HOME = ${LK_HOME}"
       ;;
    f)
       FORCE_REINSTALL=1
       ;;
    p)
       SKIP_PACKAGE_MANAGER=1
       echo "SKIP_PACKAGE_MANAGER = ${SKIP_PACKAGE_MANAGER}"
       ;;
    *)
       echo "The following arguments are supported:"
       echo "-d: the path to the labkey install, such as /usr/local/labkey.  If only this parameter is provided, tools will be installed in bin/ and src/ under this location."
       echo "-f: optional.  If provided, all tools will be reinstalled, even if already present"
       echo "Example command:"
       echo "./sequence_tools_install.sh -d /usr/local/labkey"
       exit 1;
      ;;
  esac
done

if [ -z $LK_HOME ];
then
    echo "Must provide the install location using the argument -d"
    exit 1;
fi

if [ ! -d $LK_HOME ];
then
    echo "The install directory does not exist or is not a directory: ${LK_HOME}"
    exit 1;
fi

LKTOOLS_DIR=${LK_HOME}/bin
LKSRC_DIR=${LK_HOME}/tool_src
mkdir -p $LKSRC_DIR
mkdir -p $LKTOOLS_DIR

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install location"
echo ""
echo "LKTOOLS_DIR: $LKTOOLS_DIR"
echo "LKSRC_DIR: $LKSRC_DIR"

if [ ! -z $SKIP_PACKAGE_MANAGER ]; then
    echo "Skipping package install"
elif [ $(which yum) ]; then
    echo "Using Yum"
    yum -y install liblapack-dev
elif [ $(which apt-get) ]; then
    echo "Using apt-get"
    apt-get -q -y install liblapack-dev
fi


##
##CNVnator
##
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Install CNVnator"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/cnvnator || ! -z $FORCE_REINSTALL ]];
#then
#    echo "Cleaning up previous installs"
#    rm -Rf CNVnator_v0.3*
#    #rm -Rf MORGAN_V*
#    #rm -Rf $LKTOOLS_DIR/MORGAN
#
#    #follow instructions here: http://lcg-heppkg.web.cern.ch/lcg-heppkg/debian/install.html
#
#    wget http://sv.gersteinlab.org/cnvnator/CNVnator_v0.3.zip
#    unzip CNVnator_v0.3.zip
#    cd CNVnator_v0.3/src/samtools
#    make
#    cd ../apt
#    make
#
#    #cp -R ./MORGAN_V33_Release $LKTOOLS_DIR/cnvnator
#else
#    echo "Already installed"
#fi


#
#XHMM
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install XHMM"
echo ""
cd $LKSRC_DIR
if [[ ! -e ${LKTOOLS_DIR}/xhmm || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf master.zip
    rm -Rf statgen-xhmm-*
    rm -Rf $LKTOOLS_DIR/xhmm

    rm -Rf plinkseq-*
    rm -Rf $LKTOOLS_DIR/pseq

    wget http://psychgen.u.hpc.mssm.edu/plinkseq_downloads/plinkseq-x86_64-latest.zip
    unzip plinkseq-x86_64-latest.zip
    install plinkseq-0.10/pseq $LKTOOLS_DIR/

    wget https://bitbucket.org/statgen/xhmm/get/master.zip
    unzip master.zip
    cd statgen-xhmm-*
    install ./sources/scripts/interval_list_to_pseq_reg $LKTOOLS_DIR/
    make
    make R

    #in R:
    #install.packages("xhmmScripts")

    install xhmm $LKTOOLS_DIR/
else
    echo "Already installed"
fi


#
#pindel
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install pindel"
echo ""
cd $LKSRC_DIR
if [[ ! -e ${LKTOOLS_DIR}/pindel || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf pindel*
    rm -Rf $LKTOOLS_DIR/pindel

    git clone git://github.com/genome/pindel.git
    cd pindel

    wget --read-timeout=10 http://downloads.sourceforge.net/project/samtools/samtools/1.2/samtools-1.2.tar.bz2
    bunzip2 samtools-1.2.tar.bz2
    tar -xf samtools-1.2.tar
    echo "Compressing TAR"
    bzip2 samtools-1.2.tar
    cd samtools-1.2
    make
    cd ./htslib-1.2.1
    ./configure --prefix=$LKTOOLS_DIR/htslib
    make
    make install

    cd ../../

    #hack to fix issue w/ shared libraries.  remove this if it becomes incorporated into pindel
    sed -i 's/-L$(realpath $(HTSLIB))/-L$(realpath $(HTSLIB)) -Wl,-rpath $(realpath $(HTSLIB))/' Makefile
    sed -i 's/-L$(realpath $(HTSLIB)\/lib)/-L$(realpath $(HTSLIB)\/lib) -Wl,-rpath $(realpath $(HTSLIB)\/lib)/' Makefile
    ./INSTALL ${LKTOOLS_DIR}/htslib

    install pindel $LKTOOLS_DIR
    install pindel2vcf $LKTOOLS_DIR

else
    echo "Already installed"
fi


#
#DELLY
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install DELLY"
echo ""
cd $LKSRC_DIR
if [[ ! -e ${LKTOOLS_DIR}/delly || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf delly*
    rm -Rf $LKTOOLS_DIR/delly

    git clone --recursive https://github.com/tobiasrausch/delly.git
    cd delly
    make all

    install delly $LKTOOLS_DIR
else
    echo "Already installed"
fi