#
# BioPerl module for Bio::Tools::Run::Bfast
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Bfast - a run wrapper for Bfast

=head1 SYNOPSIS



=cut

# Let the code begin...
package Bio::Tools::Run::Bfast;

use strict;
use warnings;

use Bio::Root::Root;
#TODO: need to update at some point
use Bio::Tools::Run::Bfast::Config;

use base qw(Bio::Tools::Run::WrapperBase Bio::Root::Root);
use Bio::Tools::Run::WrapperBase::CommandExts;

our $program_name = 'bfast';
our $use_dash = 1;
our $join = ' ';
our $default_cmd = 'bfast';

=head2 new



=cut

sub new { 
    my ($class, @args) = @_;
print "@args";    
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
	my @err = split("\n", $err);
	my $version = join(';', grep( /^Version/, @err));
	$version =~ m/(?<=Version: )(.*) git/i;
	return $1;
	
}


1;
