# $Id: Config.pm 16762 2010-01-25 18:25:25Z maj $
#
# BioPerl module for Bio::Tools::Run::FastxToolkit::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::FastxToolkit::Config - configurator for Bio::Tools::Run::FastxToolkit

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to FastxToolkit.pm.

=head1 FEEDBACK

=head2 Mailing Lists

User feedback is an integral part of the evolution of this and other
Bioperl modules. Send your comments and suggestions preferably to
the Bioperl mailing list.  Your participation is much appreciated.

  bioperl-l@bioperl.org                  - General discussion
http://bioperl.org/wiki/Mailing_lists  - About the mailing lists

=head2 Support

Please direct usage questions or support issues to the mailing list:

L<bioperl-l@bioperl.org>

rather than to the module maintainer directly. Many experienced and
reponsive experts will be able look at the problem and quickly
address it. Please include a thorough description of the problem
with code and data examples if at all possible.

=head2 Reporting Bugs

Report bugs to the Bioperl bug tracking system to help us keep track
of the bugs and their resolution. Bug reports can be submitted via
the web:

  http://bugzilla.open-bio.org/

=head1 AUTHOR - Ben Bimber

Email bimber -at- wisc -dot- edu

=head1 APPENDIX

The rest of the documentation details each of the object methods.
Internal methods are usually preceded with a _

=cut

# Let the code begin...

package Bio::Tools::Run::FastxToolkit::Config;
use strict;
use warnings;
no warnings qw(qw);
use Exporter;

our (@ISA, @EXPORT, @EXPORT_OK);
push @ISA, 'Exporter';
@EXPORT = qw(
             $program_dir
             @program_commands
             %command_prefixes
             @program_params
             @program_switches
             %param_translation
             %command_files
            );

@EXPORT_OK = qw();

our $program_dir;
our @program_commands = qw(
    fasta_formatter
    fasta_nucleotide_changer
    fastq_masker
    fastq_quality_converter
    fastq_quality_filter
    fastq_quality_trimmer
    fastq_to_fasta
    fastx_artifacts_filter
    fastx_clipper
    fastx_collapser
    fastx_quality_stats
    fastx_renamer
    fastx_reverse_complement
    fastx_trimmer
    fastx_uncollapser    
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'fasta_formatter' => 'formatter',
    'fasta_nucleotide_changer' => 'changer',
    'fastq_masker' => 'masker',
    'fastq_quality_converter' => 'qconverter',
    'fastq_quality_filter' => 'qfilter',
    'fastq_quality_trimmer' => 'qtrimmer',                        
	'fastq_to_fasta' => 'fq2fa',
    'fastx_artifacts_filter' => 'artifacts',
    'fastx_clipper' => 'clipper',    
    'fastx_collapser' => 'collapser',
    'fastx_quality_stats' => 'stats',
    'fastx_renamer' => 'renamer',
    'fastx_reverse_complement' => 'rc',
    'fastx_trimmer' => 'trimmer',
    'fastx_uncollapser' => 'uncollapser',
    );

our @program_params = qw(
	command    
	formatter|in_file
	formatter|out_file
	formatter|width
	changer|in_file
	changer|out_file
	masker|in_file
	masker|out_file
	masker|qualThreshold
	masker|replacementChar
	masker|asciiOffset
	qconverter|in_file
	qconverter|out_file
	qconverter|asciiOffset
	qfilter|minQual
	qfilter|minPct
	qfilter|in_file
	qfilter|out_file
	qfilter|asciiOffset
	qtrimmer|qualThreshold
	qtrimmer|minLength
	qtrimmer|in_file
	qtrimmer|out_file
	qtrimmer|asciiOffset
	fq2fa|in_file
	fq2fa|out_file
	fq2fa|asciiOffset	
	artifacts|in_file
	artifacts|out_file
	clipper|adapter
	clipper|minNT
	clipper|basesAfterAdapter
	clipper|in_file
	clipper|out_file
	clipper|minAdapterLen	
	clipper|asciiOffset	
	collapser|in_file
	collapser|out_file
	collapser|asciiOffset
	stats|in_file
	stats|out_file
	stats|asciiOffset
	renamer|in_file
	renamer|out_file
	renamer|renameType
	rc|in_file
	rc|out_file
    trimmer|in_file
    trimmer|out_file
    trimmer|firseBase
    trimmer|lastBase
    trimmer|trimFromEnd
    trimmer|minLength    
    trimmer|asciiOffset
    uncollapser|in_file
    uncollapser|out_file
    uncollapser|tabularInput   
    );

our @program_switches = qw(
	formatter|tabular
	formatter|showEmpty
	formatter|verbose
	changer|rna
	changer|dna
	changer|verbose
	masker|verbose
	qconverter|outputAscii
	qconverter|outputNumeric
	qfilter|verbose
	qtrimmer|verbose
	fq2fa|verbose
	fq2fa|keepNs
	fq2fa|rename
	artifacts|verbose
	clipper|verbose
	clipper|discardNonClipped
	clipper|discardClipped
	clipper|keepNs
	collapser|verbose
	stats|newOutput
	trimmer|verbose
	uncollapser|verbose	
);


    
our %param_translation = (
	'formatter|in_file' => 'i',
	'formatter|out_file' => 'o',
	'formatter|width' => 'w',
	'formatter|tabular' => 't',
	'formatter|showEmpty' => 'e',
	'formatter|verbose' => 'v',
	
	'changer|in_file' => 'i',
	'changer|out_file' => 'o',
	'changer|rna' => 'r',
	'changer|dna' => 'd',
	'changer|verbose' => 'v',
	
	'masker|in_file' => 'i',
	'masker|out_file' => 'o',
	'masker|qualThreshold' => 'q',
	'masker|replacementChar' => 'r',
	'masker|asciiOffset' => 'Q',
	'masker|verbose' => 'v',
		
	'qconverter|in_file' => 'i',
	'qconverter|out_file' => 'o',
	'qconverter|asciiOffset' => 'Q',
	'qconverter|outputAscii' => 'a',
	'qconverter|outputNumeric' => 'n',
		
	'qfilter|minQual' => 'q',
	'qfilter|minPct' => 'p',
	'qfilter|in_file' => 'i',
	'qfilter|out_file' => 'o',
	'qfilter|asciiOffset' => 'Q',
	'qfilter|verbose' => 'v',
	
	'qtrimmer|qualThreshold' => 't',
	'qtrimmer|minLength' => 'l',
	'qtrimmer|in_file' => 'i',
	'qtrimmer|out_file' => 'o',
	'qtrimmer|asciiOffset' => 'Q',
	'qtrimmer|verbose' => 'v',
	
	'fq2fa|in_file' => 'i',
	'fq2fa|out_file' => 'o',
	'fq2fa|verbose' => 'v',
	'fq2fa|keepNs' => 'n',
	'fq2fa|rename' => 'r',
	'fq2fa|asciiOffset' => 'Q',
		
	'artifacts|in_file' => 'i',
	'artifacts|out_file' => 'o',
	'artifacts|verbose' => 'v',
		
	'clipper|adapter' => 'a',
	'clipper|minNT' => 'l',
	'clipper|basesAfterAdapter' => 'd',
	'clipper|in_file' => 'i',
	'clipper|out_file' => 'o',
	'clipper|minAdapterLen' => 'M',	
	'clipper|verbose' => 'v',
	'clipper|discardNonClipped' => 'c',
	'clipper|discardClipped' => 'C',
	'clipper|keepNs' => 'n',
	'clipper|asciiOffset' => 'Q',
	
	'collapser|in_file' => 'i',
	'collapser|out_file' => 'o',
	'collapser|verbose' => 'v',
	'collapser|asciiOffset' => 'Q',
		
	'stats|in_file' => 'i',
	'stats|out_file' => 'o',
	'stats|newOutput' => '',
	'stats|asciiOffset' => 'Q',
		
	'renamer|in_file' => 'i',
	'renamer|out_file' => 'o',
	'renamer|renameType' => 'N',
	
	'rc|in_file' => 'i',
	'rc|out_file' => 'o',
    
    'trimmer|in_file' => 'i',
    'trimmer|out_file' => 'o',
    'trimmer|firseBase' => 'f',
    'trimmer|lastBase' => 'l',
    'trimmer|trimFromEnd' => 't',
    'trimmer|minLength' => 'm',
    'trimmer|asciiOffset' => 'Q',
    'trimmer|verbose' => 'v',
    
    'uncollapser|in_file' => 'i',
    'uncollapser|out_file' => 'o',
    'uncollapser|tabularInput' => 'c',
    'uncollapser|verbose' => 'v',
	   
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
    'formatter' => [qw(#>out )],
    'changer' => [qw(#>out )],
    'masker' => [qw(>out )],
    'qconverter' => [qw(#>out )],
    'qfilter' => [qw(#>out )],
    'qtrimmer' => [qw(#>out )],
    'fq2fa' => [qw(#>out )],
    'artifacts' => [qw( #>out )],
    'clipper' => [qw( >out )],
    'collapser' => [qw(#>out )],
    'stats' => [qw(#>out )],
    'renamer' => [qw(#>out )],
    'rc' => [qw(#>out )],
    'trimmer' => [qw(#>out )],
    'uncollapser' => [qw(#>out )],
  
    );

1;
