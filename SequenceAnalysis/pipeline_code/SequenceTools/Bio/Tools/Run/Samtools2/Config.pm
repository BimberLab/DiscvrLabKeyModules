# $Id: Config.pm 16762 2010-01-25 18:25:25Z maj $
#
# BioPerl module for Bio::Tools::Run::Samtools2::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
# Cared for by Mark A. Jensen <maj -at- fortinbras -dot- us>
#
# Copyright Mark A. Jensen
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Samtools2::Config - configurator for Bio::Tools::Run::Samtools2

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to Samtools2.pm.

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

=head1 AUTHOR - Mark A. Jensen

Email maj -at- fortinbras -dot- us

=head1 APPENDIX

The rest of the documentation details each of the object methods.
Internal methods are usually preceded with a _

=cut

# Let the code begin...

package Bio::Tools::Run::Samtools2::Config;
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
    view
    sort
    index
    merge
    faidx
    pileup
    fixmate
    rmdup
    calmd
    import
    flagstat
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'view'       => 'view',
    'sort'       => 'srt',
    'index'      => 'idx',
    'merge'      => 'mrg',
    'faidx'      => 'fai',
    'pileup'     => 'pup',
    'calmd'     => 'md',
    'import'     => 'in',    
    'flagstat'     => 'flag',
    );

our @program_params = qw(
    command
    view|tab_delim
    view|out_file
    view|pass_flags
    view|filt_flags
    view|refseq
    view|qual_threshold
    view|library
    view|read_group
    srt|mem_hint
    mrg|headers_in
    pup|refseq
    pup|map_qcap
    pup|ref_list
    pup|site_list
    pup|theta
    pup|n_haplos
    pup|exp_hap_diff
    pup|indel_prob
    );

our @program_switches = qw(
    view|bam_output
    view|uncompressed
    view|add_header
    view|only_header
    view|sam_input
    view|print_count
    srt|sort_by_names
    mrg|sort_by_names
    pup|qual_last_col
    pup|sam_input
    pup|indels_only
    pup|call_cons
    pup|genot_L
    md|replace_match_with_eq
    md|output_compressed
    md|output_uncompressed
    md|input_is_sam
    md|perform_realignment
);

our %param_translation = (
    'view|tab_delim' => 't',
    'view|out_file' => 'o',
    'view|pass_flags' => 'f',
    'view|refseq'   => 'T',
    'view|filt_flags' => 'F',
    'view|qual_threshold' => 'q',
    'view|library' => 'l',
    'view|read_group' => 'r',
    'view|bam_output' => 'b',
    'view|uncompressed' => 'u',
    'view|add_header' => 'h',
    'view|only_header' => 'H',
    'view|sam_input' => 'S',
    'view|print_count' => 'c',
    'srt|mem_hint' => 'm',
    'srt|sort_by_names' => 'n',
    'mrg|headers_in' => 'h',
    'mrg|sort_by_names' => 'n',
    'pup|refseq' => 'f',
    'pup|map_qcap' => 'M',
    'pup|ref_list' => 't',
    'pup|site_list' => 'l',
    'pup|theta' => 'T',
    'pup|n_haplos' => 'N',
    'pup|exp_hap_diff' => 'f',
    'pup|indel_prob' => 'I',
    'pup|qual_last_col' => 's',
    'pup|sam_input' => 'S',
    'pup|indels_only' => 'i',
    'pup|call_cons' => 'c',
    'pup|genot_L' => 'g',
    'md|replace_match_with_eq' => 'e',   
    'md|output_compressed' => 'u',
    'md|output_uncompressed' => 'b',
    'md|input_is_sam' => 'S',
    'md|perform_realignment' => 'r',
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
    'view' => [qw( bam #*rgn #>out )],
    'sort' => [qw( bam pfx )],
    'index' => [qw( bam )],
    'merge' => [qw( obm *ibm )],
    'faidx' => [qw( fas #*rgn )],
    'pileup' => [qw( bam >out )],
    'fixmate' => [qw( ibm obm )],
    'rmdup' => [qw( ibm obm )],
    'calmd' => [qw( bam fas >out)],
    'import' => [qw( ref sam bam )],
    'flagstat' => [qw( bam #>out)],        
    );

1;
