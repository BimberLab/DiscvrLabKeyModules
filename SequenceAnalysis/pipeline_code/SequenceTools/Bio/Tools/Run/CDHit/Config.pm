#
# BioPerl module for Bio::Tools::Run::CDHit::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::CDHit::Config - configurator for Bio::Tools::Run::CDHit

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to CDHit.pm.

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

package Bio::Tools::Run::CDHit::Config;
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
    cd-hit-454

);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'cd-hit-454'       => '454',
    );

our @program_params = qw(
	command		
	454|input
	454|output
	454|identity
	454|band_width
	454|word_length
	454|alignment_coverage_l
	454|alignment_length_l
	454|alignment_coverage_s
	454|alignment_length_s
	454|accurate_mode
	454|max_indel_size
	
    );


our @program_switches = qw(
	
    );

our %param_translation = (
	'454|input' => 'i',
	'454|output' => 'o',
	'454|identity' => 'c',
	'454|band_width' => 'b',
	'454|word_length' => 'n',
	'454|alignment_coverage_l' => 'aL',
	'454|alignment_length_l' => 'AL',
	'454|alignment_coverage_s' => 'aS',
	'454|alignment_length_s' => 'AS',
	'454|accurate_mode' => 'g',
	'454|max_indel_size' => 'D',
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
    '454' => [qw( #>out )],     
    );

1;