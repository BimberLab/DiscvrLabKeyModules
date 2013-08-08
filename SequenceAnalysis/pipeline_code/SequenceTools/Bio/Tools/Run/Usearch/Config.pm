#
# BioPerl module for Bio::Tools::Run::Usearch::Config
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Usearch::Config - configurator for Bio::Tools::Run::Usearch

=head1 SYNOPSIS

Not used directly.

=head1 DESCRIPTION

Exports global configuration variables (as required by
L<Bio::Tools::Run::WrapperBase::CommandExts>) to Usearch.pm.

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

package Bio::Tools::Run::Usearch::Config;
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
    usearch

);

# composite commands: pseudo-commands that run a 
# sequence of commands
# composite command prefix => list of prefixes of commands this
#  composite command runs
#

# prefixes only for commands that take params/switches...
our %command_prefixes = (
    'usearch'       => 'u',
    );

our @program_params = qw(
	command		
	u|uc2fasta
	u|uc2fastax
	u|uc2clstr
	u|clstr2uc
	u|output
	u|input
	u|mincodons
	u|stepwords
	u|dbstep
	u|maxlen
	u|minlen
	u|bump
	u|log
	u|sort
	u|cluster
	u|seedsout
	u|id
	u|uc
    );


our @program_switches = qw(
	u|rev
	u|quiet
	u|version
	
    );

our %param_translation = (
	'u|uc2fasta' => 'uc2fasta',
	'u|uc2fastax' => 'uc2fastax',
	'u|uc2clstr' => 'uc2clstr',
	'u|clstr2uc' => 'clstr2uc',
	'u|sort' => 'sort',
	'u|output' => 'output',
	'u|input' => 'input',
	'u|mincodons' => 'mincodons',
	'u|stepwords' => 'stepwords',
	'u|dbstep' => 'dbstep',
	'u|maxlen' => 'maxlen',
	'u|minlen' => 'minlen',
	'u|bump' => 'bump',
	'u|log' => 'log',
	'u|cluster' => 'cluster',
	'u|seedsout' => 'seedsout',
	'u|id' => 'id',
	'u|uc' => 'uc',
	
	'u|rev' => 'rev',
	'u|quiet' => 'quiet',
	'u|version' => 'version',
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
    'u' => [qw( #>out )],     
    );

1;
