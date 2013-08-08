# $Id$
#
# BioPerl module for Bio::Tools::Run::CDHit
#
# 

=head1 NAME

Bio::Tools::Run::CDHit


=head2 Support

Please direct usage questions or support issues to the mailing list:

L<bioperl-l@bioperl.org>

rather than to the module maintainer directly. Many experienced and
reponsive experts will be able look at the problem and quickly
address it. Please include a thorough description of the problem
with code and data examples if at all possible.

=head1 APPENDIX

The rest of the documentation details each of the object methods.
Internal methods are usually preceded with a _

=cut

# Let the code begin...
package Bio::Tools::Run::CDHit;
use strict;
use warnings;

use Bio::Root::Root;
use Bio::Tools::Run::CDHit::Config;


use base qw(Bio::Tools::Run::WrapperBase Bio::Root::Root);
use Bio::Tools::Run::WrapperBase::CommandExts;

our $program_name = '*cd-hit';
our $use_dash = 1;
our $join = ' ';

=head2 new

 Title   : new
 Usage   : my $obj = new Bio::Tools::Run::CDHit();
 Function: Builds a new Bio::Tools::Run::CDHit object
 Returns : an instance of Bio::Tools::Run::CDHit
 Args    :

=cut

sub new { 
    my ($class, @args) = @_;
    my $self = $class->SUPER::new(@args);
    return $self;
}

sub run { shift->_run(@_) }

sub version {
	my $self = shift;
	my ($out, $err);
	IPC::Run::run([$self->executable, '--version'], '>', \$out, '2>', \$err);	
	$out =~ m/(?<=CD-HIT version )(\S*)/i;
	return $1;
	
}


1;