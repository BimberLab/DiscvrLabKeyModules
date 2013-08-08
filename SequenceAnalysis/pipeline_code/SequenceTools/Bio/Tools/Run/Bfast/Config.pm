# $Id: Config.pm 16762 2010-01-25 18:25:25Z maj $
#
# BioPerl module for Bio::Tools::Run::Bfast::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Bfast::Config - configurator for Bio::Tools::Run::Bfast

=head1 SYNOPSIS

Not used directly.

=head1 FEEDBACK

=head1 AUTHOR - Ben Bimber

Email bimber -at- wisc -dot- edu

=cut


# Let the code begin...

package Bio::Tools::Run::Bfast::Config;
use strict;
use warnings;
no warnings qw(qw);
use Bio::Root::Root;
use Exporter;
#use base qw(Bio::Root::Root );

our (@ISA, @EXPORT, @EXPORT_OK);
push @ISA, 'Exporter';
@EXPORT = qw(
             @program_commands
             %command_prefixes
             %composite_commands
             @program_params
             @program_switches
             %param_translation
             %command_files
            );

@EXPORT_OK = qw();

our @program_commands = qw(
    fasta2brg
    index
    match
    localalign
    postprocess
    bafconvert
    header
    bmfconvert
    brg2fasta
    easyalign    
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

#our %composite_commands = (
#    );

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'fasta2brg' => 'f2b',
    'index' => 'idx',
    'match' => 'mat',
    'localalign' => 'la',
    'postprocess' => 'pp',
    'bafconvert' => 'baf',
    'header' => 'head',
    'bmfconvert' => 'bmf',
    'brg2fasta' => 'b2f',
    'easyalign' => 'ea',	
    );

our @program_params = qw(
    command

	f2b|fasta
	f2b|useColorSpace

	idx|in_file
	idx|mask
	idx|hashWidth
	idx|depth

	mat|ref
	mat|reads_file
	mat|whichStrand
	mat|maxNumMatches
	
	la|ref
	la|matchFileName
	la|scoringMatrixFileName
	la|maxNumMatches
	la|avgMismatchQuality
	
	pp|ref
	pp|alignedFileName
	pp|algorithm
	pp|outputFormat
	pp|avgMismatchQuality
	pp|scoringMatrixFileName
	pp|outputID
	pp|minMappingQuality
	pp|minNormalizedScore
	
	baf|outputType
	baf|reference
 
	ea|ref
	ea|reads_file   

    );

our @program_switches = qw(
	pp|unpaired
	la|ungapped
);

our %param_translation = (
	'f2b|fasta' => 'f',
	'f2b|useColorSpace' => 'a',

	'idx|in_file' => 'f',
	'idx|mask' => 'm',
	'idx|hashWidth' => 'w',
	'idx|depth' => 'd',

	'mat|ref' => 'f',
	'mat|reads_file' => 'r',
	'mat|whichStrand' => 'w',
	'mat|maxNumMatches' => 'M',
	
	'la|ref' => 'f',
	'la|matchFileName' => 'm',
	'la|scoringMatrixFileName' => 'x',
	'la|maxNumMatches' => 'M',
	'la|avgMismatchQuality' => 'q',
	'la|ungapped' => 'u',
	
	'pp|ref' => 'f',
	'pp|alignedFileName' => 'i',
	'pp|algorithm' => 'a',
	'pp|outputFormat' => 'O',
	'pp|avgMismatchQuality' => 'q',
	'pp|scoringMatrixFileName' => 'x',
	'pp|minMappingQuality' => 'm',
	'pp|minNormalizedScore' => 'M',
	
	'baf|outputType' => 'O',
	'baf|reference' => 'f',

	'ea|ref' => 'f',
	'ea|reads_file' => 'r',
    );

#
# the order in the arrayrefs is the order required
# on the command line
#
# the strings in the arrayrefs (less special chars)
# become the keys for named parameters to run_maq
# 
# special chars:
#
# '#' implies optional
# '*' implies variable number of this type
# <|> implies stdin/stdout redirect
#

our %command_files = (
    'f2b'        => [qw( >out )],
    'idx'       => [qw( >out )],     
    'mat'         => [qw( >out )],
    'ea'         => [qw( >out )],
    'la'         => [qw( >out )], 
    'pp'         => [qw( #opt >out )],       
    );

#    'match' => 'mat',
#    'localalign' => 'la',
#    'postprocess' => 'pp',
#    'bafconvert' => 'baf',
#    'header' => 'head',
#    'bmfconvert' => 'bmf',
#    'brg2fasta' => 'b2f',
#    'easyalign' => 'ea',	 
1;
