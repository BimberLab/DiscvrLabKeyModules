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
    rm -Rf morgan_v3*
    rm -Rf MORGAN_V*
    rm -Rf $LKTOOLS_DIR/MORGAN

    wget http://faculty.washington.edu/eathomp/Anonftp/PANGAEA/MORGAN/morgan_v332_release.tar.gz
    gunzip morgan_v332_release.tar.gz
    tar -xf morgan_v332_release.tar
    echo "Compressing TAR"
    gzip morgan_v332_release.tar
    cd MORGAN_V332_Release
    make morgan

    cd ../
    cp -R ./MORGAN_V332_Release $LKTOOLS_DIR/MORGAN
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
    cd ../

    install ./GIGI_v1.06.1/GIGI $LKTOOLS_DIR/GIGI
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

    install ./PARalyzer_v1_5/PARalyzer $LKTOOLS_DIR/PARalyzer
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

if [[ ! -e ${LKTOOLS_DIR}/miRDeep2 || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf mirdeep2_0_0_7*
    rm -Rf $LKTOOLS_DIR/miRDeep2.pl

    wget --no-check-certificate https://www.mdc-berlin.de/43969303/en/research/research_teams/systems_biology_of_gene_regulatory_elements/projects/miRDeep/mirdeep2_0_0_7.zip
    unzip mirdeep2_0_0_7.zip

    cp -R ./mirdeep2_0_0_7 $LKTOOLS_DIR/miRDeep2
else
    echo "Already installed"
fi

##
##Mira
##
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Install Mira Assembler"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/mira || ! -z $FORCE_REINSTALL ]];
#then
#    echo "Cleaning up previous installs"
#    rm -Rf mira_4.0rc4_linux-gnu_x86_64*
#    rm -Rf mira_4.0.2_linux-gnu_x86_64*
#    rm -Rf mira-4.0*
#
#    rm -Rf $LKTOOLS_DIR/mira
#    rm -Rf $LKTOOLS_DIR/miraconvert
#
#    wget http://downloads.sourceforge.net/project/mira-assembler/MIRA/stable/mira_4.0.2_linux-gnu_x86_64_static.tar.bz2
#    bunzip2 mira_4.0.2_linux-gnu_x86_64_static.tar.bz2
#    tar -xf mira_4.0.2_linux-gnu_x86_64_static.tar
#    echo "Compressing TAR"
#    bzip2 mira_4.0.2_linux-gnu_x86_64_static.tar
#    cd mira_4.0.2_linux-gnu_x86_64_static
#
#    cd $LKTOOLS_DIR
#    ln -s ./src/mira_4.0.2_linux-gnu_x86_64_static/bin/mira mira
#    ln -s ./src/mira_4.0.2_linux-gnu_x86_64_static/bin/miraconvert miraconvert
#else
#    echo "Already installed"
#fi

##
##velvet
##
#
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Install velvet"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/velvetg || ! -z $FORCE_REINSTALL ]];
#then
#    echo "Cleaning up previous installs"
#    rm -Rf velvet_1.2.09.tgz
#    rm -Rf velvet_1.2.09.tar.gz
#    rm -Rf velvet_1.2.09.tar
#    rm -Rf velvet_1.2.09
#    rm -Rf $LKTOOLS_DIR/velvetg
#    rm -Rf $LKTOOLS_DIR/velveth
#
#    wget http://www.ebi.ac.uk/~zerbino/velvet/velvet_1.2.09.tgz
#    gunzip velvet_1.2.09.tgz
#    tar -xf velvet_1.2.09.tar
#    echo "Compressing TAR"
#    gzip velvet_1.2.09.tar
#    cd velvet_1.2.09
#    make OPENMP=1 LONGSEQUENCES=1
#
#    cd $LKTOOLS_DIR
#    ln -s ./src/velvet_1.2.09/velvetg velvetg
#    ln -s ./src/velvet_1.2.09/velveth velveth
#else
#    echo "Already installed"
#fi

##
##VelvetOptimiser
##
#
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Installing VelvetOptimiser"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/VelvetOptimiser.pl || ! -z $FORCE_REINSTALL ]];
#then
#    rm -Rf VelvetOptimiser-2.2.5.tar.gz
#    rm -Rf VelvetOptimiser-2.2.5.tar
#    rm -Rf VelvetOptimiser-2.2.5
#    rm -Rf $LKTOOLS_DIR/VelvetOptimiser.pl
#
#    wget http://www.vicbioinformatics.com/VelvetOptimiser-2.2.5.tar.gz
#    gunzip VelvetOptimiser-2.2.5.tar.gz
#    tar -xf VelvetOptimiser-2.2.5.tar
#    gzip VelvetOptimiser-2.2.5.tar
#    cd VelvetOptimiser-2.2.5
#
#    cd $LKTOOLS_DIR
#    ln -s ./src/VelvetOptimiser-2.2.5/VelvetOptimiser.pl VelvetOptimiser.pl
#else
#    echo "Already installed"
#fi

##
##AMOS
##
#
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Installing AMOS"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/bank-transact || ! -z $FORCE_REINSTALL ]];
#then
#    rm -Rf amos-3.1.0.tar.gz
#    rm -Rf amos-3.1.0.tar
#    rm -Rf amos-3.1.0
#    rm -Rf $LKTOOLS_DIR/bank2fasta
#    rm -Rf $LKTOOLS_DIR/bank2contig
#    rm -Rf $LKTOOLS_DIR/bank-transact
#
#    wget http://downloads.sourceforge.net/project/amos/amos/3.1.0/amos-3.1.0.tar.gz
#    gunzip amos-3.1.0.tar.gz
#    tar -xf amos-3.1.0.tar
#    cd amos-3.1.0
#    ./configure
#    make
#    make install
#
#    cd $LKTOOLS_DIR
#    ln -s ./src/amos-3.1.0/bin/bank2fasta bank2fasta
#    ln -s ./src/amos-3.1.0/bin/bank2contig bank2contig
#    ln -s ./src/amos-3.1.0/bin/bank-transact bank-transact
#else
#    echo "Already installed"
#fi

#
# htseq
#
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Install htseq"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/htseq || ! -z $FORCE_REINSTALL ]];
#then
#    echo "Cleaning up previous installs"
#    rm -Rf STAR_2.4*
#    rm -Rf $LKTOOLS_DIR/STAR
#
#    if [ -n $SKIP_PACKAGE_MANAGER ]; then
#        echo "Skipping package install"
#    elif [ $(which apt-get) ]; then
#        apt-get install build-essential python2.7-dev python-numpy python-matplotlib
#    elif [ $(which yum) ]; then
#        yum install python-devel numpy python-matplotlib
#    fi
#
#    wget https://pypi.python.org/packages/source/H/HTSeq/HTSeq-0.6.1.tar.gz
#    gunzip HTSeq-0.6.1.tar.gz
#    tar -xf HTSeq-0.6.1.tar
#    gzip HTSeq-0.6.1.tar
#
#    cd HTSeq-0.6.1
#    python setup.py install --user
#else
#    echo "Already installed"
#fi

