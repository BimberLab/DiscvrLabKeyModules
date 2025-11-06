#!/bin/bash

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


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install plink2"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/plink2 || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf plink2*
    rm -Rf $LKTOOLS_DIR/plink2*

    wget https://s3.amazonaws.com/plink2-assets/alpha6/plink2_linux_avx2_20241111.zip
    unzip plink2_linux_avx2_20241111.zip

    install ./plink2 $LKTOOLS_DIR/plink2
else
    echo "Already installed"
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install minimap2"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/minimap2 || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf minimap2*
    rm -Rf $LKTOOLS_DIR/minimap2*

    wget https://github.com/lh3/minimap2/releases/download/v2.28/minimap2-2.28.tar.bz2
    bunzip2 minimap2-2.28.tar.bz2
    tar -xf minimap2-2.28.tar

    cd minimap2-2.28
    make

    install minimap2 $LKTOOLS_DIR/
else
    echo "Already installed"
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install cellsnp-lite"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/cellsnp-lite || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf cellsnp-lite*
    rm -Rf $LKTOOLS_DIR/cellsnp-lite*

    git clone https://github.com/single-cell-genetics/cellsnp-lite.git
    cd cellsnp-lite
    autoreconf -iv
    ./configure --with-htslib=${LKTOOLS_DIR}/lib
    make

    install cellsnp-lite $LKTOOLS_DIR/
else
    echo "Already installed"
fi


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install sratoolkit"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/fasterq-dump || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf sratoolkit*
    rm -Rf $LKTOOLS_DIR/sratoolkit*
    rm -Rf $LKTOOLS_DIR/fasterq-dump*

    wget https://ftp-trace.ncbi.nlm.nih.gov/sra/sdk/current/sratoolkit.current-centos_linux64.tar.gz
    tar -xf sratoolkit.current-centos_linux64.tar.gz
    cp -R sratoolkit.3.1.1-centos_linux64 $LKTOOLS_DIR
    ln -s ${LKTOOLS_DIR}/sratoolkit.3.1.1-centos_linux64/bin/fasterq-dump ${LKTOOLS_DIR}/fasterq-dump
else
    echo "Already installed"
fi


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install gffread"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/gffread || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf gffread*
    rm -Rf $LKTOOLS_DIR/gffread*

    wget https://github.com/gpertea/gffread/releases/download/v0.12.7/gffread-0.12.7.Linux_x86_64.tar.gz
    tar -xf gffread-0.12.7.Linux_x86_64.tar.gz

    install ./gffread-0.12.7.Linux_x86_64/gffread $LKTOOLS_DIR/
else
    echo "Already installed"
fi


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install paragraph"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/paragraph || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf paragraph*
    rm -Rf $LKTOOLS_DIR/paragraph*

    mkdir paragraph
    cd paragraph
    wget https://github.com/Illumina/paragraph/releases/download/v2.4a/paragraph-v2.4a-binary.zip
    unzip paragraph-v2.4a-binary.zip
    rm paragraph-v2.4a-binary.zip

    python3 -m pip install pysam intervaltree

    cd ../
    cp -R paragraph $LKTOOLS_DIR
    ln -s ${LKTOOLS_DIR}/paragraph/bin/paragraph ${LKTOOLS_DIR}/paragraph
    ln -s ${LKTOOLS_DIR}/paragraph/bin/idxdepth ${LKTOOLS_DIR}/idxdepth
    ln -s ${LKTOOLS_DIR}/paragraph/bin/multigrmpy.py ${LKTOOLS_DIR}/multigrmpy.py
else
    echo "Already installed"
fi

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install sniffles2"
echo ""
cd $LKSRC_DIR

if [[ ! -e ${LKTOOLS_DIR}/sniffles || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf sniffles*
    rm -Rf $LKTOOLS_DIR/sniffles*

    module load python/3.11.7
    python -m ensurepip --upgrade
    python -m pip install --force-reinstall --target ${LKTOOLS_DIR}/pythonPackages git+https://github.com/fritzsedlazeck/Sniffles.git
else
    echo "Already installed"
fi

if [[ ! -e ${LKTOOLS_DIR}/multiqc || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf multiqc*
    rm -Rf $LKTOOLS_DIR/multiqc*

    python3 -m pip install --user multiqc
else
    echo "Already installed"
fi


if [[ ! -e ${LKTOOLS_DIR}/gt || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf gt*
    rm -Rf $LKTOOLS_DIR/gt*

    wget https://github.com/genometools/genometools/releases/download/v1.6.5/gt-1.6.5-Linux_x86_64-64bit-complete.tar.gz
    tar -xf gt-1.6.5-Linux_x86_64-64bit-complete.tar.gz

    install ./gt-1.6.5-Linux_x86_64-64bit-complete/bin/gt $LKTOOLS_DIR/
    mv ./gt-1.6.5-Linux_x86_64-64bit-complete/gtdata $LKTOOLS_DIR/
else
    echo "Already installed"
fi

if [[ ! -e ${LKTOOLS_DIR}/king || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf king*
    rm -Rf Linux-king*
    rm -Rf $LKTOOLS_DIR/king*

    wget https://www.kingrelatedness.com/Linux-king.tar.gz
    tar -xf Linux-king.tar.gz

    install king $LKTOOLS_DIR/
else
    echo "Already installed"
fi

if [[ ! -e ${LKTOOLS_DIR}/regctl || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf regctl*
    rm -Rf $LKTOOLS_DIR/regctl*

    curl -L https://github.com/regclient/regclient/releases/latest/download/regctl-linux-amd64 > regctl
    chmod 755 regctl

    install regctl $LKTOOLS_DIR/
else
    echo "Already installed"
fi

if [[ ! -e ${LKTOOLS_DIR}/svtyper || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf $LKTOOLS_DIR/svtyper*

    # NOTE: this fork is used to ensure python3 compatibility
    #python3 -m pip install --user git+https://github.com/hall-lab/svtyper.git
    python3 -m pip install --user git+https://github.com/bbimber/svtyper.git

    SVTYPER=`which svtyper`
    ln -s $SVTYPER ${LKTOOLS_DIR}/svtyper

    SVTYPER=`which svtyper-sso`
    ln -s $SVTYPER ${LKTOOLS_DIR}/svtyper-sso
else
    echo "Already installed"
fi

if [[ ! -e ${LKTOOLS_DIR}/graphtyper || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf $LKTOOLS_DIR/graphtyper*

    wget https://github.com/DecodeGenetics/graphtyper/releases/download/v2.7.7/graphtyper
    chmod a+x graphtyper

    mv ./graphtyper $LKTOOLS_DIR/
else
    echo "Already installed"
fi

if [[ ! -e ${LKTOOLS_DIR}/bbmap || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf $LKTOOLS_DIR/bbmap

    wget https://sourceforge.net/projects/bbmap/files/BBMap_39.25.tar.gz
    tar -xf BBMap_39.25.tar.gz

    mv bbmap $LKTOOLS_DIR/
    ln -s $LKTOOLS_DIR/bbmap/bbmap.sh $LKTOOLS_DIR/bbmap.sh
else
    echo "Already installed"
fi

if [[ ! -e ${LKTOOLS_DIR}/sawfish || ! -z $FORCE_REINSTALL ]];
then
    echo "Cleaning up previous installs"
    rm -Rf $LKTOOLS_DIR/sawfish*

    wget https://github.com/PacificBiosciences/sawfish/releases/download/v2.2.0/sawfish-v2.2.0-x86_64-unknown-linux-gnu.tar.gz
    tar -xzf sawfish-v2.2.0-x86_64-unknown-linux-gnu.tar.gz

    mv sawfish-v2.2.0-x86_64-unknown-linux-gnu $LKTOOLS_DIR/
    ln -s $LKTOOLS_DIR/sawfish-v2.2.0-x86_64-unknown-linux-gnu/bin/sawfish $LKTOOLS_DIR/
else
    echo "Already installed"
fi
