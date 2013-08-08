# $Id$
#
# BioPerl module for Bio::Tools::Run::Usearch
#
# 

=head1 NAME

Bio::Tools::Run::Usearch


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
package Bio::Tools::Run::Usearch;
use strict;
use warnings;
# use lib '../../../../live';
# use lib '../../..';
use Bio::Root::Root;
use Bio::Tools::Run::Usearch::Config;


use base qw(Bio::Tools::Run::WrapperBase Bio::Root::Root);
use Bio::Tools::Run::WrapperBase::CommandExts;

our $program_name = '*usearch';
our $use_dash = 'double';
our $join = ' ';

=head2 new

 Title   : new
 Usage   : my $obj = new Bio::Tools::Run::Usearch();
 Function: Builds a new Bio::Tools::Run::Usearch object
 Returns : an instance of Bio::Tools::Run::Usearch
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
	my @output = split("\n", $out);
	my $version = join(';', grep( /^usearch v/, @output));	
	$version =~ m/(?<=USEARCH )(.*)/i;
	return $1;
	
}


1;
