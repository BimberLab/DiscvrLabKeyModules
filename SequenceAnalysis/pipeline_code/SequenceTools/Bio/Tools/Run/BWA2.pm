#
# BioPerl module for Bio::Tools::Run::BWA2
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::BWA2 - a run wrapper for the BWA

=head1 SYNOPSIS



=head1 DESCRIPTION




=head1 RUNNING COMMANDS

=head1 OPTIONS


Reasonably mnemonic names have been assigned to the single-letter
command line options. These are the names returned by
C<available_parameters>, and can be used in the factory constructor
like typical BioPerl named parameters.


=head1 FILES

When a command requires filenames, these are provided to the
C<run()> method, not the constructor (C<new()>). To see the set of
files required by a command, use C<available_parameters('filespec')>
or the alias C<filespec()>:

If files are not specified per the filespec, text sent to STDOUT and
STDERR is saved and is accessible with C<$bwafac->stdout()> and
C<$bwafac->stderr()>.

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
package Bio::Tools::Run::BWA2;

use strict;
use warnings;

use Bio::Root::Root;
#TODO: need to update at some point
use Bio::Tools::Run::BWA2::Config;

use base qw(Bio::Tools::Run::WrapperBase Bio::Root::Root);
use Bio::Tools::Run::WrapperBase::CommandExts;

our $program_name = 'bwa';
our $use_dash = 1;
our $join = ' ';
our $default_cmd = 'bwa';

=head2 new

 Title   : new
 Usage   : my $obj = new Bio::Tools::Run::BWA2();
 Function: Builds a new Bio::Tools::Run::BWA2 object
 Returns : an instance of Bio::Tools::Run::BWA2
 Args    :

=cut

sub new { 
    my ($class, @args) = @_;
    
    #set defaults
   	unless (grep /command/, @args) {
		push @args, '-command', $default_cmd;
		}
		
    my $self = $class->SUPER::new(@args);    
    
    return $self;
}

sub run { 	 
	my $self = shift;
	my %args = @_;
	
	$self->_run(%args);
  
	$self->{'_result'}->{'file_name'} = $args{'-out'};
	$self->{'_result'}->{'file'} = Bio::Root::IO->new( -file => $args{'-out'} );
	
	return 1;#return $self->{'_result'}->{'file_name'};

}

sub version {
	my $self = shift;	
	my ($out, $err);
	IPC::Run::run([$self->executable], '>', \$out, '2>', \$err);
	my @out = split("\n", $err);
	my $version = join(';', grep( /^Version/, @out));
	$version =~ m/(?<=Version: )(.*)/i;
	return $1;
	
}

1;
