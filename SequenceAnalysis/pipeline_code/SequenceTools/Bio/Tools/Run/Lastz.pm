#
# BioPerl module for Bio::Tools::Run::Lastz
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Lastz - a run wrapper for Lastz

=head1 SYNOPSIS



=cut

# Let the code begin...
package Bio::Tools::Run::Lastz;

use strict;
use warnings;

use Bio::Root::Root;
#TODO: need to update at some point
use Bio::Tools::Run::Lastz::Config;

use base qw(Bio::Tools::Run::WrapperBase Bio::Root::Root);
use Bio::Tools::Run::WrapperBase::CommandExts;

our $program_name = '*lastz';
our $use_dash = 'double';
our $join = '=';
our $default_cmd = 'lastz';

=head2 new



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



1;
