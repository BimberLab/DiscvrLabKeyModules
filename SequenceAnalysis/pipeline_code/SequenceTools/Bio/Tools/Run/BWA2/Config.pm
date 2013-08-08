# $Id: Config.pm 16762 2010-01-25 18:25:25Z maj $
#
# BioPerl module for Bio::Tools::Run::BWA2::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::BWA2::Config - configurator for Bio::Tools::Run::SFFTools

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to BWA2.pm.

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

package Bio::Tools::Run::BWA2::Config;
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
    run
    index
    aln
    samse
    sampe
    dbwtsw
    bwasw
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

our %composite_commands = (
    'run'        => [qw( map asm c2q )]
    );

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'aln'        => 'aln',
    'dbwtsw'     => 'bwt',
    'index'      => 'idx',
    'samse'      => 'sms',
    'sampe'      => 'smp',
    'run'        => 'run',
    'bwasw'		 => 'bsw',
    );

our @program_params = qw(
    command
    idx|output_prefix
    idx|algorithm
    aln|max_edit_dist
    aln|max_gap_opens
    aln|max_gap_extns
    aln|deln_protect_3p
    aln|deln_protect_ends
    aln|subseq_seed
    aln|max_edit_dist_seed
    aln|n_threads
    aln|mm_penalty
    aln|gap_open_penalty
    aln|gap_extn_penalty
    aln|subopt_hit_threshold
    aln|trim_parameter
    sms|hit_limit
    smp|max_insert_size
    smp|max_read_occur
    bwt|match_score
    bwt|mm_penalty
    bwt|gap_open_penalty
    bwt|gap_extn_penalty
    bwt|n_threads
    bwt|band_width
    bwt|rel_min_score_threshold
    bwt|threshold_adj_coeff
    bwt|z_best
    bwt|max_sa_interval
    bwt|min_seeds_to_skip
    bsw|match_score
    bsw|mm_penalty
    bsw|gap_open_penalty
    bsw|gap_extn_penalty
    bsw|n_threads
    bsw|band_width
    bsw|rel_min_score_threshold
    bsw|threshold_adj_coeff
    bsw|z_best
    bsw|max_sa_interval
    bsw|min_seeds_to_skip
    

    );

our @program_switches = qw(
    idx|color_space_idx
    aln|reverse_no_comp
    aln|no_iter_search
);

our %param_translation = (
    'idx|output_prefix' => 'p',
    'idx|algorithm' => 'a',
    'aln|max_edit_dist' => 'n',
    'aln|max_gap_opens' => 'o',
    'aln|max_gap_extns' => 'e',
    'aln|deln_protect_3p' => 'd',
    'aln|deln_protect_ends' => 'i',
    'aln|subseq_seed' => 'l',
    'aln|max_edit_dist_seed' => 'k',
    'aln|n_threads' => 't',
    'aln|mm_penalty' => 'M',
    'aln|gap_open_penalty' => 'O',
    'aln|gap_extn_penalty' => 'E',
    'aln|subopt_hit_threshold' => 'R',
    'aln|trim_parameter' => 'q',
    'sms|hit_limit' => 'n',
    'smp|max_insert_size' => 'a',
    'smp|max_read_occur' => 'o',
    'bwt|match_score' => 'a',
    'bwt|mm_penalty' => 'b',
    'bwt|gap_open_penalty' => 'q',
    'bwt|gap_extn_penalty' => 'r',
    'bwt|n_threads' => 't',
    'bwt|band_width' => 'w',
    'bwt|rel_min_score_threshold' => 'T',
    'bwt|threshold_adj_coeff' => 'c',
    'bwt|z_best' => 'z',
    'bwt|max_sa_interval' => 's',
    'bwt|min_seeds_to_skip' => 'N',
    'bsw|match_score' => 'a',
    'bsw|mm_penalty' => 'b',
    'bsw|gap_open_penalty' => 'q',
    'bsw|gap_extn_penalty' => 'r',
    'bsw|n_threads' => 't',
    'bsw|band_width' => 'w',
    'bsw|rel_min_score_threshold' => 'T',
    'bsw|threshold_adj_coeff' => 'c',
    'bsw|z_best' => 'z',
    'bsw|max_sa_interval' => 's',
    'bsw|min_seeds_to_skip' => 'N'    
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
    'run'        => [qw( faq fas faq )],
    'index'       => [qw( fas )],     
    'aln'         => [qw( fas faq >sai )],     
    'samse'       => [qw( fas sai faq >sam )],     
    'sampe'       => [qw( fas sai1 sai2 faq1 faq2 >sam )],     
    'dbwtsw'      => [qw( fas faq )],
    'bwasw'       => [qw( fas faq  >sam)]
    );


1;
