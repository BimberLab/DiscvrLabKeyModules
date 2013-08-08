#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Lastz::Config - configurator for Bio::Tools::Run::Lastz

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to Lastz.pm.

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

package Bio::Tools::Run::Lastz::Config;
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
    lastz
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'lastz'       => 'lastz',
    );

our @program_params = qw(
	command
    lastz|strand
	lastz|scores
	lastz|match
	lastz|gap
	lastz|ambiguous
	lastz|step
	lastz|maxwordcount
	lastz|masking
	lastz|seed
	lastz|hspthresh
	lastz|mismatch
	lastz|identity
	lastz|continuity
	lastz|coverage
	lastz|matchcount
	lastz|output
	lastz|format
    );

our @program_switches = qw(
    lastz|anyornone
    lastz|recoverseeds
    lastz|multiple
);

 
    
our %param_translation = (
    'lastz|strand' => 'strand',
	'lastz|scores' => 'scores',
	'lastz|match' => 'match',
	'lastz|gap' => 'gap',
	'lastz|ambiguous' => 'ambiguous',
	'lastz|step' => 'step',
	'lastz|maxwordcount' => 'maxwordcount',
	'lastz|masking' => 'masking',
	'lastz|seed' => 'seed',
	'lastz|hspthresh' => 'hspthresh',
	'lastz|mismatch' => 'mismatch',
	'lastz|identity' => 'identity',
	'lastz|continuity' => 'continuity',
	'lastz|coverage' => 'coverage',
	'lastz|matchcount' => 'matchcount',
	'lastz|output' => 'output',
	'lastz|format' => 'format',
    'lastz|anyornone' => 'anyornone',
    'lastz|recoverseeds' => 'recoverseeds',
    'lastz|multiple' => 'multiple',
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
    'lastz' => [qw( target query )],
    );

1;
