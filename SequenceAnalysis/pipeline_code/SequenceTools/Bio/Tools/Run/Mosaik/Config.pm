# $Id: Config.pm 16762 2010-01-25 18:25:25Z maj $
#
# BioPerl module for Bio::Tools::Run::Mosaik::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Mosaik::Config - configurator for Bio::Tools::Run::SFFTools

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to Mosaik.pm.

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

package Bio::Tools::Run::Mosaik::Config;
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
    MosaikBuild
    MosaikAligner
    MosaikText
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'MosaikBuild'       => 'mb',
    'MosaikAligner'       => 'ma',
    'MosaikText'       => 'mt'    
    );

our @program_params = qw(
	command
		
    mb|fasta
    mb|fasta2
    mb|fastq
    mb|fastq2
    mb|short_read
    mb|technology
    mb|out_file
    mb|out_archive

    ma|in_file
	ma|out_file
	ma|index
	ma|unaligned_file
	ma|mode
	ma|hash_size
	ma|processors
	ma|align_threshold
	ma|max_mismatch
	ma|max_mismatch_pct
	ma|min_pct_aligned	
	ma|max_hash_positions
	ma|jump_db
	ma|local_search
	ma|banded
	ma|pe_neural_network
	ma|se_neural_network
	
	mt|input_reads
	mt|fastq_store
	mt|display
	mt|input_archive
	mt|unique_only
	mt|axt_file
	mt|bam_file
	mt|bed_file
	mt|eland_file
	mt|sam_file				
    );


our @program_switches = qw(
	ma|use_aligned_length
	ma|output_multiple
	ma|quiet

    mb|assignQual
    mb|color_space
    mb|prefix
    mb|quiet
);

our %param_translation = (
    'mb|fasta' => 'fr',
    'mb|fasta2' => 'f2',
    'mb|fastq' => 'q',
    'mb|fastq2' => 'q2',
    'mb|short_read' => 'srf',
    'mb|technology' => 'st',
    'mb|out_file' => 'out',
    'mb|out_archive' => 'oa',
    'mb|assignQual' => 'assignQual',
    'mb|color_space' => 'cs',
    'mb|prefix' => 'p',
    'mb|quiet' => 'quiet',

    'ma|in_file' => 'in',
	'ma|out_file' => 'out',
	'ma|index' => 'ia',
	'ma|mode' => 'm',
	'ma|hash_size' => 'hs',
	'ma|processors' => 'p',
	'ma|align_threshold' => 'act',
	'ma|max_mismatch' => 'mm',
	'ma|max_mismatch_pct' => 'mmp',
	'ma|min_pct_aligned' => 'minp',
	'ma|use_aligned_length' => 'mmal',
	'ma|max_hash_positions' => 'mhp',
	'ma|jump_db' => 'j',
	'ma|local_search' => 'ls',
	'ma|banded' => 'bw',
	'ma|pe_neural_network' => 'annpe',
	'ma|se_neural_network' => 'annse',
	'ma|output_multiple' => 'om',
	
	'ma|quiet' => 'quiet',
	'mt|input_reads' => 'ir',
	'mt|fastq_store' => 'fastq',
	'mt|display' => 'screen',
	'mt|input_archive' => 'in',
	'mt|unique_only' => 'u',
	'mt|axt_file' => 'axt',
	'mt|bam_file' => 'bam',
	'mt|bed_file' => 'bed',
	'mt|eland_file' => 'eland',
	'mt|sam_file' => 'sam'
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
    'mb' => [qw( #>out )],
    'ma' => [qw( #>out )],
    'mt' => [qw( #>out )],      
    );

1;
