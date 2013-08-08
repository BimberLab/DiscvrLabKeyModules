# $Id: Config.pm 16762 2010-01-25 18:25:25Z maj $
#
# BioPerl module for Bio::Tools::Run::Blat2::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Blat2::Config - configurator for Bio::Tools::Run::Blat2

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to Blat2.pm.

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

package Bio::Tools::Run::Blat2::Config;
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
    blat
);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'blat'       => 'blat',
    );

our @program_params = qw(
	command
    blat|dbType
    blat|queryType
    blat|DB_Type
    blat|ooc
    blat|tileSize
    blat|stepSize
    blat|oneOff
    blat|minMatch
    blat|minScore
    blat|minIdentity
    blat|maxGap
    blat|makeOoc
    blat|repMatch
    blat|mask
    blat|qMask
    blat|repeats
    blat|minRepDivergence
    blat|dots
    blat|out
    blat|maxIntron
    blat|extendThroughN
    );

our @program_switches = qw(
    blat|noHead
    blat|prot
    blat|trimT
    blat|noTrimA
    blat|trimHardA
    blat|fastMap
    blat|fine
);

 
    
our %param_translation = (
    'blat|dbType' => 't',
    'blat|queryType' => 'q',
    'blat|ooc' => 'ooc',
    'blat|tileSize' => 'tileSize',
    'blat|stepSize' => 'stepSize',
    'blat|oneOff' => 'oneOff',
    'blat|minMatch' => 'minMatch',
    'blat|minScore' => 'minScore',
    'blat|minIdentity' => 'minIdentity',
    'blat|maxGap' => 'maxGap',
    'blat|makeOoc' => 'makeOoc',
    'blat|repMatch' => 'repMatch',
    'blat|mask' => 'mask',
    'blat|qMask' => 'qMask',
    'blat|repeats' => 'repeats',
    'blat|minRepDivergence' => 'minRepDivergence',
    'blat|dots' => 'dots',
    'blat|out' => 'out',
    'blat|maxIntron' => 'maxIntron',
    'blat|extendThroughN' => 'extendThroughN',
    'blat|noHead' => 'noHead',
    'blat|prot' => 'prot',
    'blat|trimT' => 'trimT',
    'blat|noTrimA' => 'noTrimA',
    'blat|trimHardA' => 'trimHardA',
    'blat|fastMap' => 'fastMap',
    'blat|fine' => 'fine',
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
    'blat' => [qw( db in out )],
    );

1;
