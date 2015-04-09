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
       ;;
    *)
       echo "The following arguments are supported:"
       echo "-d: the path to the labkey install, such as /usr/local/labkey.  If only this parameter is provided, tools will be installed in bin/ and src/ under this location."
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
LKSRC_DIR=${LKTOOLS_DIR}/src
mkdir -p $LKSRC_DIR

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
#        wget --read-timeout=10 http://dl.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
#        wget --read-timeout=10 http://rpms.famillecollet.com/enterprise/remi-release-6.rpm
#        rpm -Uvh remi-release-6*.rpm epel-release-6*.rpm
#    fi
#fi

if [ -n $SKIP_PACKAGE_MANAGER ]; then
    echo "Skipping package install"
elif [ $(which yum) ]; then
    echo "Using Yum"
    yum -y install zip unzip gcc bzip2-devel gcc-c++ libstdc++ libstdc++-devel glibc-devel boost-devel ncurses-devel libgtextutils libgtextutils-devel python-devel openssl-devel glibc-devel.i686 glibc-static.i686 glibc-static.x86_64 expat expat-devel subversion cpan git cmake liblzf-devel apache-maven
elif [ $(which apt-get) ]; then
    echo "Using apt-get"

    #this is a possible setup for R
    #add-apt-repository "deb http://cran.cnr.berkeley.edu/bin/linux/ubuntu/ precise/"
    #gpg --keyserver keyserver.ubuntu.com --recv-key E084DAB9 or gpg --hkp://keyserver keyserver.ubuntu.com:80 --recv-key E084DAB9
    #gpg -a --export E084DAB9 | sudo apt-key add -

    apt-get -q -y install libc6 libc6-dev libncurses5-dev libtcmalloc-minimal0 libgtextutils-dev python-dev unzip zip ncftp gcc make perl libssl-dev libgcc1 libstdc++6 zlib1g zlib1g-dev libboost-all-dev python-numpy python-scipy libexpat1-dev libgtextutils-dev pkg-config subversion flex subversion libgoogle-perftools-dev perl-doc git cmake maven
else
    echo "No known package manager present, aborting"
    exit 1
fi


for l in $(find ${LKTOOLS_DIR} -type l -exec test ! -e {} \; -print); do
    echo "removing broken symlink: $l";
    rm -Rf $l
done

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

    wget --read-timeout=10 http://downloads.sourceforge.net/project/bio-bwa/bwa-0.7.9a.tar.bz2
    bunzip2 bwa-0.7.9a.tar.bz2
    tar -xf bwa-0.7.9a.tar
    bzip2 bwa-0.7.9a.tar
    cd bwa-0.7.9a
    make CFLAGS=-msse2
    
    cd $LKTOOLS_DIR
    ln -s ./src/bwa-0.7.9a/bwa bwa
else
    echo "Already installed"
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

if [[ ! -e ${LKTOOLS_DIR}/gmap_build || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf gmap-gsnap-2014-12-16*
    rm -Rf gmap-2014-12-16*
    rm -Rf $LKTOOLS_DIR/gsnap
    rm -Rf $LKTOOLS_DIR/gmap
    rm -Rf $LKTOOLS_DIR/gmap_build

    wget --read-timeout=10 http://research-pub.gene.com/gmap/src/gmap-gsnap-2014-12-16.v2.tar.gz
    gunzip gmap-gsnap-2014-12-16.v2.tar.gz
    tar -xf gmap-gsnap-2014-12-16.v2.tar
    gzip gmap-gsnap-2014-12-16.v2.tar
    cd gmap-2014-12-16
    ./configure --prefix=${LK_HOME}
    make
    make install
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
    rm -Rf STAR_2.4.0h*
    rm -Rf $LKTOOLS_DIR/STAR

    wget --read-timeout=10 https://github.com/alexdobin/STAR/archive/STAR_2.4.0h.tar.gz
    gunzip STAR_2.4.0h.tar.gz
    tar -xf STAR_2.4.0h.tar
    gzip STAR_2.4.0h.tar

    cd $LKTOOLS_DIR
    ln -s ./src/STAR-STAR_2.4.0h/bin/Linux_x86_64_static/STAR STAR
else
    echo "Already installed"
fi


#
# qualimap
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install qualimap"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKSRC_DIR}/qualimap_v2.0 || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf qualimap_v2.0*

    wget --read-timeout=10 http://qualimap.bioinfo.cipf.es/release/qualimap_v2.0.zip
    unzip qualimap_v2.0.zip
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
    rm -Rf MOSAIK-2.1.73*
    rm -Rf $LKTOOLS_DIR/MosaikAligner
    rm -Rf $LKTOOLS_DIR/MosaikBuild
    rm -Rf $LKTOOLS_DIR/MosaikJump
    rm -Rf $LKTOOLS_DIR/MosaikText

    wget --read-timeout=10 http://mosaik-aligner.googlecode.com/files/MOSAIK-2.1.73-binary.tar
    tar -xf MOSAIK-2.1.73-binary.tar
    echo "Compressing TAR"
    gzip MOSAIK-2.1.73-binary.tar

    cd $LKTOOLS_DIR
    ln -s ./src/MOSAIK-2.1.73-binary/MosaikAligner MosaikAligner
    ln -s ./src/MOSAIK-2.1.73-binary/MosaikBuild MosaikBuild
    ln -s ./src/MOSAIK-2.1.73-binary/MosaikJump MosaikJump
    ln -s ./src/MOSAIK-2.1.73-binary/MosaikText MosaikText
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

    wget --read-timeout=10 http://mosaik-aligner.googlecode.com/files/MOSAIK-2.1.73-source.tar
    tar -xf MOSAIK-2.1.73-source.tar
    echo "Compressing TAR"
    gzip MOSAIK-2.1.73-source.tar

    cd $LKTOOLS_DIR
    ln -s ./src/MOSAIK-2.1.73-source/networkFile mosaikNetworkFile
else
    echo "Mosaik network files already downloaded"
fi


#
#fastq_screen
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install fastq_screen"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/fastq_screen || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf fastq_screen_v0.4.4
    rm -Rf $LKTOOLS_DIR/fastq_screen

    wget --read-timeout=10 http://www.bioinformatics.babraham.ac.uk/projects/fastq_screen/fastq_screen_v0.4.4.tar.gz
    gunzip fastq_screen_v0.4.4.tar.gz
    tar -xf fastq_screen_v0.4.4.tar
    echo "Compressing TAR"
    gzip fastq_screen_v0.4.4.tar
    cd fastq_screen_v0.4.4

    cd $LKTOOLS_DIR
    ln -s ./src/fastq_screen_v0.4.4/fastq_screen fastq_screen
else
    echo "Already installed"
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
    rm -Rf bismark_v0.12.5*
    rm -Rf $LKTOOLS_DIR/bismark
    rm -Rf $LKTOOLS_DIR/bismark2bedGraph
    rm -Rf $LKTOOLS_DIR/bismark2report
    rm -Rf $LKTOOLS_DIR/bismark_genome_preparation
    rm -Rf $LKTOOLS_DIR/bismark_methylation_extractor
    rm -Rf $LKTOOLS_DIR/coverage2cytosine
    rm -Rf $LKTOOLS_DIR/deduplicate_bismark

    wget --read-timeout=10 http://www.bioinformatics.babraham.ac.uk/projects/bismark/bismark_v0.12.5.tar.gz
    gunzip bismark_v0.12.5.tar.gz
    tar -xf bismark_v0.12.5.tar
    echo "Compressing TAR"
    gzip bismark_v0.12.5.tar
    cd bismark_v0.12.5

    cd $LKTOOLS_DIR
    ln -s ./src/bismark_v0.12.5/bismark bismark
    ln -s ./src/bismark_v0.12.5/bismark2bedGraph bismark2bedGraph 
    ln -s ./src/bismark_v0.12.5/bismark2report bismark2report 
    ln -s ./src/bismark_v0.12.5/bismark_genome_preparation bismark_genome_preparation
    ln -s ./src/bismark_v0.12.5/bismark_methylation_extractor bismark_methylation_extractor
    ln -s ./src/bismark_v0.12.5/coverage2cytosine coverage2cytosine
    ln -s ./src/bismark_v0.12.5/deduplicate_bismark deduplicate_bismark 

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


##
##tophat
##
#echo ""
#echo ""
#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Install tophat"
#echo ""
#cd $LKSRC_DIR
#
#if [[ ! -e ${LKTOOLS_DIR}/tophat || ! -z $FORCE_REINSTALL ]];
#then
#    echo "Cleaning up previous installs"
#    rm -Rf tophat-2.0.12*
#    rm -Rf $LKTOOLS_DIR/tophat2
#    rm -Rf $LKTOOLS_DIR/prep_reads
#
#    wget --read-timeout=10 http://ccb.jhu.edu/software/tophat/downloads/tophat-2.0.12.Linux_x86_64.tar.gz
#    gunzip tophat-2.0.12.Linux_x86_64.tar.gz
#    tar -xf tophat-2.0.12.Linux_x86_64.tar
#    echo "Compressing TAR"
#    gzip tophat-2.0.12.Linux_x86_64.tar
#    cd tophat-2.0.12.Linux_x86_64
#
#    ln -s ./src/tophat-2.0.12.Linux_x86_64/tophat2 $LKTOOLS_DIR
#    ln -s ./src/tophat-2.0.12.Linux_x86_64/prep_reads $LKTOOLS_DIR
#else
#    echo "Already installed"
#fi


#
#samtools
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install samtools/bcftools"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/bcftools || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf samtools-0.1.18*
    rm -Rf $LKTOOLS_DIR/samtools
    rm -Rf $LKTOOLS_DIR/bcftools

    wget --read-timeout=10 http://downloads.sourceforge.net/project/samtools/samtools/0.1.18/samtools-0.1.18.tar.bz2
    bunzip2 samtools-0.1.18.tar.bz2
    tar -xf samtools-0.1.18.tar
    echo "Compressing TAR"
    bzip2 samtools-0.1.18.tar
    cd samtools-0.1.18
    #note: this is used later by Bio::DB::Samtools
    make CXXFLAGS=-fPIC CFLAGS=-fPIC CPPFLAGS=-fPIC
    
    cd $LKTOOLS_DIR
    ln -s ./src/samtools-0.1.18/samtools samtools
    ln -s ./src/samtools-0.1.18/bcftools/bcftools bcftools
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

    wget --read-timeout=10 http://downloads.sourceforge.net/project/samtools/tabix/tabix-0.2.6.tar.bz2
    bunzip2 tabix-0.2.6.tar.bz2
    tar -xf tabix-0.2.6.tar
    echo "Compressing TAR"
    bzip2 tabix-0.2.6.tar
    chmod 755 tabix-0.2.6
    cd tabix-0.2.6
    make
    
    cd $LKTOOLS_DIR
    ln -s ./src/tabix-0.2.6/tabix tabix
    ln -s ./src/tabix-0.2.6/bgzip bgzip
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
    rm -Rf bedtools-2.20.1*
    rm -Rf $LKTOOLS_DIR/bedtools

    wget --read-timeout=10 https://github.com/arq5x/bedtools2/releases/download/v2.20.1/bedtools-2.20.1.tar.gz
    gunzip bedtools-2.20.1.tar.gz
    tar -xf bedtools-2.20.1.tar
    echo "Compressing TAR"
    gzip bedtools-2.20.1.tar
    cd bedtools2-2.20.1
    make
    
    cd $LKTOOLS_DIR
    ln -s ./src/bedtools2-2.20.1/bin/bedtools bedtools
else
    echo "Already installed"
fi

#
#VCFTools
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install vcftools"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/vcftools || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf vcftools_0.1.12*
    rm -Rf $LKTOOLS_DIR/vcftools

    wget --read-timeout=10 http://downloads.sourceforge.net/project/vcftools/vcftools_0.1.12b.tar.gz
    gunzip vcftools_0.1.12b.tar.gz
    tar -xf vcftools_0.1.12b.tar
    echo "Compressing TAR"
    gzip vcftools_0.1.12b.tar
    cd vcftools_0.1.12b
    make
    
    cd $LKTOOLS_DIR
    ln -s ./src/vcftools_0.1.12b/bin/vcftools vcftools
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

    wget --read-timeout=10 http://downloads.sourceforge.net/project/snpeff/snpEff_latest_core.zip
    unzip snpEff_latest_core.zip
    cd snpEff

    cd $LKTOOLS_DIR
    ln -s ./src/snpEff snpEff
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
    wget --read-timeout=10 http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64/blat/blat
    chmod +x blat
    
    cd $LKTOOLS_DIR
    ln -s ./src/blat/blat blat
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

    wget --read-timeout=10 http://www.bx.psu.edu/miller_lab/dist/lastz-1.02.00.tar.gz
    gunzip lastz-1.02.00.tar.gz
    tar -xf lastz-1.02.00.tar
    echo "Compressing TAR"
    gzip lastz-1.02.00.tar
    cd lastz-distrib-1.02.00
    mv src/Makefile src/Makefile.dist
    sed 's/-Werror //g' src/Makefile.dist > src/Makefile
    make
    
    cd $LKTOOLS_DIR
    ln -s ./src/lastz-distrib-1.02.00/src/lastz lastz
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
    #wget --read-timeout=10 http://cancan.cshl.edu/labmembers/gordon/files/libgtextutils-0.6.tar.bz2
    #tar -xjf libgtextutils-0.6.tar.bz2
    #cd libgtextutils-0.6
    #./configure
    #make
    #make install

    wget --read-timeout=10 http://hannonlab.cshl.edu/fastx_toolkit/fastx_toolkit-0.0.13.2.tar.bz2
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

if [[ ! -e ${LKSRC_DIR}/picard-tools-1.119 || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf picard-tools-*
    rm -Rf snappy-java-1.0.3-rc3.jar
    rm -Rf $LKTOOLS_DIR/picard-tools

    wget --read-timeout=10 http://downloads.sourceforge.net/project/picard/picard-tools/1.119/picard-tools-1.119.zip
    unzip picard-tools-1.119.zip
    
    cd $LKTOOLS_DIR
    ln -s ./src/picard-tools-1.119 picard-tools
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

    #old version
    rm -Rf bowtie-0.12.8*
    rm -Rf bowtie-1.0.1*
    rm -Rf $LKTOOLS_DIR/bowtie
    rm -Rf $LKTOOLS_DIR/bowtie-build

    wget --read-timeout=10 http://downloads.sourceforge.net/project/bowtie-bio/bowtie/1.0.1/bowtie-1.0.1-linux-x86_64.zip
    unzip bowtie-1.0.1-linux-x86_64.zip
    cd bowtie-1.0.1

    cd $LKTOOLS_DIR
    ln -s ./src/bowtie-1.0.1/bowtie bowtie
    ln -s ./src/bowtie-1.0.1/bowtie-build bowtie-build
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

if [[ (-z $(which cutadapt) || ! -z $FORCE_REINSTALL) && -z $SKIP_PACKAGE_MANAGER ]];
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

    wget --read-timeout=10 http://seq.cs.iastate.edu/CAP3/cap3.linux.opteron64.tar
    tar -xf cap3.linux.opteron64.tar
    echo "Compressing TAR"
    gzip cap3.linux.opteron64.tar
    
    cd $LKTOOLS_DIR
    ln -s ./src/CAP3/cap3 cap3
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

if [ -n $SKIP_PACKAGE_MANAGER ]; then
    echo "Skipping package install"
elif [ $(which apt-get) ]; then
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

        wget --read-timeout=10 http://biopython.org/DIST/biopython-1.60.tar.gz
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

    wget --read-timeout=10 http://bioinf.comav.upv.es/downloads/seq_crumbs-0.1.8-x64-linux.tar.gz
    gunzip seq_crumbs-0.1.8-x64-linux.tar.gz
    tar -xf seq_crumbs-0.1.8-x64-linux.tar
    echo "Compressing TAR"
    gzip seq_crumbs-0.1.8-x64-linux.tar
    cd seq_crumbs-0.1.8-x64-linux
    
    cd $LKTOOLS_DIR
    ln -s ./src/seq_crumbs-0.1.8-x64-linux/sff_extract sff_extract
    ln -s ./src/seq_crumbs-0.1.8-x64-linux/convert_format convert_format
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

    wget --read-timeout=10 http://downloads.sourceforge.net/project/flashpage/FLASH-1.2.7.tar.gz
    gunzip FLASH-1.2.7.tar.gz
    tar -xf FLASH-1.2.7.tar
    echo "Compressing TAR"
    gzip FLASH-1.2.7.tar
    cd FLASH-1.2.7
    make
    
    cd $LKTOOLS_DIR
    ln -s ./src/FLASH-1.2.7/flash flash
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

    wget --read-timeout=10 http://downloads.sourceforge.net/project/mira-assembler/MIRA/stable/mira_4.0.2_linux-gnu_x86_64_static.tar.bz2
    bunzip2 mira_4.0.2_linux-gnu_x86_64_static.tar.bz2
    tar -xf mira_4.0.2_linux-gnu_x86_64_static.tar
    echo "Compressing TAR"
    bzip2 mira_4.0.2_linux-gnu_x86_64_static.tar
    cd mira_4.0.2_linux-gnu_x86_64_static

    cd $LKTOOLS_DIR
    ln -s ./src/mira_4.0.2_linux-gnu_x86_64_static/bin/mira mira
    ln -s ./src/mira_4.0.2_linux-gnu_x86_64_static/bin/miraconvert miraconvert
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

    wget --read-timeout=10 http://www.ebi.ac.uk/~zerbino/velvet/velvet_1.2.09.tgz
    gunzip velvet_1.2.09.tgz
    tar -xf velvet_1.2.09.tar
    echo "Compressing TAR"
    gzip velvet_1.2.09.tar
    cd velvet_1.2.09
    make OPENMP=1 LONGSEQUENCES=1
    
    cd $LKTOOLS_DIR
    ln -s ./src/velvet_1.2.09/velvetg velvetg
    ln -s ./src/velvet_1.2.09/velveth velveth
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

    wget --read-timeout=10 http://www.vicbioinformatics.com/VelvetOptimiser-2.2.5.tar.gz
    gunzip VelvetOptimiser-2.2.5.tar.gz
    tar -xf VelvetOptimiser-2.2.5.tar
    gzip VelvetOptimiser-2.2.5.tar
    cd VelvetOptimiser-2.2.5
    
    cd $LKTOOLS_DIR    
    ln -s ./src/VelvetOptimiser-2.2.5/VelvetOptimiser.pl VelvetOptimiser.pl  
else
    echo "Already installed"
fi

#
#AMOS
#
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
#    wget --read-timeout=10 http://downloads.sourceforge.net/project/amos/amos/3.1.0/amos-3.1.0.tar.gz
#    gunzip amos-3.1.0.tar.gz
#    tar -xf amos-3.1.0.tar
#    cd amos-3.1.0
#    ./configure
#    make
#    make install
#
#    ln -sr $LKSRC_DIR/amos-3.1.0/bin/bank2fasta $LKTOOLS_DIR
#    ln -sr $LKSRC_DIR/amos-3.1.0/bin/bank2contig $LKTOOLS_DIR
#    ln -sr $LKSRC_DIR/amos-3.1.0/bin/bank-transact $LKTOOLS_DIR
#else
#    echo "Already installed"
#fi


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

    wget --read-timeout=10 http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64.v287/liftOver
    chmod +x liftOver
    
    cd $LKTOOLS_DIR
    ln -s ./src/liftOver liftOver
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

    wget --read-timeout=10 http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64.v287/faToTwoBit
    chmod +x faToTwoBit

    cd $LKTOOLS_DIR
    ln -s ./src/faToTwoBit faToTwoBit
else
    echo "Already installed"
fi


#
#jbrowse
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing jbrowse"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/JBrowse-1.11.5 || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf JBrowse-*
    rm -Rf $LKTOOLS_DIR/JBrowse-*

    wget --read-timeout=10 http://jbrowse.org/releases/JBrowse-1.11.5.zip
    unzip JBrowse-1.11.5.zip
    rm JBrowse-1.11.5.zip
    cd JBrowse-1.11.5
    ./setup.sh

    cd $LKTOOLS_DIR
    ln -s ./src/JBrowse-1.11.5 JBrowse-1.11.5
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

    wget --read-timeout=10 ftp://ftp.ncbi.nlm.nih.gov/blast/executables/blast+/2.2.30/ncbi-blast-2.2.30+-x64-linux.tar.gz
    gunzip ncbi-blast-2.2.30+-x64-linux.tar.gz
    tar -xf ncbi-blast-2.2.30+-x64-linux.tar
    gzip ncbi-blast-2.2.30+-x64-linux.tar

    cd $LKTOOLS_DIR
    ln -s ./src/ncbi-blast-2.2.30+/bin/blastn blastn
    ln -s ./src/ncbi-blast-2.2.30+/bin/blast_formatter blast_formatter
    ln -s ./src/ncbi-blast-2.2.30+/bin/makeblastdb makeblastdb
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

    wget --read-timeout=10 http://downloads.sourceforge.net/project/varscan/VarScan.v2.3.6.jar
    
    cd $LKTOOLS_DIR
    ln -s ./src/VarScan.v2.3.6.jar VarScan2.jar
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
    wget --read-timeout=10 -U firefox http://www.ub.es/softevol/variscan/variscan-2.0.3.tar.gz
    gunzip variscan-2.0.3.tar.gz
    tar -xf variscan-2.0.3.tar
    
    cd $LKTOOLS_DIR
    ln -s ./src/variscan-2.0.3/bin/Linux-i386/variscan variscan
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

    wget --read-timeout=10 http://www.clustal.org/download/current/clustalw-2.1.tar.gz
    gunzip clustalw-2.1.tar.gz
    tar -xf clustalw-2.1.tar
    gzip clustalw-2.1.tar
    cd clustalw-2.1
    ./configure
    make

    cd $LKTOOLS_DIR
    ln -s ./src/clustalw-2.1/src/clustalw2 clustalw2

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

    wget --read-timeout=10 http://www.drive5.com/muscle/downloads3.8.31/muscle3.8.31_i86linux64.tar.gz
    gunzip muscle3.8.31_i86linux64.tar.gz
    tar -xf muscle3.8.31_i86linux64.tar
    gzip muscle3.8.31_i86linux64.tar

    cd $LKTOOLS_DIR
    ln -s ./src/muscle3.8.31_i86linux64 muscle

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
    rm -Rf Trimmomatic-0.32*
    rm -Rf $LKTOOLS_DIR/trimmomatic.jar

    wget --read-timeout=10 http://www.usadellab.org/cms/uploads/supplementary/Trimmomatic/Trimmomatic-0.32.zip
    unzip Trimmomatic-0.32.zip
    cd Trimmomatic-0.32

    cd $LKTOOLS_DIR
    ln -s ./src/Trimmomatic-0.32/trimmomatic-0.32.jar trimmomatic.jar

else
    echo "Already installed"
fi


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