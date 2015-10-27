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
LK_USER=

while getopts "d:u:fp" arg;
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

if [ ! -z $SKIP_PACKAGE_MANAGER ]; then
    echo "Skipping package install"
elif [ $(which yum) ]; then
    echo "Using Yum"
    yum -y install zip unzip gcc bzip2-devel gcc-c++ libstdc++ libstdc++-devel glibc-devel boost-devel ncurses-devel libgtextutils libgtextutils-devel python-devel openssl-devel glibc-devel.i686 glibc-static.i686 glibc-static.x86_64 expat expat-devel subversion cpan git cmake liblzf-devel apache-maven R
elif [ $(which apt-get) ]; then
    echo "Using apt-get"

    #this is a possible setup for R
    #add-apt-repository "deb http://cran.cnr.berkeley.edu/bin/linux/ubuntu/ precise/"
    #gpg --keyserver keyserver.ubuntu.com --recv-key E084DAB9 or gpg --hkp://keyserver keyserver.ubuntu.com:80 --recv-key E084DAB9
    #gpg -a --export E084DAB9 | sudo apt-key add -

    #install oracle java
    #apt-get install python-software-properties
    #add-apt-repository ppa:webupd8team/java
    #apt-get update
    #apt-get install oracle-java7-installer
    #update-alternatives --config java
    #update-alternatives --config javac

    apt-get -q -y install libc6 libc6-dev libncurses5-dev libgtextutils-dev python-dev unzip zip ncftp gcc make perl libssl-dev libgcc1 libstdc++6 zlib1g zlib1g-dev libboost-all-dev python-numpy python-scipy libexpat1-dev libgtextutils-dev pkg-config subversion flex subversion libgoogle-perftools-dev perl-doc git cmake maven r-base
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
    install bwa $LKTOOLS_DIR/
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

    install flash $LKTOOLS_DIR/
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

    if [ ! -v JAVA_HOME ]; then
        echo "JAVA_HOME not defined"
    else
        echo "JAVA_HOME: [${JAVA_HOME}]"
    fi

    echo "Downloading GATK from GIT"
    #git clone git://github.com/broadgsa/gatk.git
    git clone git://github.com/broadgsa/gatk-protected.git
    cd gatk-protected
    #git checkout tags/3.4
    git checkout
    cd ../

    #this is a custom extension
    svn export https://github.com/NationalGenomicsInfrastructure/piper/trunk/src/main/scala/molmed/queue/engine/parallelshell
    sed -i 's/molmed.queue.engine.parallelshell/org.broadinstitute.gatk.queue.engine.parallelshell/' parallelshell/*
    mv parallelshell ./gatk-protected/public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/

    #another one: https://github.com/biodev/HTCondor_drivers
    git clone https://github.com/biodev/HTCondor_drivers.git
    mkdir ./gatk-protected/public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/condor
    cp ./HTCondor_drivers/Queue/CondorJob* ./gatk-protected/public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/condor/

    #another, for MV checking
    mkdir -p ${LK_HOME}/svn/trunk/pipeline_code/
    svn co --username cpas --password cpas --no-auth-cache https://hedgehog.fhcrc.org/tor/stedi/trunk/externalModules/labModules/SequenceAnalysis/pipeline_code/gatk ${LK_HOME}/svn/trunk/pipeline_code/gatk/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/MendelianViolationCount.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/ConflictingReadCount.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/ConflictingReadCountBySamples.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/

    cd gatk-protected

    #remove due to compilation error
    rm ./public/external-example/src/main/java/org/mycompany/app/*
    rm ./public/external-example/src/test/java/org/mycompany/app/*

    mvn verify
    mvn package
    cp ./protected/gatk-package-distribution/target/gatk-package-distribution-3.5-SNAPSHOT.jar ${LKTOOLS_DIR}/GenomeAnalysisTK.jar
    cp ./protected/gatk-queue-package-distribution/target/gatk-queue-package-distribution-3.5-SNAPSHOT.jar ${LKTOOLS_DIR}/Queue.jar
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
    rm -Rf gmap-gsnap-2015-09-10*
    rm -Rf gmap-2015-09-10*
    rm -Rf $LKTOOLS_DIR/gsnap
    rm -Rf $LKTOOLS_DIR/gmap
    rm -Rf $LKTOOLS_DIR/gmap_build

    wget --read-timeout=10 http://research-pub.gene.com/gmap/src/gmap-gsnap-2015-09-10.tar.gz
    gunzip gmap-gsnap-2015-09-10.tar.gz
    tar -xf gmap-gsnap-2015-09-10.tar
    gzip gmap-gsnap-2015-09-10.tar
    cd gmap-2015-09-10

    ./configure --prefix=${LK_HOME}
    make
    make install

    #note: there was a bug in gmap if your input has a space in the filepath
    #cd "$LKTOOLS_DIR"
    #sed -i 's/-o $coordsfile/-o \\"$coordsfile\\"/' gmap_build
    #sed -i 's/-c $coordsfile/-c \\"$coordsfile\\"/' gmap_build
    #sed -i 's/-f $fasta_sources/-c \\"$fasta_sources\\"/' gmap_build
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
    rm -Rf STAR_2.4*
    rm -Rf $LKTOOLS_DIR/STAR

    wget --read-timeout=10 https://github.com/alexdobin/STAR/archive/STAR_2.4.2a.tar.gz
    gunzip STAR_2.4.2a.tar.gz
    tar -xf STAR_2.4.2a.tar
    gzip STAR_2.4.2a.tar

    install ./STAR-STAR_2.4.2a/bin/Linux_x86_64_static/STAR $LKTOOLS_DIR/STAR
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

    install ./MOSAIK-2.1.73-binary/MosaikAligner $LKTOOLS_DIR/MosaikAligner
    install ./MOSAIK-2.1.73-binary/MosaikBuild $LKTOOLS_DIR/MosaikBuild
    install ./MOSAIK-2.1.73-binary/MosaikJump $LKTOOLS_DIR/MosaikJump
    install ./MOSAIK-2.1.73-binary/MosaikText $LKTOOLS_DIR/MosaikText
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

    cp -R ./MOSAIK-2.1.73-source/networkFile $LKTOOLS_DIR/mosaikNetworkFile
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
    install samtools ${LKTOOLS_DIR}/samtools
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
    rm -Rf bedtools-2.20.1*
    rm -Rf bedtools-2.24.0*
    rm -Rf $LKTOOLS_DIR/bedtools

    wget --read-timeout=10 https://github.com/arq5x/bedtools2/releases/download/v2.24.0/bedtools-2.24.0.tar.gz
    gunzip bedtools-2.24.0.tar.gz
    tar -xf bedtools-2.24.0.tar
    echo "Compressing TAR"
    gzip bedtools-2.24.0.tar
    cd bedtools2
    make
    
    install ./bin/bedtools ${LKTOOLS_DIR}/bedtools
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
    wget --read-timeout=10 http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64/blat/blat
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

    install ./src/lastz $LKTOOLS_DIR/lastz
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

if [[ ! -e $LKTOOLS_DIR/picard.jar || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf picard-tools-*
    rm -Rf snappy-java-1.0.3-rc3.jar
    rm -Rf $LKTOOLS_DIR/picard-tools
    rm -Rf $LKTOOLS_DIR/picard*
    rm -Rf $LKTOOLS_DIR/htsjdk-*
    rm -Rf $LKTOOLS_DIR/libIntelDeflater.so

    wget --read-timeout=10 https://github.com/broadinstitute/picard/releases/download/1.135/picard-tools-1.135.zip
    unzip picard-tools-1.135.zip

    cp -R ./picard-tools-1.135/* $LKTOOLS_DIR/
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

    install ./bowtie-1.0.1/bowtie $LKTOOLS_DIR/bowtie
    install ./bowtie-1.0.1/bowtie-build $LKTOOLS_DIR/bowtie-build
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

if [[ ! -e ${LKTOOLS_DIR}/cutadapt.py || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf cutadapt-*
    rm -Rf $LKTOOLS_DIR/cutadapt*
    rm -Rf $LKTOOLS_DIR/_preamble.py

    wget https://pypi.python.org/packages/source/c/cutadapt/cutadapt-1.8.1.tar.gz
    gunzip cutadapt-1.8.1.tar.gz
    tar -xf cutadapt-1.8.1.tar
    gzip cutadapt-1.8.1.tar

    #note: cutadapt expects to be installed using python's package manager; however, this should work
    install ./cutadapt-1.8.1/bin/cutadapt ${LKTOOLS_DIR}/cutadapt.py
    install ./cutadapt-1.8.1/bin/_preamble.py ${LKTOOLS_DIR}/_preamble.py
    cp -R ./cutadapt-1.8.1/cutadapt ${LKTOOLS_DIR}/cutadapt

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

    wget --read-timeout=10 http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64.v287/faToTwoBit
    chmod +x faToTwoBit

    install ./faToTwoBit $LKTOOLS_DIR/faToTwoBit
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
    cd ../

    cp -R ./JBrowse-1.11.5 $LKTOOLS_DIR/JBrowse-1.11.5
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

    install ./ncbi-blast-2.2.30+/bin/blastn $LKTOOLS_DIR/blastn
    install ./ncbi-blast-2.2.30+/bin/blast_formatter $LKTOOLS_DIR/blast_formatter
    install ./ncbi-blast-2.2.30+/bin/makeblastdb $LKTOOLS_DIR/makeblastdb
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
    rm -Rf muscle3.8.31_i86linux64.tar.gz
    rm -Rf muscle3.8.31_i86linux64.tar
    rm -Rf muscle3.8.31_i86linux64
    rm -Rf $LKTOOLS_DIR/muscle

    wget --read-timeout=10 http://www.drive5.com/muscle/downloads3.8.31/muscle3.8.31_i86linux64.tar.gz
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
    rm -Rf Trimmomatic-0.32*
    rm -Rf Trimmomatic-0.33*
    rm -Rf $LKTOOLS_DIR/trimmomatic.jar

    wget --read-timeout=10 http://www.usadellab.org/cms/uploads/supplementary/Trimmomatic/Trimmomatic-0.33.zip
    unzip Trimmomatic-0.33.zip

    install ./Trimmomatic-0.33/trimmomatic-0.33.jar $LKTOOLS_DIR/trimmomatic.jar

else
    echo "Already installed"
fi

if [ ! -z $LK_USER ];
then
    echo "Setting owner of files to: ${LK_USER}"
    chown -R ${LK_USER} $LKTOOLS_DIR
    chown -R ${LK_USER} $LKSRC_DIR
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installation is complete"