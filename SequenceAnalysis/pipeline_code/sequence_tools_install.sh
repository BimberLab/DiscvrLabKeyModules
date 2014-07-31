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
# If you encounter issues, please contact bimber -at- ohsu.edu
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
# Install required software
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install Required packages via the package manager"
echo ""

# can install EPEL to get additional repositories if necessary.  May be needed on RHEL/CentOS
#OS=`cat /etc/redhat-release | awk {'print $1}'`
#if [ "$OS" = "CentOS" ]
#then
#    if [[ ! -e ${LKSRC_DIR}/epel-release-6-8.noarch.rpm ]];
#    then
#        cd $LKSRC_DIR
#        wget http://dl.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
#        wget http://rpms.famillecollet.com/enterprise/remi-release-6.rpm
#        rpm -Uvh remi-release-6*.rpm epel-release-6*.rpm
#    fi
#fi

if [ $(which yum) ]; then
    echo "Using Yum"
    yum -y install zip unzip gcc bzip2-devel gcc-c++ libstdc++ libstdc++-devel glibc-devel boost-devel ncurses-devel libgtextutils libgtextutils-devel python-devel openssl-devel glibc-devel.i686 glibc-static.i686 glibc-static.x86_64 expat expat-devel subversion cpan git cmake liblzf-devel
elif [ $(which apt-get) ]; then
    echo "Using apt-get"
    apt-get -q -y install libc6 libc6-dev libncurses5-dev libtcmalloc-minimal0 libgtextutils-dev python-dev unzip zip ncftp gcc make perl libssl-dev libgcc1 libstdc++6 zlib1g zlib1g-dev libboost-all-dev python-numpy python-scipy libexpat1-dev libgtextutils-dev pkg-config subversion flex subversion libgoogle-perftools-dev perl-doc git cmake
else
    echo "No known package manager present, aborting"
    exit 1
fi


#
# bwa
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install BWA"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bwa || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf bwa-0.6.2*
    rm -Rf bwa-0.7.9a*
    rm -Rf $LKTOOLS_DIR/bwa

    wget http://downloads.sourceforge.net/project/bio-bwa/bwa-0.7.9a.tar.bz2
    bunzip2 bwa-0.7.9a.tar.bz2
    tar -xf bwa-0.7.9a.tar
    bzip2 bwa-0.7.9a.tar
    cd bwa-0.7.9a
    make CFLAGS=-msse2
    ln -s $LKSRC_DIR/bwa-0.7.9a/bwa $LKTOOLS_DIR
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
mkdir -p GATK
cd GATK

if [[ ! -e ${LKSRC_DIR}/GATK || ! -z $FORCE_REINSTALL ]];
then
    echo "Downloading GATK from GIT"
    #git clone git://github.com/broadgsa/gatk.git
    #git clone git://github.com/broadgsa/gatk-protected.git
else
    echo "Updating GATK from GIT"
    #git pull
fi


#
# GSNAP
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install GSNAP"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/gsnap || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf gmap-gsnap-2014-05-15*
    rm -Rf gmap-2014-05-15*
    rm -Rf $LKTOOLS_DIR/gsnap

    wget http://research-pub.gene.com/gmap/src/gmap-gsnap-2014-05-15.v3.tar.gz
    gunzip gmap-gsnap-2014-05-15.v3.tar.gz
    tar -xf gmap-gsnap-2014-05-15.v3.tar
    gzip gmap-gsnap-2014-05-15.v3.tar
    cd gmap-2014-05-15
    ./configure
    make
    ln -s $LKSRC_DIR/gmap-2014-05-15/src/gmap $LKTOOLS_DIR
    ln -s $LKSRC_DIR/gmap-2014-05-15/src/gsnap $LKTOOLS_DIR
else
    echo "Already installed"
fi


#
#mosaik
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install mosaik"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/MosaikAligner || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf MOSAIK-2.1.73-binary*
    rm -Rf $LKTOOLS_DIR/MosaikAligner
    rm -Rf $LKTOOLS_DIR/MosaikBuild
    rm -Rf $LKTOOLS_DIR/MosaikJump
    rm -Rf $LKTOOLS_DIR/MosaikText

    wget http://mosaik-aligner.googlecode.com/files/MOSAIK-2.1.73-binary.tar
    tar -xf MOSAIK-2.1.73-binary.tar
    echo "Compressing TAR"
    gzip MOSAIK-2.1.73-binary.tar

    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikAligner $LKTOOLS_DIR/MosaikAligner
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikBuild $LKTOOLS_DIR/MosaikBuild
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikJump $LKTOOLS_DIR/MosaikJump
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikText $LKTOOLS_DIR/MosaikText
fi

#also download src to get the networkFiles
echo "Download mosaik network files"
echo ""
if [[ ! -e ${LKTOOLS_DIR}/mosaikNetworkFile || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf MOSAIK-2.1.73-source*
    rm -Rf $LKTOOLS_DIR/networkFile
    rm -Rf $LKTOOLS_DIR/mosaikNetworkFile

    cd $LKSRC_DIR

    wget http://mosaik-aligner.googlecode.com/files/MOSAIK-2.1.73-source.tar
    tar -xf MOSAIK-2.1.73-source.tar
    echo "Compressing TAR"
    gzip MOSAIK-2.1.73-source.tar

    ln -s $LKSRC_DIR/MOSAIK-2.1.73-source/networkFile $LKTOOLS_DIR/mosaikNetworkFile
else
    echo "Mosaik network files already downloaded"
fi


#
#samtools
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install samtools"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/samtools || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf samtools-0.1.18*
    rm -Rf $LKTOOLS_DIR/samtools

    wget http://downloads.sourceforge.net/project/samtools/samtools/0.1.18/samtools-0.1.18.tar.bz2
    bunzip2 samtools-0.1.18.tar.bz2
    tar -xf samtools-0.1.18.tar
    echo "Compressing TAR"
    bzip2 samtools-0.1.18.tar
    cd samtools-0.1.18
    #note: this is used later by Bio::DB::Samtools
    make CXXFLAGS=-fPIC CFLAGS=-fPIC CPPFLAGS=-fPIC
    ln -s $LKSRC_DIR/samtools-0.1.18/samtools $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#tabix
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install tabix"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/tabix || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf tabix-0.2.6*
    rm -Rf $LKTOOLS_DIR/tabix
    rm -Rf $LKTOOLS_DIR/bgzip

    wget wget http://downloads.sourceforge.net/project/samtools/tabix/tabix-0.2.6.tar.bz2
    bunzip2 tabix-0.2.6.tar.bz2
    tar -xf tabix-0.2.6.tar
    echo "Compressing TAR"
    bzip2 tabix-0.2.6.tar
    cd tabix-0.2.6
    make
    ln -s $LKSRC_DIR/tabix-0.2.6/tabix $LKTOOLS_DIR
    ln -s $LKSRC_DIR/tabix-0.2.6/bgzip $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#blat
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install blat"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/blat || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf blat
    rm -Rf $LKTOOLS_DIR/blat

    mkdir blat
    cd blat
    wget http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64/blat/blat
    chmod +x blat
    ln -s $LKSRC_DIR/blat/blat $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#lastz
#
# In order to install lastz, we need to make a modification to the Makefile in
# $LKSRC_DIR/src. These changes are required due to changes in GCC in v4.6 and
# later. If this change is not made and you use GCC 4.6 or later the make
# command will fail.
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install lastz"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/lastz || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf lastz-1.02.00*
    rm -Rf $LKTOOLS_DIR/lastz

    wget http://www.bx.psu.edu/miller_lab/dist/lastz-1.02.00.tar.gz
    gunzip lastz-1.02.00.tar.gz
    tar -xf lastz-1.02.00.tar
    echo "Compressing TAR"
    gzip lastz-1.02.00.tar
    cd lastz-distrib-1.02.00
    mv src/Makefile src/Makefile.dist
    sed 's/-Werror //g' src/Makefile.dist > src/Makefile
    make
    ln -s $LKSRC_DIR/lastz-distrib-1.02.00/src/lastz $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#fastx-toolkit
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install fastx-toolkit"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/fastq_masker || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf fastx_toolkit-0.0.13.2*

    #this should not be required with this script
    #wget http://cancan.cshl.edu/labmembers/gordon/files/libgtextutils-0.6.tar.bz2
    #tar -xjf libgtextutils-0.6.tar.bz2
    #cd libgtextutils-0.6
    #./configure
    #make
    #make install

    wget http://hannonlab.cshl.edu/fastx_toolkit/fastx_toolkit-0.0.13.2.tar.bz2
    bunzip2 fastx_toolkit-0.0.13.2.tar.bz2
    tar -xf fastx_toolkit-0.0.13.2.tar
    echo "Compressing TAR"
    bzip2 fastx_toolkit-0.0.13.2.tar
    cd fastx_toolkit-0.0.13.2
    ./configure --prefix=$LK_HOME
    make
    make install
else
    echo "Already installed"
fi

#
#picard
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install picard"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/picard-tools || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf picard-tools-1.77*
    rm -Rf picard-tools-1.96*
    rm -Rf picard-tools-1.114*
    rm -Rf snappy-java-1.0.3-rc3.jar
    rm -Rf $LKTOOLS_DIR/picard-tools

    wget http://downloads.sourceforge.net/project/picard/picard-tools/1.114/picard-tools-1.114.zip
    unzip picard-tools-1.114.zip
    ln -s $LKSRC_DIR/picard-tools-1.114 $LKTOOLS_DIR/picard-tools
else
    echo "Already installed"
fi

#
#bowtie
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bowtie"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bowtie || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"

    #old version
    rm -Rf bowtie-0.12.8*
    rm -Rf bowtie-1.0.1*
    rm -Rf $LKTOOLS_DIR/bowtie
    rm -Rf $LKTOOLS_DIR/bowtie-build

    wget http://downloads.sourceforge.net/project/bowtie-bio/bowtie/1.0.1/bowtie-1.0.1-linux-x86_64.zip
    unzip bowtie-1.0.1-linux-x86_64.zip
    cd bowtie-1.0.1

    ln -s $LKSRC_DIR/bowtie-1.0.1/bowtie $LKTOOLS_DIR
    ln -s $LKSRC_DIR/bowtie-1.0.1/bowtie-build $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#cutadapt
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install cutadapt"
echo ""
cd $LKSRC_DIR

if [[ $(which cutadapt) || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"

    if [ $(which apt-get) ]; then
        apt-get -q -y install python-pip
    elif [ $(which yum) ]; then
        yum -y install python-pip
    fi

    pip install cutadapt
else
    echo "Already installed"
fi

#
#bfast
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bfast"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bfast || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf bfast-0.7.0a.tar.gz
    rm -Rf bfast-0.7.0a.tar
    rm -Rf bfast-0.7.0a
    rm -Rf $LKTOOLS_DIR/bfast

    wget http://downloads.sourceforge.net/project/bfast/bfast/0.7.0/bfast-0.7.0a.tar.gz
    gunzip bfast-0.7.0a.tar.gz
    tar -xf bfast-0.7.0a.tar
    echo "Compressing TAR"
    gzip bfast-0.7.0a.tar
    cd bfast-0.7.0a
    ./configure
    make
    ln -s $LKSRC_DIR/bfast-0.7.0a/bfast/bfast $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#cap3
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install cap3"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/cap3 || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf cap3.linux.opteron64.tar.gz
    rm -Rf cap3.linux.opteron64.tar
    rm -Rf CAP3
    rm -Rf $LKTOOLS_DIR/cap3

    wget http://seq.cs.iastate.edu/CAP3/cap3.linux.opteron64.tar
    tar -xf cap3.linux.opteron64.tar
    echo "Compressing TAR"
    gzip cap3.linux.opteron64.tar
    ln -s $LKSRC_DIR/CAP3/cap3 $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#biopython
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install biopython"
echo ""
cd $LKSRC_DIR

if [ $(which apt-get) ]; then
    echo "Installing biopython using apt-get"
    apt-get -q -y install python-biopython
#TODO: this is not available from standard yum repositories
#elif [ $(which yum) ]; then
#    echo "Installing biopython using yum"
#    yum -y install python-biopython
else
    if [[ ! -e biopython-1.60 || ! -z $FORCE_REINSTALL ]];then
        echo "Installing biopython manually"
        echo "Cleaning up previous installs"
        rm -Rf biopython-1.60.tar.gz
        rm -Rf biopython-1.60.tar
        rm -Rf biopython-1.60

        wget http://biopython.org/DIST/biopython-1.60.tar.gz
        gunzip biopython-1.60.tar.gz
        tar -xf biopython-1.60.tar
        echo "Compressing TAR"
        gzip biopython-1.60.tar
        cd biopython-1.60
        python setup.py build
        python setup.py test
        python setup.py install
    else
        echo "Already installed"
    fi
fi

#
#seq_crumbs
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install seq_crumbs"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/sff_extract || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf seq_crumbs-0.1.6*
    rm -Rf seq_crumbs-0.1.8*
    rm -Rf $LKTOOLS_DIR/sff_extract
    rm -Rf $LKTOOLS_DIR/convert_format

    wget http://bioinf.comav.upv.es/downloads/seq_crumbs-0.1.8-x64-linux.tar.gz
    gunzip seq_crumbs-0.1.8-x64-linux.tar.gz
    tar -xf seq_crumbs-0.1.8-x64-linux.tar
    echo "Compressing TAR"
    gzip seq_crumbs-0.1.8-x64-linux.tar
    cd seq_crumbs-0.1.8-x64-linux
    ln -s $LKSRC_DIR/seq_crumbs-0.1.8-x64-linux/sff_extract $LKTOOLS_DIR
    ln -s $LKSRC_DIR/seq_crumbs-0.1.8-x64-linux/convert_format $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#FLASH
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install FLASH"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/flash || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf FLASH-1.2.7.tar.gz
    rm -Rf FLASH-1.2.7.tar
    rm -Rf FLASH-1.2.7
    rm -Rf $LKTOOLS_DIR/flash

    wget http://downloads.sourceforge.net/project/flashpage/FLASH-1.2.7.tar.gz
    gunzip FLASH-1.2.7.tar.gz
    tar -xf FLASH-1.2.7.tar
    echo "Compressing TAR"
    gzip FLASH-1.2.7.tar
    cd FLASH-1.2.7
    make
    ln -s $LKSRC_DIR/FLASH-1.2.7/flash $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#Mira
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install Mira Assembler"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/mira || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf mira_4.0rc4_linux-gnu_x86_64*
    rm -Rf mira_4.0.2_linux-gnu_x86_64*
    rm -Rf mira-4.0*

    rm -Rf $LKTOOLS_DIR/mira
    rm -Rf $LKTOOLS_DIR/miraconvert

    wget http://downloads.sourceforge.net/project/mira-assembler/MIRA/stable/mira_4.0.2_linux-gnu_x86_64_static.tar.bz2
    bunzip2 mira_4.0.2_linux-gnu_x86_64_static.tar.bz2
    tar -xf mira_4.0.2_linux-gnu_x86_64_static.tar
    echo "Compressing TAR"
    bzip2 mira_4.0.2_linux-gnu_x86_64_static.tar
    cd mira_4.0.2_linux-gnu_x86_64_static

    ln -s $LKSRC_DIR/mira_4.0.2_linux-gnu_x86_64_static/bin/mira $LKTOOLS_DIR
    ln -s $LKSRC_DIR/mira_4.0.2_linux-gnu_x86_64_static/bin/miraconvert $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#velvet
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install velvet"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/velvetg || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf velvet_1.2.09.tgz
    rm -Rf velvet_1.2.09.tar.gz
    rm -Rf velvet_1.2.09.tar
    rm -Rf velvet_1.2.09
    rm -Rf $LKTOOLS_DIR/velvetg
    rm -Rf $LKTOOLS_DIR/velveth

    wget http://www.ebi.ac.uk/~zerbino/velvet/velvet_1.2.09.tgz
    gunzip velvet_1.2.09.tgz
    tar -xf velvet_1.2.09.tar
    echo "Compressing TAR"
    gzip velvet_1.2.09.tar
    cd velvet_1.2.09
    make OPENMP=1 LONGSEQUENCES=1
    ln -s $LKSRC_DIR/velvet_1.2.09/velvetg $LKTOOLS_DIR
    ln -s $LKSRC_DIR/velvet_1.2.09/velveth $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#VelvetOptimiser
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing VelvetOptimiser"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/VelvetOptimiser.pl || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf VelvetOptimiser-2.2.5.tar.gz
    rm -Rf VelvetOptimiser-2.2.5.tar
    rm -Rf VelvetOptimiser-2.2.5
    rm -Rf $LKTOOLS_DIR/VelvetOptimiser.pl

    wget http://www.vicbioinformatics.com/VelvetOptimiser-2.2.5.tar.gz
    gunzip VelvetOptimiser-2.2.5.tar.gz
    tar -xf VelvetOptimiser-2.2.5.tar
    gzip VelvetOptimiser-2.2.5.tar
    cd VelvetOptimiser-2.2.5
    ln -s $LKSRC_DIR/VelvetOptimiser-2.2.5/VelvetOptimiser.pl $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#AMOS
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing AMOS"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bank-transact || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf amos-3.1.0.tar.gz
    rm -Rf amos-3.1.0.tar
    rm -Rf amos-3.1.0
    rm -Rf $LKTOOLS_DIR/bank2fasta
    rm -Rf $LKTOOLS_DIR/bank2contig
    rm -Rf $LKTOOLS_DIR/bank-transact

    wget http://downloads.sourceforge.net/project/amos/amos/3.1.0/amos-3.1.0.tar.gz
    gunzip amos-3.1.0.tar.gz
    tar -xf amos-3.1.0.tar
    cd amos-3.1.0
    ./configure
    make
    make install

    ln -s $LKSRC_DIR/amos-3.1.0/bin/bank2fasta $LKTOOLS_DIR
    ln -s $LKSRC_DIR/amos-3.1.0/bin/bank2contig $LKTOOLS_DIR
    ln -s $LKSRC_DIR/amos-3.1.0/bin/bank-transact $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#varscan2
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing Varscan2"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/VarScan2.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf VarScan.v2.3.6.jar
    rm -Rf $LKTOOLS_DIR/VarScan2.jar

    wget http://downloads.sourceforge.net/project/varscan/VarScan.v2.3.6.jar
    ln -s $LKSRC_DIR/VarScan.v2.3.6.jar $LKTOOLS_DIR/VarScan2.jar
else
    echo "Already installed"
fi


#

#
#variscan
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing Variscan"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/variscan || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf variscan-2.0.3.tar.gz
    rm -Rf variscan-2.0.3.tar
    rm -Rf variscan-2.0.3
    rm -Rf $LKTOOLS_DIR/variscan

    #NOTE: -U seems required to avoid a 403 error
    wget -U firefox http://www.ub.es/softevol/variscan/variscan-2.0.3.tar.gz
    gunzip variscan-2.0.3.tar.gz
    tar -xf variscan-2.0.3.tar
    ln -s $LKSRC_DIR/variscan-2.0.3/bin/Linux-i386/variscan $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#clustalw
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing ClustalW"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/clustalw2 || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf clustalw-2.1.tar.gz
    rm -Rf clustalw-2.1.tar
    rm -Rf clustalw-2.1
    rm -Rf $LKTOOLS_DIR/clustalw2

    wget http://www.clustal.org/download/current/clustalw-2.1.tar.gz
    gunzip clustalw-2.1.tar.gz
    tar -xf clustalw-2.1.tar
    gzip clustalw-2.1.tar
    cd clustalw-2.1
    ./configure
    make

    ln -s $LKSRC_DIR/clustalw-2.1/src/clustalw2 $LKTOOLS_DIR

else
    echo "Already installed"
fi


#
#muscle
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing MUSCLE"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/muscle || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf muscle3.8.31_i86linux64.tar.gz
    rm -Rf muscle3.8.31_i86linux64.tar
    rm -Rf muscle3.8.31_i86linux64
    rm -Rf $LKTOOLS_DIR/muscle

    wget http://www.drive5.com/muscle/downloads3.8.31/muscle3.8.31_i86linux64.tar.gz
    gunzip muscle3.8.31_i86linux64.tar.gz
    tar -xf muscle3.8.31_i86linux64.tar
    gzip muscle3.8.31_i86linux64.tar

    ln -s $LKSRC_DIR/muscle3.8.31_i86linux64 $LKTOOLS_DIR/muscle

else
    echo "Already installed"
fi


#
#muscle
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing Trimmomatic"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/trimmomatic.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf Trimmomatic-0.32*
    rm -Rf $LKTOOLS_DIR/trimmomatic.jar

    wget http://www.usadellab.org/cms/uploads/supplementary/Trimmomatic/Trimmomatic-0.32.zip
    unzip Trimmomatic-0.32.zip
    cd Trimmomatic-0.32

    ln -s $LKSRC_DIR/Trimmomatic-0.32/trimmomatic-0.32.jar $LKTOOLS_DIR/trimmomatic.jar

else
    echo "Already installed"
fi


#
# V-Phaser 2
#

#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Installing V-Phaser 2"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/variant_caller || ! -z $FORCE_REINSTALL ]];
#then
#    rm -Rf bamtools*
#    rm -Rf v_phaser_2*
#    rm -Rf VPhaser-2*
#    rm -Rf $LKTOOLS_DIR/variant_caller
#
#    echo "First download/install bamtools"
#    #mkdir bamtools
#    #cd bamtools
#    git clone git://github.com/pezmaster31/bamtools.git
#    cd bamtools/src
#    cmake ..
#    make all -lz
#    ln -s ${LKSRC_DIR}/bamtools/lib/libbamtools.so.2.3.0 /usr/lib/
#
#    echo "now download VPhaser2"
#    cd ../../
#    wget http://www.broadinstitute.org/software/viral/v_phaser_2/v_phaser_2.zip
#    unzip v_phaser_2.zip
#    cd VPhaser-2-02112013/src
#
#    #NOTE: I am adding this line so the tools will compile.  If a future version fixes this, it should be reverted
#    sed '24i\#include <climits>' bam_manip.h > bam_manip.h.tmp
#    rm bam_manip.h
#    mv bam_manip.h.tmp bam_manip.h
#
#    make MYPATH=${LKSRC_DIR}/bamtools
#
#    ln -s $LKSRC_DIR/VPhaser-2-02112013/bin/variant_caller $LKTOOLS_DIR/variant_caller
#
#else
#    echo "Already installed"
#fi


#
# LoFreq
#
#
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Installing LoFreq"
#echo ""
#cd $LKSRC_DIR
#
#if [[ $(which apt-get) || ! -z $FORCE_REINSTALL ]];
#then
#    rm -Rf lofreq-0.6.1*
#    rm -Rf lofreq_star-2.0.0-rc-1*
#    rm -Rf $LKTOOLS_DIR/lofreq
#
#    wget http://downloads.sourceforge.net/project/lofreq/lofreq_star-2.0.0-rc-1.linux-x86-64.tar.gz
#    gunzip lofreq_star-2.0.0-rc-1.linux-x86-64.tar.gz
#    tar -xf lofreq_star-2.0.0-rc-1.linux-x86-64.tar
#    gzip lofreq_star-2.0.0-rc-1.linux-x86-64.tar
#
#    cd lofreq_star-2.0.0-rc-1
#    bash binary_installer.sh
#else
#    echo "Already installed"
#fi


#
#emboss
#

#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Installing EMBOSS"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/seqret || ! -z $FORCE_REINSTALL ]];
#then
#    rm -Rf EMBOSS-6.6.0.tar.gz
#    rm -Rf EMBOSS-6.6.0.tar
#    rm -Rf EMBOSS-6.6.0
#    rm -Rf $LKTOOLS_DIR/seqret
#
#    wget ftp://ftp.ebi.ac.uk/pub/software/unix/EMBOSS/EMBOSS-6.6.0.tar.gz
#    gunzip EMBOSS-6.6.0.tar.gz
#    tar -xf EMBOSS-6.6.0.tar
#    gzip EMBOSS-6.6.0.tar
#
#    cd EMBOSS-6.6.0.tar
#    ./configure
#    make
#
#    ln -s $LKSRC_DIR/seqret $LKTOOLS_DIR/EMBOSS-6.6.0/emboss/seqret
#
#else
#    echo "Already installed"
#fi

##
##misc perl modules
##
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Install All required Perl modules"
#echo ""
#
## preferentially use apt-get when available
#if [ $(which apt-get) ];
#then
#    apt-get -q -y install perl-base libclass-data-inheritable-perl libstatistics-descriptive-perl libgetopt-long-descriptive-perl libxml-sax-perl libxml-writer-perl libxml-xpath-perl libcrypt-ssleay-perl libtest-cpan-meta-perl libtest-warn-perl libproc-processtable-perl libtext-diff-perl liblist-moreutils-perl libtest-exception-perl libjson-perl libyaml-perl libstring-approx-perl libmath-round-perl libalgorithm-diff-perl libfile-homedir-perl libipc-run-perl libipc-run-safehandles-perl libfile-util-perl libarray-compare-perl libset-scalar-perl libsort-naturally-perl libdata-stag-perl libtest-most-perl liburi-perl
#elif [ $(which yum) ];
#then
#    #most packages can be installed using yum
#    yum -y install perl-JSON.noarch perl-YAML.noarch perl-IPC-Run.noarch perl-Class-Data-Inheritable.noarch perl-libxml-perl.noarch perl-XML-SAX.noarch perl-XML-SAX-Writer.noarch perl-XML-Simple.noarch perl-XML-Twig.noarch perl-XML-DOM.noarch perl-XML-DOM-XPath.noarch perl-Crypt-SSLeay.x86_64 perl-Test-Warn.noarch perl-Test-Exception.noarch perl-Test-Simple.x86_64 perl-Text-Diff.noarch perl-List-MoreUtils.x86_64 perl-Algorithm-Diff.noarch perl-File-HomeDir.noarch perl-URI.noarch
#    cpan -i Proc::ProcessTable String::Approx Math::Round File::Util XML::Writer
#else
#    cpan -i JSON YAML IPC::Run Class::Data::Inheritable Statistics::Descriptive XML::Parser::PerlSAX XML::SAX XML::SAX::Writer XML::Simple XML::Twig XML::DOM::XPath Test::CPAN::Meta::JSON Test::Warn Proc::ProcessTable Text::Diff List::Util Test::Exception String::Approx Math::Round Algorithm::Diff File::HomeDir File::Util Array::Compare Set::Scalar Sort::Naturally Data::Stag Test::Most URI XML::Writer
#fi

#this is no longer necessary for the pipeline
#cpan -i LabKey::Query


##
##bioperl
##
#
#if [[ $(perldoc -l Bio::SeqIO) || ! -z $FORCE_REINSTALL ]];
#then
#    echo "BioPerl already installed"
#else
#    if [ $(which apt-get) ];
#    then
#        echo "Installing BioPerl using apt-get"
#        apt-get -q -y install libbio-perl-perl
#    else
#        cpan -i -f BioPerl
#    fi
#fi
#
#if [[ $(perldoc -l Bio::DB::Sam) || ! -z $FORCE_REINSTALL ]];
#then
#    echo "Bio::DB::Sam already installed"
#else
#    if [ $(which apt-get) ];
#    then
#        echo "Installing Bio::DB::Sam using apt-get"
#        apt-get -q -y install libbio-samtools-perl
#    else
#        cpan -i Bio::DB::Sam
#    fi
#fi
#
#if [[ $(perldoc -l Bio::Tools::Run::BWA) || ! -z $FORCE_REINSTALL ]];
#then
#    echo "BioPerl-Run already installed"
#else
#    if [ $(which apt-get) ];
#    then
#        echo "Installing BioPerl-Run using apt-get"
#        apt-get -q -y install bioperl-run
#    else
#        cpan -i -f CJFIELDS/BioPerl-Run-1.006900.tar.gz
#    fi
#fi


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installation is complete"