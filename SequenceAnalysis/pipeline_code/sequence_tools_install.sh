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
set -x

FORCE_REINSTALL=
SKIP_PACKAGE_MANAGER=
CLEAN_SRC=
LK_HOME=
LK_USER=

while getopts "d:u:fpc" arg;
do
  case $arg in
    d)
       LK_HOME=$OPTARG
       LK_HOME=${LK_HOME%/}
       echo "LK_HOME = ${LK_HOME}"
       ;;
    u)
       LK_USER=$OPTARG
       echo "LK_USER = ${LK_USER}"
       ;;
    f)
       FORCE_REINSTALL=1
       ;;
    p)
       SKIP_PACKAGE_MANAGER=1
       echo "SKIP_PACKAGE_MANAGER = ${SKIP_PACKAGE_MANAGER}"
       ;;
    c)
       CLEAN_SRC=1
       echo "CLEAN_SRC = ${CLEAN_SRC}"
       ;;
    *)
       echo "The following arguments are supported:"
       echo "-d: the path to the labkey install, such as /usr/local/labkey.  If only this parameter is provided, tools will be installed in bin/ and src/ under this location."
       echo "-u: optional.  The OS user that will own the downloaded files.  Defaults to labkey"
       echo "-f: optional.  If provided, all tools will be reinstalled, even if already present"
       echo "-p: optional. "
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
WGET_OPTS="--read-timeout=10 --secure-protocol=auto --no-check-certificate -q"

#
# Install required software
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install Required packages via the package manager"
echo ""


if [ ! -z $SKIP_PACKAGE_MANAGER ]; then
    echo "Skipping package install"
elif [ $(which yum) ]; then
    echo "Using Yum"
    yum -y install zip unzip gcc bzip2-devel gcc-c++ libstdc++ libstdc++-devel glibc-devel boost-devel ncurses-devel python-devel openssl-devel glibc-static expat expat-devel subversion cpan git cmake liblzf-devel apache-maven R perl-devel perl-CPAN perl-PerlIO-gzip python-pip
elif [ $(which apt-get) ]; then
    echo "Using apt-get"

    #apt-get -y update
    apt-get -q -y install bzip2 libbz2-dev libc6 libc6-dev libncurses5-dev python3-dev unzip zip ncftp gcc make perl libssl-dev libgcc1 libstdc++6 zlib1g zlib1g-dev libboost-all-dev python3-numpy python3-scipy libexpat1-dev pkg-config subversion flex subversion libgoogle-perftools-dev perl-doc git cmake maven r-base r-cran-rcpp python-pip
else
    echo "No known package manager present, aborting"
    exit 1
fi


for l in $(find ${LKTOOLS_DIR} -type l -exec test ! -e {} \; -print); do
    echo "removing broken symlink: $l";
    rm -Rf $l
done

echo "wget version: "
wget --version

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
    rm -Rf bwa-0.*
    rm -Rf $LKTOOLS_DIR/bwa

    wget $WGET_OPTS -O bwa.zip https://github.com/lh3/bwa/zipball/master/
    unzip bwa.zip
    DIRNAME=`ls | grep lh3-bwa`
    cd $DIRNAME
    make
    install bwa $LKTOOLS_DIR/
else
    echo "Already installed"
fi

#
# gffread
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install gffread"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/gffread || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf gclib*
    rm -Rf gffread*
    rm -Rf $LKTOOLS_DIR/gffread

    git clone https://github.com/gpertea/gclib
    git clone https://github.com/gpertea/gffread
    cd gffread
    make

    install gffread $LKTOOLS_DIR/
else
    echo "Already installed"
fi

#
# subread
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install subread"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/featureCounts || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf subread*
    rm -Rf $LKTOOLS_DIR/featureCounts

    wget $WGET_OPTS https://downloads.sourceforge.net/project/subread/subread-2.0.3/subread-2.0.3-Linux-x86_64.tar.gz
    gunzip subread-2.0.3-Linux-x86_64.tar.gz
    tar -xf subread-2.0.3-Linux-x86_64.tar

    install ./subread-2.0.3-Linux-x86_64/bin/featureCounts $LKTOOLS_DIR/
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
    rm -Rf FLASH-1.2.11*
    rm -Rf $LKTOOLS_DIR/flash

    wget $WGET_OPTS http://ccb.jhu.edu/software/FLASH/FLASH-1.2.11-Linux-x86_64.tar.gz
    gunzip FLASH-1.2.11-Linux-x86_64.tar.gz
    tar -xf FLASH-1.2.11-Linux-x86_64.tar
    echo "Compressing TAR"
    gzip FLASH-1.2.11-Linux-x86_64.tar

    install ./FLASH-1.2.11-Linux-x86_64/flash $LKTOOLS_DIR/
else
    echo "Already installed"
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install DISCVRSeq"
echo ""
cd $LKSRC_DIR
if [[ ! -e ${LKTOOLS_DIR}/DISCVRSeq.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf DISCVRSeq*
    rm -Rf ${LKTOOLS_DIR}/DISCVRSeq.jar

    curl -s https://api.github.com/repos/BimberLab/DISCVRSeq/releases/latest \
    | grep 'browser_download_url.*jar' \
    | cut -d : -f 2,3 \
    | tr -d \" \
    | wget -O DISCVRSeq.jar -qi -

    cp DISCVRSeq.jar ${LKTOOLS_DIR}/DISCVRSeq.jar
fi


#
# GATK4
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install GATK4"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/GenomeAnalysisTK4.jar || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf gatk-4*
    rm -Rf $LKTOOLS_DIR/GenomeAnalysisTK4.jar

    wget $WGET_OPTS https://github.com/broadinstitute/gatk/releases/download/4.4.0.0/gatk-4.4.0.0.zip
    unzip gatk-4.4.0.0.zip

    cp ./gatk-4.4.0.0/gatk-package-4.4.0.0-local.jar $LKTOOLS_DIR/GenomeAnalysisTK4.jar
else
    echo "Already installed"
fi


#
# STAR
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install STAR"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/STAR || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf STAR_2.*
    rm -Rf $LKTOOLS_DIR/STAR

    wget $WGET_OPTS https://github.com/alexdobin/STAR/archive/2.7.10b.tar.gz
    gunzip 2.7.10b.tar.gz
    tar -xf 2.7.10b.tar
    gzip 2.7.10b.tar

    install ./STAR-2.7.10b/bin/Linux_x86_64_static/STAR $LKTOOLS_DIR/STAR
    install ./STAR-2.7.10b/bin/Linux_x86_64_static/STARlong $LKTOOLS_DIR/STARlong
else
    echo "Already installed"
fi


#
# RNA-SeQC
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install RNA-SeQC"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/RNA-SeQC.jar || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf RNA-SeQC*
    rm -Rf $LKTOOLS_DIR/RNA-SeQC.jar

    wget $WGET_OPTS https://data.broadinstitute.org/cancer/cga/tools/rnaseqc/RNA-SeQC_v1.1.8.jar

    install ./RNA-SeQC_v1.1.8.jar $LKTOOLS_DIR/RNA-SeQC.jar
else
    echo "Already installed"
fi


#
# BisSNP
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install BisSNP"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/BisSNP.jar || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf BisSNP*
    rm -Rf $LKTOOLS_DIR/BisSNP.jar

    wget $WGET_OPTS https://downloads.sourceforge.net/project/bissnp/BisSNP-0.82.2/BisSNP-0.82.2.jar

    install ./BisSNP-0.82.2.jar $LKTOOLS_DIR/BisSNP.jar
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
    rm -Rf MOSAIK-*
    rm -Rf $LKTOOLS_DIR/MosaikAligner
    rm -Rf $LKTOOLS_DIR/MosaikBuild
    rm -Rf $LKTOOLS_DIR/MosaikJump
    rm -Rf $LKTOOLS_DIR/MosaikText

    wget $WGET_OPTS https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/mosaik-aligner/MOSAIK-2.2.3-Linux-x64.tar
    tar -xf MOSAIK-2.2.3-Linux-x64.tar
    echo "Compressing TAR"
    gzip MOSAIK-2.2.3-Linux-x64.tar

    install ./MOSAIK-2.2.3-Linux-x64/MosaikAligner $LKTOOLS_DIR/MosaikAligner
    install ./MOSAIK-2.2.3-Linux-x64/MosaikBuild $LKTOOLS_DIR/MosaikBuild
    install ./MOSAIK-2.2.3-Linux-x64/MosaikJump $LKTOOLS_DIR/MosaikJump
    install ./MOSAIK-2.2.3-Linux-x64/MosaikText $LKTOOLS_DIR/MosaikText
fi

#also download src to get the networkFiles
echo "Download mosaik network files"
echo ""
if [[ ! -e ${LKTOOLS_DIR}/mosaikNetworkFile || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf MOSAIK-*
    rm -Rf $LKTOOLS_DIR/networkFile
    rm -Rf $LKTOOLS_DIR/mosaikNetworkFile

    cd $LKSRC_DIR

    wget $WGET_OPTS https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/mosaik-aligner/MOSAIK-2.2.3-source.tar
    tar -xf MOSAIK-2.2.3-source.tar
    echo "Compressing TAR"
    gzip MOSAIK-2.2.3-source.tar

    mkdir -p $LKTOOLS_DIR/mosaikNetworkFile
    cp -R ./MOSAIK-2.2.3-source/networkFile/*.ann $LKTOOLS_DIR/mosaikNetworkFile/
else
    echo "Mosaik network files already downloaded"
fi


#
#bismark
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bismark"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bismark || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf bismark_*
    rm -Rf Bismark_*
    rm -Rf 0.22.3*
    rm -Rf $LKTOOLS_DIR/bismark
    rm -Rf $LKTOOLS_DIR/bismark2bedGraph
    rm -Rf $LKTOOLS_DIR/bismark2report
    rm -Rf $LKTOOLS_DIR/bismark_genome_preparation
    rm -Rf $LKTOOLS_DIR/bismark_methylation_extractor
    rm -Rf $LKTOOLS_DIR/coverage2cytosine
    rm -Rf $LKTOOLS_DIR/deduplicate_bismark

    wget $WGET_OPTS https://github.com/FelixKrueger/Bismark/archive/0.22.3.tar.gz
    gunzip 0.22.3.tar.gz
    tar -xf 0.22.3.tar
    echo "Compressing TAR"
    gzip 0.22.3.tar
    cd Bismark-0.22.3

    install ./bismark $LKTOOLS_DIR/bismark
    install ./bismark2bedGraph $LKTOOLS_DIR/bismark2bedGraph
    install ./bismark2report $LKTOOLS_DIR/bismark2report
    install ./bismark_genome_preparation $LKTOOLS_DIR/bismark_genome_preparation
    install ./bismark_methylation_extractor $LKTOOLS_DIR/bismark_methylation_extractor
    install ./coverage2cytosine $LKTOOLS_DIR/coverage2cytosine
    install ./deduplicate_bismark $LKTOOLS_DIR/deduplicate_bismark

    if [ -n $SKIP_PACKAGE_MANAGER ]; then
        echo "Skipping package install"
    elif [ $(which apt-get) ]; then
        apt-get -q -y install libgd-graph-perl
    elif [ $(which yum) ]; then
        yum -y install perl-GD perl-GDGraph
    fi
else
    echo "Already installed"
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
    rm -Rf samtools-*
    rm -Rf $LKTOOLS_DIR/samtools
    rm -Rf $LKTOOLS_DIR/bcftools

    wget $WGET_OPTS https://github.com/samtools/samtools/releases/download/1.16.1/samtools-1.16.1.tar.bz2
    bunzip2 samtools-1.16.1.tar.bz2
    tar -xf samtools-1.16.1.tar
    echo "Compressing TAR"
    bzip2 samtools-1.16.1.tar
    cd samtools-1.16.1
    ./configure
    make
    install ./samtools ${LKTOOLS_DIR}/samtools
else
    echo "Already installed"
fi

#
#bcftools
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bcftools"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bcftools || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf bcftools*
    rm -Rf $LKTOOLS_DIR/bcftools

    wget $WGET_OPTS https://github.com/samtools/bcftools/releases/download/1.18/bcftools-1.18.tar.bz2
    tar xjvf bcftools-1.18.tar.bz2
    chmod 755 bcftools-1.18
    cd bcftools-1.18
    rm -f plugins/liftover.c
    wget $WGET_OPTS -P plugins https://raw.githubusercontent.com/freeseek/score/master/liftover.c

    ./configure
    make

    install ./bcftools $LKTOOLS_DIR
    install ./plugins/liftover.so $LKTOOLS_DIR
else
    echo "Already installed"
fi

#
#tabix
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install tabix/htslib"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/tabix || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf tabix-*
    rm -Rf $LKTOOLS_DIR/tabix
    rm -Rf $LKTOOLS_DIR/bgzip

    wget $WGET_OPTS https://github.com/samtools/htslib/releases/download/1.16/htslib-1.16.tar.bz2
    bunzip2 htslib-1.16.tar.bz2
    tar -xf htslib-1.16.tar
    echo "Compressing TAR"
    bzip2 htslib-1.16.tar
    chmod 755 htslib-1.16
    cd htslib-1.16
    ./configure
    make

    install ./tabix $LKTOOLS_DIR
    install ./bgzip $LKTOOLS_DIR
else
    echo "Already installed"
fi


#
#bedtools
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bedtools"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bedtools || ! -f ${LKTOOLS_DIR}/bedtools || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf bedtools*
    rm -Rf $LKTOOLS_DIR/bedtools

    wget -O bedtools $WGET_OPTS https://github.com/arq5x/bedtools2/releases/download/v2.30.0/bedtools.static.binary
    chmod +x bedtools

    install ./bedtools ${LKTOOLS_DIR}/bedtools
else
    echo "Already installed"
fi


#
#SnpEff
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install SnpEff"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/snpEff/snpEff.jar || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf snpEff*
    rm -Rf $LKTOOLS_DIR/snpEff

    wget $WGET_OPTS https://downloads.sourceforge.net/project/snpeff/snpEff_latest_core.zip
    unzip snpEff_latest_core.zip
    rm -Rf ./snpEff/examples
    rm -Rf ./snpEff/galaxy
    rm -Rf ./snpEff/scripts

    cp -R ./snpEff ${LKTOOLS_DIR}/snpEff
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
    wget $WGET_OPTS http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64/blat/blat
    chmod +x blat

    install blat $LKTOOLS_DIR/blat
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
    rm -Rf lastz-*
    rm -Rf 1.04.22*
    rm -Rf $LKTOOLS_DIR/lastz

    wget $WGET_OPTS https://github.com/lastz/lastz/archive/refs/tags/1.04.22.tar.gz
    gunzip 1.04.22.tar.gz
    tar -xf 1.04.22.tar
    echo "Compressing TAR"
    gzip 1.04.22.tar
    cd lastz-1.04.22
    make

    install ./src/lastz $LKTOOLS_DIR/lastz
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

if [[ ! -e $LKTOOLS_DIR/picard.jar || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf picard*
    rm -Rf $LKTOOLS_DIR/picard-tools
    rm -Rf $LKTOOLS_DIR/picard*
    rm -Rf $LKTOOLS_DIR/htsjdk-*
    rm -Rf $LKTOOLS_DIR/libIntelDeflater.so

    wget $WGET_OPTS https://github.com/broadinstitute/picard/releases/download/3.0.0/picard.jar

    cp -R ./picard.jar $LKTOOLS_DIR/
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

if [[ ! -e ${LKTOOLS_DIR}/bowtie || ! -e ${LKTOOLS_DIR}/bowtie-build || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"

    rm -Rf bowtie-*
    rm -Rf $LKTOOLS_DIR/bowtie
    rm -Rf $LKTOOLS_DIR/bowtie-build

    wget $WGET_OPTS https://downloads.sourceforge.net/project/bowtie-bio/bowtie/1.0.1/bowtie-1.0.1-linux-x86_64.zip
    unzip bowtie-1.0.1-linux-x86_64.zip

    install ./bowtie-1.0.1/bowtie $LKTOOLS_DIR/bowtie
    install ./bowtie-1.0.1/bowtie-build $LKTOOLS_DIR/bowtie-build
else
    echo "Already installed"
fi


#
#bowtie2
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bowtie2"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bowtie2 || ! -e ${LKTOOLS_DIR}/bowtie2-build || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"

    #old version
    rm -Rf bowtie2*
    rm -Rf $LKTOOLS_DIR/bowtie2
    rm -Rf $LKTOOLS_DIR/bowtie2-*

    wget $WGET_OPTS https://sourceforge.net/projects/bowtie-bio/files/bowtie2/2.3.4/bowtie2-2.3.4-linux-x86_64.zip
    unzip bowtie2-2.3.4-linux-x86_64.zip

    install ./bowtie2-2.3.4-linux-x86_64/bowtie2 $LKTOOLS_DIR/bowtie2
    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-build $LKTOOLS_DIR/bowtie2-build

    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-align-s $LKTOOLS_DIR/bowtie2-align-s
    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-align-l $LKTOOLS_DIR/bowtie2-align-l
    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-build-s $LKTOOLS_DIR/bowtie2-build-s
    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-build-l $LKTOOLS_DIR/bowtie2-build-l
    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-inspect $LKTOOLS_DIR/bowtie2-inspect
    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-inspect-s $LKTOOLS_DIR/bowtie2-inspect-s
    install ./bowtie2-2.3.4-linux-x86_64/bowtie2-inspect-l $LKTOOLS_DIR/bowtie2-inspect-l
else
    echo "Already installed"
fi


#
#trinity
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install trinity"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/trinity || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"

    #old version
    rm -Rf v2.1.1*
    rm -Rf trinityrnaseq-*
    rm -Rf Trinityrnaseq-*
    rm -Rf $LKTOOLS_DIR/Trinity
    rm -Rf $LKTOOLS_DIR/trinity

    wget $WGET_OPTS https://github.com/trinityrnaseq/trinityrnaseq/releases/download/Trinity-v2.6.6/Trinityrnaseq-v2.6.6.wExtSampleData.tar.gz
    tar -xf Trinityrnaseq-v2.6.6.wExtSampleData.tar.gz

    cd Trinityrnaseq-v2.6.6
    make

    cd ../
    cp -R Trinityrnaseq-v2.6.6 $LKTOOLS_DIR/trinity
else
    echo "Already installed"
fi


#
#jellyfish
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install jellyfish"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/jellyfish || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"

    #old version
    rm -Rf jellyfish*
    rm -Rf $LKTOOLS_DIR/jellyfish*

    wget $WGET_OPTS https://github.com/COMBINE-lab/salmon/releases/download/v0.9.1/Salmon-0.9.1_linux_x86_64.tar.gz
    tar xvf Salmon-0.9.1_linux_x86_64.tar.gz
    cp Salmon-latest_linux_x86_64/bin/salmon $LKTOOLS_DIR/

    wget $WGET_OPTS https://github.com/gmarcais/Jellyfish/releases/download/v2.2.10/jellyfish-linux
    chmod +x jellyfish-linux
    mv jellyfish-linux $LKTOOLS_DIR/jellyfish
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

if [[ ! -e ${LKTOOLS_DIR}/cutadapt || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf cutadapt-*
    rm -Rf $LKTOOLS_DIR/cutadapt*
    rm -Rf $LKTOOLS_DIR/_preamble.py
    rm -Rf $LKTOOLS_DIR/cutadapt_pip

    if [ -x "$(command -v pip3)" ];then
      PIP_EXE=`command -v pip3`
    else
      PIP_EXE=pip
    fi

    PIP_VERSION=`$PIP_EXE -V | cut -d '(' -f 2 | sed 's/python //' | cut -c 1 2>1`
    if [[ $PIP_VERSION == '2' ]];then
      echo 'Using python 2 compatible cutadapt'
      wget $WGET_OPTS https://pypi.python.org/packages/source/c/cutadapt/cutadapt-1.8.1.tar.gz
      gunzip cutadapt-1.8.1.tar.gz
      tar -xf cutadapt-1.8.1.tar
      gzip cutadapt-1.8.1.tar

      #note: cutadapt expects to be installed using python's package manager; however, this should work
      install ./cutadapt-1.8.1/bin/cutadapt ${LKTOOLS_DIR}/cutadapt.py
      install ./cutadapt-1.8.1/bin/_preamble.py ${LKTOOLS_DIR}/_preamble.py
      cp -R ./cutadapt-1.8.1/cutadapt ${LKTOOLS_DIR}/cutadapt
    else
      $PIP_EXE install --user --break-system-packages cutadapt==4.2
      $PIP_EXE show cutadapt
      $PIP_EXE install --user --break-system-packages pyinstaller
      $PIP_EXE show pyinstaller
      ~/.local/bin/pyinstaller --onefile --clean ~/.local/bin/cutadapt
      cp ./dist/cutadapt ${LKTOOLS_DIR}/cutadapt
      ${LKTOOLS_DIR}/cutadapt -h
    fi
else
    echo "Already installed"
fi


#
#sff2fastq
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install sff2fastq"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/sff2fastq || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf sff2fastq*
    rm -Rf $LKTOOLS_DIR/sff2fastq

    git clone https://github.com/indraniel/sff2fastq.git
    cd sff2fastq
    make

    install ./sff2fastq $LKTOOLS_DIR/
else
    echo "Already installed"
fi


#
#seqtk
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install seqtk"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/seqtk || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf seqtk*
    rm -Rf $LKTOOLS_DIR/seqtk

    git clone https://github.com/lh3/seqtk.git
    cd seqtk
    make

    install ./seqtk $LKTOOLS_DIR/
else
    echo "Already installed"
fi


#
#liftOver
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing liftOver"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/liftOver || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf liftOver
    rm -Rf $LKTOOLS_DIR/liftOver

    wget $WGET_OPTS http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64/liftOver
    chmod +x liftOver

    install ./liftOver $LKTOOLS_DIR/liftOver
else
    echo "Already installed"
fi


#
#faToTwoBit
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing faToTwoBit"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/faToTwoBit || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf faToTwoBit
    rm -Rf $LKTOOLS_DIR/faToTwoBit

    wget $WGET_OPTS http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64/faToTwoBit
    chmod +x faToTwoBit

    install ./faToTwoBit $LKTOOLS_DIR/faToTwoBit
else
    echo "Already installed"
fi


#
#BLAST+
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing BLAST+"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/blastn || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf ncbi-blast-*
    rm -Rf $LKTOOLS_DIR/blastn
    rm -Rf $LKTOOLS_DIR/blast_formatter
    rm -Rf $LKTOOLS_DIR/makeblastdb

    wget $WGET_OPTS ftp://ftp.ncbi.nlm.nih.gov/blast/executables/blast+/2.2.31/ncbi-blast-2.2.31+-x64-linux.tar.gz
    gunzip ncbi-blast-2.2.31+-x64-linux.tar.gz
    tar -xf ncbi-blast-2.2.31+-x64-linux.tar
    gzip ncbi-blast-2.2.31+-x64-linux.tar

    install ./ncbi-blast-2.2.31+/bin/blastn $LKTOOLS_DIR/blastn
    install ./ncbi-blast-2.2.31+/bin/blast_formatter $LKTOOLS_DIR/blast_formatter
    install ./ncbi-blast-2.2.31+/bin/makeblastdb $LKTOOLS_DIR/makeblastdb
    install ./ncbi-blast-2.2.31+/bin/makembindex $LKTOOLS_DIR/makembindex
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
    rm -Rf clustalw-*
    rm -Rf $LKTOOLS_DIR/clustalw2

    wget $WGET_OPTS http://www.clustal.org/download/current/clustalw-2.1.tar.gz
    gunzip clustalw-2.1.tar.gz
    tar -xf clustalw-2.1.tar
    gzip clustalw-2.1.tar
    cd clustalw-2.1
    ./configure
    make

    install ./src/clustalw2 $LKTOOLS_DIR/clustalw2

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
    rm -Rf muscle3*
    rm -Rf $LKTOOLS_DIR/muscle

    wget $WGET_OPTS https://www.drive5.com/muscle/downloads3.8.31/muscle3.8.31_i86linux64.tar.gz
    gunzip muscle3.8.31_i86linux64.tar.gz
    tar -xf muscle3.8.31_i86linux64.tar
    gzip muscle3.8.31_i86linux64.tar

    install ./muscle3.8.31_i86linux64 $LKTOOLS_DIR/muscle

else
    echo "Already installed"
fi


#
#Trimmomatic
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing Trimmomatic"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/trimmomatic.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf Trimmomatic*
    rm -Rf $LKTOOLS_DIR/trimmomatic.jar

    wget $WGET_OPTS https://github.com/usadellab/Trimmomatic/files/5854859/Trimmomatic-0.39.zip
    unzip Trimmomatic-0.39.zip

    install ./Trimmomatic-0.39/trimmomatic-0.39.jar $LKTOOLS_DIR/trimmomatic.jar

else
    echo "Already installed"
fi


#
#lofreq
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing lofreq"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/lofreq || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf lofreq_star*
    rm -Rf $LKTOOLS_DIR/lofreq_star*

    wget $WGET_OPTS https://github.com/CSB5/lofreq/raw/master/dist/lofreq_star-2.1.4_linux-x86-64.tgz
    tar -xf lofreq_star-2.1.4_linux-x86-64.tgz

    install ./lofreq_star-2.1.4_linux-x86-64/bin/lofreq* $LKTOOLS_DIR/
else
    echo "Already installed"
fi


#
#Genrich
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing Genrich"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/Genrich || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf Genrich*
    rm -Rf v0.6.1*
    rm -Rf $LKTOOLS_DIR/Genrich*

    wget $WGET_OPTS https://github.com/jsh58/Genrich/archive/refs/tags/v0.6.1.tar.gz
    tar -xf v0.6.1.tar.gz
    cd Genrich-0.6.1
    make

    install ./Genrich $LKTOOLS_DIR/
else
    echo "Already installed"
fi


if [ ! -z $LK_USER ];
then
    echo "Setting owner of files to: ${LK_USER}"
    chown -R ${LK_USER} $LKTOOLS_DIR
    chown -R ${LK_USER} $LKSRC_DIR
fi

if [ ! -z $CLEAN_SRC ];
then
    echo "Cleaning up tool_src"
    rm -Rf $LKSRC_DIR
else
  echo "Contents of tool_src:"
  ls $LKSRC_DIR
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installation is complete"

