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
MAVEN_OPTS="-Xss10m"

#NOTE: java/javac not automatically picked up
PATH=${JAVA_HOME}/bin:$PATH

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
WGET_OPTS="--read-timeout=10 --secure-protocol=auto"

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
#        wget $WGET_OPTS http://dl.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
#        wget $WGET_OPTS http://rpms.famillecollet.com/enterprise/remi-release-6.rpm
#        rpm -Uvh remi-release-6*.rpm epel-release-6*.rpm
#    fi
#fi

if [ ! -z $SKIP_PACKAGE_MANAGER ]; then
    echo "Skipping package install"
elif [ $(which yum) ]; then
    echo "Using Yum"
    yum -y install zip unzip gcc bzip2-devel gcc-c++ libstdc++ libstdc++-devel glibc-devel boost-devel ncurses-devel libgtextutils libgtextutils-devel python-devel openssl-devel glibc-static expat expat-devel subversion cpan git cmake liblzf-devel apache-maven R perl-devel perl-CPAN perl-PerlIO-gzip
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

    apt-get -q -y install bzip2 libbz2-dev libc6 libc6-dev libncurses5-dev libgtextutils-dev python-dev unzip zip ncftp gcc make perl libssl-dev libgcc1 libstdc++6 zlib1g zlib1g-dev libboost-all-dev python-numpy python-scipy libexpat1-dev libgtextutils-dev pkg-config subversion flex subversion libgoogle-perftools-dev perl-doc git cmake maven r-base r-cran-rcpp
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
    rm -Rf bwa-0.6.2*
    rm -Rf bwa-0.7.9a*
    rm -Rf bwa-0.7.12*
    rm -Rf $LKTOOLS_DIR/bwa

    wget $WGET_OPTS https://downloads.sourceforge.net/project/bio-bwa/bwa-0.7.12.tar.bz2
    bunzip2 bwa-0.7.12.tar.bz2
    tar -xf bwa-0.7.12.tar
    bzip2 bwa-0.7.12.tar
    cd bwa-0.7.12
    make CFLAGS=-msse2
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

    wget $WGET_OPTS https://downloads.sourceforge.net/project/subread/subread-1.6.0/subread-1.6.0-source.tar.gz
    gunzip subread-1.6.0-source.tar.gz
    tar -xf subread-1.6.0-source.tar

    cd subread-1.6.0-source/src
    make -f Makefile.Linux
    cd ../

    install ./bin/featureCounts $LKTOOLS_DIR/
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

    wget $WGET_OPTS https://downloads.sourceforge.net/project/flashpage/FLASH-1.2.7.tar.gz
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

    echo "Downloading GATK from GIT"
    git clone git://github.com/broadgsa/gatk-protected.git
    cd gatk-protected
    git checkout tags/3.7
    cd ../

    #this manually increases Xss in the hope of fixing intermittent StackOverlowErrors
    sed -i '/<groupId>org.scala-tools<\/groupId>/a <configuration><jvmArgs><jvmArg>-Xss10m<\/jvmArg><\/jvmArgs><\/configuration>' ./gatk-protected/pom.xml

    #fix multithreading bug
    sed -i 's/private final List<GenomeLoc> upstreamDeletionsLoc = new LinkedList<>();/private final ThreadLocal< List<GenomeLoc> > upstreamDeletionsLoc = ThreadLocal.withInitial(() -> new LinkedList<GenomeLoc>());/g' ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/genotyper/GenotypingEngine.java
    sed -i 's/upstreamDeletionsLoc.add(genomeLoc);/upstreamDeletionsLoc.get().add(genomeLoc);/g' ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/genotyper/GenotypingEngine.java
    sed -i 's/upstreamDeletionsLoc.iterator();/upstreamDeletionsLoc.get().iterator();/g' ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/genotyper/GenotypingEngine.java

    #libVectorLogless compile:
    #see: https://gatkforums.broadinstitute.org/gatk/discussion/9947/crashes-with-segmentation-fault-in-shipped-libvectorloglesspairhmm-so
    sed -i 's/<!--<USE_GCC>1<\/USE_GCC>-->/<USE_GCC>1<\/USE_GCC>/g' ./gatk-protected/public/VectorPairHMM/pom.xml
    sed -i 's/<!--<C_COMPILER>\/opt\/gcc-4.8.2\/bin\/gcc<\/C_COMPILER>-->/<C_COMPILER>\/usr\/bin\/gcc<\/C_COMPILER>/g' ./gatk-protected/public/VectorPairHMM/pom.xml
    sed -i 's/<!--<CPP_COMPILER>\/opt\/gcc-4.8.2\/bin\/g++<\/CPP_COMPILER>-->/<CPP_COMPILER>\/usr\/bin\/g++<\/CPP_COMPILER>/g' ./gatk-protected/public/VectorPairHMM/pom.xml
    cd gatk-protected/public/VectorPairHMM/
    mvn verify
    cd ../../../
    cp gatk-protected/public/VectorPairHMM/target/libVectorLoglessPairHMM.so ${LKTOOLS_DIR}

    #this is a custom extension: https://github.com/biodev/HTCondor_drivers
    #git clone https://github.com/biodev/HTCondor_drivers.git
    #mkdir ./gatk-protected/public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/condor
    #cp ./HTCondor_drivers/Queue/CondorJob* ./gatk-protected/public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/condor/

    #another, for MV checking
    mkdir -p ${LK_HOME}/svn/trunk/pipeline_code/
    svn co --username cpas --password cpas --no-auth-cache https://hedgehog.fhcrc.org/tor/stedi/trunk/externalModules/labModules/SequenceAnalysis/pipeline_code/gatk ${LK_HOME}/svn/trunk/pipeline_code/gatk/
    #mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/VariantType.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/MendelianViolationCount.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/MendelianViolationBySample.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/GenotypeConcordance.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/GenotypeConcordanceBySite.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/MinorAlleleFrequency.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/annotator/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/MultipleAllelesAtLoci.java ./gatk-protected/public/gatk-tools-public/src/main/java/org/broadinstitute/gatk/tools/walkers/coverage/
    mv ${LK_HOME}/svn/trunk/pipeline_code/gatk/RemoveAnnotations.java ./gatk-protected/protected/gatk-tools-protected/src/main/java/org/broadinstitute/gatk/tools/walkers/

    cd gatk-protected

    #remove due to compilation / dependency resolution error
    rm ./public/external-example/src/main/java/org/mycompany/app/*
    rm ./public/external-example/src/test/java/org/mycompany/app/*
    sed -i '/<module>external-example<\/module>/d' ./public/pom.xml

    #NOTE: can add -P\!queue to skip queue in some cases
    mvn verify -U
    mvn package
    cp ./protected/gatk-package-distribution/target/gatk-package-distribution-3.7.jar ${LKTOOLS_DIR}/GenomeAnalysisTK.jar
    cp ./protected/gatk-queue-package-distribution/target/gatk-queue-package-distribution-3.7.jar ${LKTOOLS_DIR}/Queue.jar
fi

if [[ ! -e ${LKTOOLS_DIR}/VariantQC.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf VariantQC*
    rm -Rf ${LKTOOLS_DIR}/VariantQC.jar

    wget $WGET_OPTS https://github.com/bbimber/gatk-protected/releases/download/0.08/VariantQC-0.08.jar
    cp VariantQC-0.08.jar ${LKTOOLS_DIR}/VariantQC.jar

fi

if [[ ! -e ${LKTOOLS_DIR}/DISCVRSeq.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf DISCVRSeq*
    rm -Rf ${LKTOOLS_DIR}/DISCVRSeq.jar

    wget $WGET_OPTS https://github.com/DISCVRSeq/DISCVRSeq/releases/download/0.01/DISCVRSeq-0.01.jar
    cp DISCVRSeq-0.01.jar ${LKTOOLS_DIR}/DISCVRSeq.jar
fi

if [[ ! -e ${LKTOOLS_DIR}/GenomeAnalysisTK-discvr.jar || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf ${LKTOOLS_DIR}/GenomeAnalysisTK-discvr.jar
    rm -Rf ${LKSRC_DIR}/gatk-discvr

    mkdir -p gatk-discvr
    cd gatk-discvr

    echo "Downloading GATK from GIT"
    git clone git://github.com/bbimber/gatk-protected.git
    cd gatk-protected

    #remove due to compilation error
    rm ./public/external-example/src/main/java/org/mycompany/app/*
    rm ./public/external-example/src/test/java/org/mycompany/app/*

    mvn verify -U -P\!queue
    mvn package -P\!queue

    cp ./protected/gatk-package-distribution/target/gatk-package-distribution-3.7.jar ${LKTOOLS_DIR}/GenomeAnalysisTK-discvr.jar
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

    wget $WGET_OPTS http://research-pub.gene.com/gmap/src/gmap-gsnap-2015-09-10.tar.gz
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
    rm -Rf STAR_2.5*
    rm -Rf $LKTOOLS_DIR/STAR

    wget $WGET_OPTS https://github.com/alexdobin/STAR/archive/2.5.1b.tar.gz
    gunzip 2.5.1b.tar.gz
    tar -xf 2.5.1b.tar
    gzip 2.5.1b.tar

    install ./STAR-2.5.1b/bin/Linux_x86_64_static/STAR $LKTOOLS_DIR/STAR
    install ./STAR-2.5.1b/bin/Linux_x86_64_static/STARlong $LKTOOLS_DIR/STARlong
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
    rm -Rf $LKTOOLS_DIR/bismark
    rm -Rf $LKTOOLS_DIR/bismark2bedGraph
    rm -Rf $LKTOOLS_DIR/bismark2report
    rm -Rf $LKTOOLS_DIR/bismark_genome_preparation
    rm -Rf $LKTOOLS_DIR/bismark_methylation_extractor
    rm -Rf $LKTOOLS_DIR/coverage2cytosine
    rm -Rf $LKTOOLS_DIR/deduplicate_bismark

    wget $WGET_OPTS https://github.com/FelixKrueger/Bismark/archive/0.17.0.tar.gz
    gunzip 0.17.0.tar.gz
    tar -xf 0.17.0.tar
    echo "Compressing TAR"
    gzip 0.17.0.tar
    cd Bismark-0.17.0

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

    wget $WGET_OPTS https://github.com/samtools/samtools/releases/download/1.5/samtools-1.5.tar.bz2
    bunzip2 samtools-1.5.tar.bz2
    tar -xf samtools-1.5.tar
    echo "Compressing TAR"
    bzip2 samtools-1.5.tar
    cd samtools-1.5
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

    wget $WGET_OPTS https://downloads.sourceforge.net/project/samtools/tabix/tabix-0.2.6.tar.bz2
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
    rm -Rf bedtools*
    rm -Rf $LKTOOLS_DIR/bedtools

    #wget $WGET_OPTS https://github.com/arq5x/bedtools2/releases/download/v2.26.0/bedtools-2.26.0.tar.gz
    #gunzip bedtools-2.26.0.tar.gz
    #tar -xf bedtools-2.26.0.tar
    #echo "Compressing TAR"
    #gzip bedtools-2.26.0.tar

    git clone https://github.com/arq5x/bedtools2.git
    cd bedtools2
    git checkout tags/v2.26.0
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
    rm -Rf lastz-1.02.00*
    rm -Rf $LKTOOLS_DIR/lastz

    wget $WGET_OPTS http://www.bx.psu.edu/miller_lab/dist/lastz-1.02.00.tar.gz
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
    #wget $WGET_OPTS http://cancan.cshl.edu/labmembers/gordon/files/libgtextutils-0.6.tar.bz2
    #tar -xjf libgtextutils-0.6.tar.bz2
    #cd libgtextutils-0.6
    #./configure
    #make
    #make install

    wget $WGET_OPTS http://hannonlab.cshl.edu/fastx_toolkit/fastx_toolkit-0.0.13.2.tar.bz2
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

    wget $WGET_OPTS https://github.com/broadinstitute/picard/releases/download/2.17.1/picard.jar

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

    #old version
    rm -Rf bowtie-0.12.8*
    rm -Rf bowtie-1.0.1*
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
    rm -Rf bowtie2-*
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

if [[ ! -e ${LKTOOLS_DIR}/Trinity || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"

    #old version
    rm -Rf v2.1.1*
    rm -Rf trinityrnaseq-*
    rm -Rf $LKTOOLS_DIR/Trinity

    wget $WGET_OPTS https://github.com/trinityrnaseq/trinityrnaseq/archive/Trinity-v2.5.1.zip
    unzip  Trinity-v2.5.1.zip

    cd trinityrnaseq-Trinity-v2.5.1
    make

    cd ../
    cp -R trinityrnaseq-Trinity-v2.5.1 $LKTOOLS_DIR/trinity
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

        wget $WGET_OPTS http://biopython.org/DIST/biopython-1.60.tar.gz
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
#jbrowse
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing jbrowse"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/JBrowse-1.12.1 || ! -z $FORCE_REINSTALL ]];
then
    rm -Rf JBrowse-*
    rm -Rf $LKTOOLS_DIR/JBrowse-*

    wget $WGET_OPTS https://jbrowse.org/releases/JBrowse-1.12.1.zip
    unzip JBrowse-1.12.1.zip
    rm JBrowse-1.12.1.zip
    cd JBrowse-1.12.1
    ./setup.sh
    cd ../

    cp -R ./JBrowse-1.12.1 $LKTOOLS_DIR/JBrowse-1.12.1
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

    wget $WGET_OPTS ftp://ftp.ncbi.nlm.nih.gov/blast/executables/blast+/2.2.30/ncbi-blast-2.2.30+-x64-linux.tar.gz
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
    rm -Rf muscle3.8.31_i86linux64.tar.gz
    rm -Rf muscle3.8.31_i86linux64.tar
    rm -Rf muscle3.8.31_i86linux64
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
    rm -Rf Trimmomatic-0.32*
    rm -Rf Trimmomatic-0.33*
    rm -Rf $LKTOOLS_DIR/trimmomatic.jar

    wget $WGET_OPTS http://www.usadellab.org/cms/uploads/supplementary/Trimmomatic/Trimmomatic-0.33.zip
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

if [ ! -z $CLEAN_SRC ]; then
    echo "Cleaning up tool_src"
    rm -Rf $LKSRC_DIR
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installation is complete"