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
LK_HOME=

while getopts "d:f" arg;
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
LKSRC_DIR=${LKTOOLS_DIR}/src
mkdir -p $LKSRC_DIR
mkdir -p $LKTOOLS_DIR

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install location"
echo ""
echo "LKTOOLS_DIR: $LKTOOLS_DIR"
echo "LKSRC_DIR: $LKSRC_DIR"


#
#MORGAN
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install MORGAN"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/MORGAN || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf morgan32_release*
    rm -Rf MORGAN_V32_Release
    rm -Rf $LKTOOLS_DIR/MORGAN

    wget http://faculty.washington.edu/eathomp/Anonftp/PANGAEA/MORGAN/morgan32_release.tar.gz
    gunzip morgan32_release.tar.gz
    tar -xf morgan32_release.tar
    echo "Compressing TAR"
    gzip morgan32_release.tar
    cd MORGAN_V32_Release
    make morgan

    cd $LKTOOLS_DIR
    ln -s ./src/MORGAN_V32_Release MORGAN
else
    echo "Already installed"
fi


#
#GIGI
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install GIGI"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/GIGI || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf GIGI_v1.06.1*
    rm -Rf $LKTOOLS_DIR/GIGI
    rm -Rf __MACOSX*

    wget https://faculty.washington.edu/wijsman/progdists/gigi/software/GIGI/GIGI_v1.06.1.zip
    unzip GIGI_v1.06.1.zip
    cd GIGI_v1.06.1
    make

    cd $LKTOOLS_DIR
    ln -s ./src/GIGI_v1.06.1/GIGI GIGI
else
    echo "Already installed"
fi


#
# PARalyzer
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install PARalyzer"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/PARalyzer || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf PARalyzer_v1_5*
    rm -Rf $LKTOOLS_DIR/PARalyzer

    wget --no-check-certificate https://ohlerlab.mdc-berlin.de/files/duke/PARalyzer/PARalyzer_v1_5.tar.gz
    gunzip PARalyzer_v1_5.tar.gz
    tar -xf PARalyzer_v1_5.tar
    gzip PARalyzer_v1_5.tar

    cd $LKTOOLS_DIR
    ln -s ./src/PARalyzer_v1_5/PARalyzer PARalyzer
else
    echo "Already installed"
fi


#
# bwa
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install miRDeep2"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/miRDeep2.pl || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf mirdeep2_0_0_7*
    rm -Rf $LKTOOLS_DIR/miRDeep2.pl

    wget --no-check-certificate https://www.mdc-berlin.de/43969303/en/research/research_teams/systems_biology_of_gene_regulatory_elements/projects/miRDeep/mirdeep2_0_0_7.zip
    unzip mirdeep2_0_0_7.zip

    cd $LKTOOLS_DIR
    ln -s ./src/mirdeep2_0_0_7/miRDeep2.pl miRDeep2.pl
else
    echo "Already installed"
fi


#
# GATK
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install GATK"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/Queue.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf ${LKTOOLS_DIR}/Queue.jar
    rm -Rf ${LKTOOLS_DIR}/GenomeAnalysisTK.jar
    rm -Rf ${LKSRC_DIR}/gatk

    mkdir -p gatk
    cd gatk

    echo "Downloading GATK from GIT"
    #git clone git://github.com/broadgsa/gatk.git
    git clone git://github.com/broadgsa/gatk-protected.git

    #this is a custom extension
    svn export https://github.com/NationalGenomicsInfrastructure/piper/trunk/src/main/scala/molmed/queue/engine/parallelshell
    sed -i 's/molmed.queue.engine.parallelshell/org.broadinstitute.gatk.queue.engine.parallelshell/' parallelshell/*
    mv parallelshell ./gatk-protected/public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/

    cd gatk-protected

    #remove due to compilation error
    rm ./public/external-example/src/main/java/org/mycompany/app/*
    rm ./public/external-example/src/test/java/org/mycompany/app/*

    mvn verify
    mvn package
    cp ./protected/gatk-package-distribution/target/gatk-package-distribution-3.3.jar ${LKTOOLS_DIR}/GenomeAnalysisTK.jar
    cp ./protected/gatk-queue-package-distribution/target/gatk-queue-package-distribution-3.3.jar ${LKTOOLS_DIR}/Queue.jar
fi

