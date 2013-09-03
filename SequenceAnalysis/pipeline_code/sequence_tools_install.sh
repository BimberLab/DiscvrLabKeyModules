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
LK_HOME=/usr/local/labkey
LKTOOLS_DIR=${LK_HOME}/bin
LKSRC_DIR=${LKTOOLS_DIR}/src
mkdir -p $LKSRC_DIR
mkdir -p $LKTOOLS_DIR


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install location"
echo ""
echo "LK_HOME: $LK_HOME"
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
    yum update
    yum install glibc-devel ncurses-devel libgtextutils-devel python-developenssl-devel
    yum install glibc-devel.i686 glibc-static.i686 glibc-static.x86_64
    yum install expat expat-devel
elif [ $(which apt-get) ]; then
    echo "Using apt-get"
    apt-get update
    apt-get install libc6 libc6-dev libncurses5-dev libgtextutils-dev python-dev libssl-dev
    #apt-get install libc6-dev-i386 libc6-i386
    apt-get install python-numpy python-scipy

    #note: this line untested due to lack of access to right OS
    apt-get install libexpat1-dev
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


#
#mosaik
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install mosaik"
echo ""
cd $LKSRC_DIR

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


#
#samtools
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install samtools"
echo ""
cd $LKSRC_DIR

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


#
#blat
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install blat"
echo ""
cd $LKSRC_DIR

echo "Cleaning up previous installs"
rm -Rf blat
rm -Rf $LKTOOLS_DIR/blat

mkdir blat
cd blat
wget http://hgdownload.cse.ucsc.edu/admin/exe/linux.x86_64/blat/blat
chmod +x blat
ln -s $LKSRC_DIR/blat/blat $LKTOOLS_DIR


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


#
#fastx-toolkit
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install fastx-toolkit"
echo ""
cd $LKSRC_DIR

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


#
#picard
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install picard"
echo ""
cd $LKSRC_DIR

echo "Cleaning up previous installs"
rm -Rf picard-tools-1.77*
rm -Rf picard-tools-1.96*
rm -Rf snappy-java-1.0.3-rc3.jar
rm -Rf $LKTOOLS_DIR/picard-tools

wget http://downloads.sourceforge.net/project/picard/picard-tools/1.96/picard-tools-1.96.zip
unzip picard-tools-1.96.zip
ln -s $LKSRC_DIR/picard-tools-1.96 $LKTOOLS_DIR/picard-tools


#
#bowtie
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bowtie"
echo ""
cd $LKSRC_DIR

echo "Cleaning up previous installs"
rm -Rf bowtie-0.12.8-linux-x86_64.zip
rm -Rf bowtie-0.12.8
rm -Rf $LKTOOLS_DIR/bowtie

wget http://sourceforge.net/projects/bowtie-bio/files/bowtie/0.12.8/bowtie-0.12.8-linux-x86_64.zip
unzip bowtie-0.12.8-linux-x86_64.zip
ln -s $LKSRC_DIR/bowtie-0.12.8/bowtie $LKTOOLS_DIR


#
#bfast
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install bfast"
echo ""
cd $LKSRC_DIR

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


#
#cap3
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install cap3"
echo ""
cd $LKSRC_DIR

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


#
#biopython
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install biopython"
echo ""
cd $LKSRC_DIR

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


#
#seq_crumbs
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install seq_crumbs"
echo ""
cd $LKSRC_DIR

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


#
#velvet
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install velvet"
echo ""
cd $LKSRC_DIR

echo "Cleaning up previous installs"
rm -Rf velvet_1.2.09.tgz
rm -Rf velvet_1.2.09.tar
rm -Rf velvet-1.2.09
rm -Rf $LKTOOLS_DIR/velgetg
rm -Rf $LKTOOLS_DIR/velgeth

wget http://www.ebi.ac.uk/~zerbino/velvet/velvet_1.2.09.tgz
gunzip velvet_1.2.09.tgz
tar -xf velvet_1.2.09.tar
echo "Compressing TAR"
gzip velvet_1.2.09.tar
cd velvet_1.2.09
make
ln -s $LKSRC_DIR/velvet-1.2.09/velvetg $LKTOOLS_DIR
ln -s $LKSRC_DIR/velvet-1.2.09/velveth $LKTOOLS_DIR


#
#VelvetOptimiser
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing VelvetOptimiser"
echo ""

cd $LKSRC_DIR
rm -Rf VelvetOptimiser-2.2.5.tar.gz
rm -Rf VelvetOptimiser-2.2.5.tar
rm -Rf VelvetOptimiser-2.2.5
rm -Rf $LKTOOLS_DIR/ VelvetOptimiser.pl

wget http://www.vicbioinformatics.com/VelvetOptimiser-2.2.5.tar.gz
gunzip VelvetOptimiser-2.2.5.tar.gz
tar -xf VelvetOptimiser-2.2.5.tar
gzip VelvetOptimiser-2.2.5.tar
cd VelvetOptimiser-2.2.5
ln -s $LKSRC_DIR/VelvetOptimiser-2.2.5/VelvetOptimiser.pl $LKTOOLS_DIR


#
#AMOS
#

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing AMOS"
echo ""

cd $LKSRC_DIR
rm -Rf amos-3.1.0.tar.gz
rm -Rf amos-3.1.0.tar
rm -Rf amos-3.1.0
rm -Rf $LKSRC_DIR/amos-3.1.0/bank2fasta
rm -Rf $LKSRC_DIR/amos-3.1.0/bank2config

wget http://downloads.sourceforge.net/project/amos/amos/3.1.0/amos-3.1.0.tar.gz?r=&ts=1368671864&use_mirror=superb-dca2
gunzip amos-3.1.0.tar.gz
tar -xf amos-3.1.0.tar
cd amos-3.1.0
./configure
make
make install

ln -s $LKSRC_DIR/amos-3.1.0/bank2fasta $LKTOOLS_DIR
ln -s $LKSRC_DIR/amos-3.1.0/bank2contig $LKTOOLS_DIR


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


#
#misc perl modules
#
echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install All required Perl modules"
echo ""
cpan -i Cwd
cpan -i JSON
cpan -i YAML
cpan -i File::HomeDir
cpan -i String::Approx
cpan -i Statistics::Descriptive
cpan -i Math::Round
cpan -i List::Util
cpan -i IPC::Run
cpan -i Labkey::Query
cpan -i File::Util
cpan -i Algorithm::Diff
cpan -i File::Sort
cpan -i Array::Compare
cpan -i Proc::ProcessTable
cpan -i XML::Writer
cpan -i URI
cpan -i Test::Warn
cpan -i XML::DOM::XPath
cpan -i XML::Parser::PerlSAX
cpan -i XML::SAX
cpan -i XML::SAX::Writer
cpan -i XML::Simple
cpan -i XML::Twig
cpan -i Set::Scalar
cpan -i Sort::Naturally
cpan -i Data::Stag
cpan -i Crypt::SSLeay


#
#bioperl
#
cpan -i CJFIELDS/BioPerl-1.6.901.tar.gz
cpan -i Bio::DB::Sam
cpan -i -f CJFIELDS/BioPerl-Run-1.006900.tar.gz


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
echo 'Assuming the location install locations ($LKTOOLS_DIR) is in your PATH, then no other config is needed."
echo 'If not, you will need to supply the install location for each tool, by also adding these to the XML file:"
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