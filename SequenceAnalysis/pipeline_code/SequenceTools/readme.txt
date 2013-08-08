Install Notes:

The SequenceAnalysis module depends on a set of perl scripts and external bioinformatics tools.


Ubuntu Packages:
apt-get install build-essential gcc make sysv-rc-conf zlib1g-dev libcurl4-dev ncurses-dev subversion yum openssl unzip nfs-common git-core libssl-dev r-base r-recommended

Perl Modules, preferably installed via CPAN:

BioPerl-core package 1.6.1 or higher
BioPerl-run package 1.6.1 or higher
JSON
YAML
File::HomeDir
String::Approx
Math::Round
List::Util
IPC::Run
Bio::DB::Sam
Labkey::Query
Crypt::SSLeay
File::Util


Environment variables:

NOTE: Variables can now be specified in the pipeline config XML file.  See example file in /tools folder of module.

#these are required:
PICARDPATH=/usr/local/bin/picard-tools
GATKPATH=/usr/local/bin/GATK
MOSAIK_NETWORKFILEPATH=../../   #mosaik network file location.  required to use mosaik aligner
SEQUENCEANALYSIS_CODELOCATION=/usr/local/sequencetools/  #the location of the code checked out from SVN.  this is only used for the purpose of outputting the svn revision to the log.  can be omitted.
SEQUENCEANALYSIS_EXTERNALDIR=/usr/local/sequencetools/  #the sequence module includes external tools used in the pipeline in /pipeline_code/external.  these should be checked out from SVN

#If the executables for these tools are located in the PATH, these are not required:
PERL5LIB=$PERL5LIB:/usr/local/bin/SequenceTools/  #should contain location of SequenceAnalysis perl files
BLATPATH=/usr/local/bin/blat
BOWTIEPATH=/usr/local/bin/bowtie
BWAPATH=/usr/local/bin/bwa
BFASTPATH=/usr/local/bin/bwa
FASTXPATH=/usr/local/bin/fastx_toolkit
MOSAIKPATH=/usr/local/bin/mosaik/bin
SAMTOOLSPATH=/usr/local/bin/samtools
ROCHETOOLSPATH=/usr/local/bin/Roche/bin 
FASTQCPATH=/usr/local/bin/FastQC
LASTZPATH=/usr/local/bin/lastz
CAP3PATH=/usr/local/bin/CAP3
PYROBAYESPATH=/usr/local/bin/PyroBayes