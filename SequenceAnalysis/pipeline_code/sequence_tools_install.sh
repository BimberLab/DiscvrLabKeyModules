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
# bash sequence_tools_install.sh | tee sequence_tools_install.log
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

if [ $(which yum) ]; then
    echo "Using Yum"
    yum install glibc-devel ncurses-devel libgtextutils-devel python-devel openssl-devel glibc-devel.i686 glibc-static.i686 glibc-static.x86_64 expat expat-devel
elif [ $(which apt-get) ]; then
    echo "Using apt-get"
    apt-get -q -y install libc6 libc6-dev libncurses5-dev libgtextutils-dev python-dev libssl-dev libgcc1 libstdc++6 libtcmalloc-minimal0 zlib1g zlib1g-dev libboost-thread-dev libboost-dev libboost-system-dev libboost-regex-dev libboost-filesystem-dev libboost-iostreams-dev python-numpy python-scipy libexpat1-dev
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
    rm -Rf $LKTOOLS_DIR/bwa

    wget http://downloads.sourceforge.net/project/bio-bwa/bwa-0.6.2.tar.bz2
    bunzip2 bwa-0.6.2.tar.bz2
    tar -xf bwa-0.6.2.tar
    bzip2 bwa-0.6.2.tar
    cd bwa-0.6.2
    make CFLAGS=-msse2
    ln -s $LKSRC_DIR/bwa-0.6.2/bwa $LKTOOLS_DIR
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
    rm -Rf $LKTOOLS_DIR/networkFile

    wget http://mosaik-aligner.googlecode.com/files/MOSAIK-2.1.73-binary.tar
    tar -xf MOSAIK-2.1.73-binary.tar
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikAligner $LKTOOLS_DIR/MosaikAligner
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikBuild $LKTOOLS_DIR/MosaikBuild
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikJump $LKTOOLS_DIR/MosaikJump
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-binary/MosaikText $LKTOOLS_DIR/MosaikText
    echo "Compressing TAR"
    gzip MOSAIK-2.1.73-binary.tar

    #also download src to get the networkFiles
    cd $LKSRC_DIR

    wget http://mosaik-aligner.googlecode.com/files/MOSAIK-2.1.73-source.tar
    tar -xf MOSAIK-2.1.73-source.tar
    echo "Compressing TAR"
    gzip MOSAIK-2.1.73-source.tar
    ln -s $LKSRC_DIR/MOSAIK-2.1.73-source/networkFile $LKTOOLS_DIR
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
    rm -Rf snappy-java-1.0.3-rc3.jar
    rm -Rf $LKTOOLS_DIR/picard-tools

    wget http://downloads.sourceforge.net/project/picard/picard-tools/1.96/picard-tools-1.96.zip
    unzip picard-tools-1.96.zip
    ln -s $LKSRC_DIR/picard-tools-1.96 $LKTOOLS_DIR/picard-tools
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
    rm -Rf bowtie-0.12.8-linux-x86_64.zip
    rm -Rf bowtie-0.12.8
    rm -Rf $LKTOOLS_DIR/bowtie

    wget http://sourceforge.net/projects/bowtie-bio/files/bowtie/0.12.8/bowtie-0.12.8-linux-x86_64.zip
    unzip bowtie-0.12.8-linux-x86_64.zip
    ln -s $LKSRC_DIR/bowtie-0.12.8/bowtie $LKTOOLS_DIR
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

if [[ ! -e biopython-1.60 || ! -z $FORCE_REINSTALL ]];
then
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
    rm -Rf seq_crumbs-0.1.6-x64-linux.tar.gz
    rm -Rf seq_crumbs-0.1.6-x64-linux.tar
    rm -Rf seq_crumbs-0.1.6-x64-linux
    rm -Rf $LKTOOLS_DIR/sff_extract
    rm -Rf $LKTOOLS_DIR/convert_format

    wget http://bioinf.comav.upv.es/_downloads/seq_crumbs-0.1.6-x64-linux.tar.gz
    gunzip seq_crumbs-0.1.6-x64-linux.tar.gz
    tar -xf seq_crumbs-0.1.6-x64-linux.tar
    echo "Compressing TAR"
    gzip seq_crumbs-0.1.6-x64-linux.tar
    cd seq_crumbs-0.1.6-x64-linux
    ln -s $LKSRC_DIR/seq_crumbs-0.1.6-x64-linux/sff_extract $LKTOOLS_DIR
    ln -s $LKSRC_DIR/seq_crumbs-0.1.6-x64-linux/convert_format $LKTOOLS_DIR
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

#if [[ ! -e ${LKTOOLS_DIR}/mira || ! -z $FORCE_REINSTALL ]];
#then
    echo "Cleaning up previous installs"
    rm -Rf mira_4.0rc4_linux-gnu_x86_64_static.tar.bz2
    rm -Rf mira_4.0rc4_linux-gnu_x86_64_static.tar
    rm -Rf mira_4.0rc4_linux-gnu_x86_64_static

    rm -Rf mira-4.0.tar.bz2
    rm -Rf mira-4.0.tar
    rm -Rf mira-4.0

    rm -Rf $LKTOOLS_DIR/mira
    rm -Rf $LKTOOLS_DIR/miraconvert

    wget http://downloads.sourceforge.net/project/mira-assembler/MIRA/stable/mira_4.0_linux-gnu_x86_64_static.tar.bz2
    bunzip2 mira_4.0_linux-gnu_x86_64_static.tar.bz2
    tar -xf mira_4.0_linux-gnu_x86_64_static.tar
    echo "Compressing TAR"
    bzip2 mira_4.0_linux-gnu_x86_64_static.tar
    cd mira_4.0_linux-gnu_x86_64_static

    ln -s $LKSRC_DIR/mira_4.0_linux-gnu_x86_64_static/bin/mira $LKTOOLS_DIR
    ln -s $LKSRC_DIR/mira_4.0_linux-gnu_x86_64_static/bin/miraconvert $LKTOOLS_DIR
#else
#    echo "Already installed"
#fi

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
#sequence perl code
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install SequenceAnalysis pipeline_code perl code from the SequenceAnalysis module"
echo ""
mkdir -p $LK_HOME/svn
cd $LK_HOME/svn

echo "Cleaning up previous installs"
rm -Rf trunk

mkdir trunk
cd trunk
svn co --no-auth-cache --username cpas --password cpas https://hedgehog.fhcrc.org/tor/stedi/trunk/externalModules/labModules/SequenceAnalysis/pipeline_code
chmod +x $LK_HOME/svn/trunk/pipeline_code/sequence_tools_install.sh


#
#misc perl modules
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install All required Perl modules"
echo ""
cpan -i Cwd JSON YAML File::HomeDir String::Approx Statistics::Descriptive Math::Round List::Util IPC::Run LabKey::Query File::Util Algorithm::Diff File::Sort Array::Compare Proc::ProcessTable XML::Writer URI Test::Warn XML::DOM::XPath XML::Parser::PerlSAX XML::SAX XML::SAX::Writer XML::Simple XML::Twig Set::Scalar Sort::Naturally Data::Stag Crypt::SSLeay


#
#bioperl
#

perl -e 'use Bio::SeqIO' &> /dev/null;
if [[ $? -eq 0 || ! -z $FORCE_REINSTALL ]];
then
    echo "BioPerl already installed"
else
    cpan -i -f BioPerl
fi

perl -e 'use Bio::DB::Sam' &> /dev/null;
if [[ $? -eq 0 || ! -z $FORCE_REINSTALL ]];
then
    echo "Bio::DB::Sam already installed"
else
    cpan -i Bio::DB::Sam
fi

perl -e 'use Bio::Tools::Run::BWA' &> /dev/null;
if [[ $? -eq 0 || ! -z $FORCE_REINSTALL ]];
then
    echo "BioPerl-Run already installed"
else
    cpan -i -f CJFIELDS/BioPerl-Run-1.006900.tar.gz
fi


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installation is complete"
echo ""
echo "The following should be added to your pipelineConfig.xml file, in the 'softwarePackages' tag.  See the checked in example for more information."
echo '<entry key="SEQUENCEANALYSIS_CODELOCATION" value="/usr/local/labkey/svn/current/pipeline_code/SequenceTools/"/>'
echo '<entry key="SEQUENCEANALYSIS_EXTERNALDIR" value="/usr/local/labkey/svn/current/pipeline_code/external/"/>'
echo '<entry key="PICARDPATH" value="/usr/local/labkey/bin/picard-tools/"/>'
echo '<entry key="MOSAIK_NETWORKFILEPATH" value="/usr/local/labkey/bin/networkFile/"/>'
echo '<entry key="Perl" value="/usr/bin/"/>'
echo ""
echo ""
echo 'Assuming the location install locations ($LKTOOLS_DIR) is in your PATH, then no other config is needed.'
echo 'If not, you will need to supply the install location for each tool, by also adding these to the XML file:'
echo '<entry key="BLATPATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="BOWTIEPATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="BWAPATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="BFASTPATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="CAP3PATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="FASTXPATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="LASTZPATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="MOSAIKPATH" value="/usr/local/labkey/bin/"/>'
echo '<entry key="SAMTOOLSPATH" value="/usr/local/labkey/bin/"/>'

#echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
#echo "Testing installed tools"
#PATH=$PATH:LKSRC_DIR
#bwa | grep version
#MosaikAligner -h | grep 2.1
#samtools -h
#blat -h
#lastz -h
#bfast -h
#bowtie -h
#fastx -h
#convert_format --version
