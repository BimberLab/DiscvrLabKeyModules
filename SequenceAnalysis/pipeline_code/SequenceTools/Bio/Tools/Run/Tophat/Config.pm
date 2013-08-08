#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Tophat::Config - configurator for Bio::Tools::Run::Tophat

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to Tophat.pm.

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

package Bio::Tools::Run::Tophat::Config;
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
    tophat
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'tophat'       => 'tophat',
    );

our @program_params = qw(
	command
    tophat|version
	tophat|output_dir
	tophat|mate_inner_dist
	tophat|mate_std_dev
	tophat|min_anchor_length
	tophat|splice_mismatches
	tophat|min_intron_length
	tophat|max_intron_length
	tophat|max_deletion_length
	tophat|max_insertion_length
	tophat|solexa_quals
	tophat|solexa1.3_quals
	tophat|quals
	tophat|integer_quals
	tophat|color
	tophat|num_threads
	tophat|max_multihits
	tophat|no_closure_search
	tophat|closure_search
	tophat|coverage_search
	tophat|microexon_search
	tophat|butterfly_search
	tophat|library_type
	
	tophat|segment_mismatches
	tophat|segment_length
	tophat|min-closure-exon
	tophat|min-closure-intron
	tophat|max-closure-intron
	tophat|min-coverage-exon
	tophat|max-coverage-exon
	tophat|min-segment-exon
	tophat|max-segment-exon
	

    );

our @program_switches = qw(
    tophat|anyornone
    tophat|recoverseeds
    tophat|multiple
    
	tophat|bowtie-n
	tophat|no-sort-bam
	tophat|no-convert-bam
    
);

 
    
our %param_translation = (
	'tophat|version' => 'version',
	'tophat|output_dir' => 'output_dir',
	'tophat|mate_inner_dist' => 'mate_inner_dist',
	'tophat|mate_std_dev' => 'mate_std_dev',
	'tophat|min_anchor_length' => 'min_anchor_length',
	'tophat|splice_mismatches' => 'splice_mismatches',
	'tophat|min_intron_length' => 'min_intron_length',
	'tophat|max_intron_length' => 'max_intron_length',
	'tophat|max_deletion_length' => 'max_deletion_length',
	'tophat|max_insertion_length' => 'max_insertion_length',
	'tophat|solexa_quals' => 'solexa_quals',
	'tophat|solexa1.3_quals' => 'solexa1.3_quals',
	'tophat|quals' => 'quals',
	'tophat|integer_quals' => 'integer_quals',
	'tophat|color' => 'color',
	'tophat|num_threads' => '',
	'tophat|max_multihits' => 'max_multihits',
	'tophat|no_closure_search' => 'no_closure_search',
	'tophat|closure_search' => 'closure_search',
	'tophat|coverage_search' => 'coverage_search',
	'tophat|microexon_search' => 'microexon_search',
	'tophat|butterfly_search' => 'butterfly_search',
	'tophat|library_type' => 'library_type',
	
	'tophat|segment_mismatches' => 'segment_mismatches',
	'tophat|segment_length' => 'segment_length',
	'tophat|min-closure-exon' => 'min-closure-exon',
	'tophat|min-closure-intron' => 'min-closure-intron',
	'tophat|max-closure-intron' => 'max-closure-intron',
	'tophat|min-coverage-exon' => 'min-coverage-exon',
	'tophat|max-coverage-exon' => 'max-coverage-exon',
	'tophat|min-segment-exon' => 'min-segment-exon',
	'tophat|max-segment-exon' => 'max-segment-exon',
	
    'tophat|anyornone' => 'anyornone',
    'tophat|recoverseeds' => 'recoverseeds',
    'tophat|multiple' => 'multiple',
    
	'tophat|bowtie-n' => 'bowtie-n',
	'tophat|no-sort-bam' => 'no-sort-bam',
	'tophat|no-convert-bam' => 'no-convert-bam',
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
    'tophat' => [qw( index in# )],
    );

1;
