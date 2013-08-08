# $Id: Config.pm 16762 2010-01-25 18:25:25Z maj $
#
# BioPerl module for Bio::Tools::Run::SFFTools::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::SFFTools::Config - configurator for Bio::Tools::Run::SFFTools

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to SFFTools.pm.

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

package Bio::Tools::Run::SFFTools::Config;
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
    sfffile
    sffinfo
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
# composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'sfffile'       => 'sfffile',
    'sffinfo'       => 'sffinfo',
    );

our @program_params = qw(
	command
	sfffile|output
	sfffile|include
	sfffile|exclude
	sfffile|flowCycles
	sfffile|MIDsetname
	sfffile|MIDconfigFile
	sfffile|pick
    );

our @program_switches = qw(
	sfffile|rescore
	
	sffinfo|accession
	sffinfo|sequences
	sffinfo|qual
	sffinfo|flow
	sffinfo|tab
	sffinfo|notrim
	sffinfo|manifest
);

    
our %param_translation = (
	'sfffile|output' => 'o',
	'sfffile|include' => 'i',
	'sfffile|exclude' => 'e',
	'sfffile|flowCycles' => 'c',
	'sfffile|MIDsetname' => 's',
	'sfffile|MIDconfigFile' => 'mcf',
	'sfffile|pick' => 'pick',
	
	'sffinfo|accession' => 'a',
	'sffinfo|sequences' => 's',
	'sffinfo|qual' => 'q',
	'sffinfo|flow' => 'f',
	'sffinfo|tab' => 't',
	'sffinfo|notrim' => 'n',
	'sffinfo|manifest' => 'm',
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
    'sfffile' => [qw( *in #>out )],
    'sffinfo' => [qw( in >out )],
    );

1;
