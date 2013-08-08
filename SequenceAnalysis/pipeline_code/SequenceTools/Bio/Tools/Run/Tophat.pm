#
# BioPerl module for Bio::Tools::Run::Tophat
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Tophat - a run wrapper for the Tophat

=head1 SYNOPSIS



=head1 DESCRIPTION




=head1 RUNNING COMMANDS

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
package Bio::Tools::Run::Tophat;

use strict;
use warnings;

use Bio::Root::Root;
use Bio::Tools::Run::Tophat::Config;

use base qw(Bio::Tools::Run::WrapperBase Bio::Root::Root);
use Bio::Tools::Run::WrapperBase::CommandExts;

our $program_name = '*Tophat';
our $use_dash = 1;
our $join = ' ';
our $default_cmd = 'tophat';

=head2 new

 Title   : new
 Usage   : my $obj = new Bio::Tools::Run::Tophat();
 Function: Builds a new Bio::Tools::Run::Tophat object
 Returns : an instance of Bio::Tools::Run::Tophat
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
	
	return 1;#$self->{'_result'}->{'file_name'};

}

=head2  version

 Title   : version
 Usage   : $v = $prog->version();
 Function: Determine the version number of the program
 Example :
 Returns : string or undef
 Args    : none

=cut

sub version {
    my ($self) = @_;
    return unless $self->executable;
    my $exe = $self->executable;
    my $string = `$exe -h 2>&1`;
   
	my @out = split("\n", $string);
	my $v = join(';', grep( /^TopHat/i, @out));	
	$v =~ m/TopHat v([\d\.]*)/i;
	return $self->{'_progversion'} = $1 || undef;	
}

1;
