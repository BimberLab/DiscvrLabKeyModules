#
# BioPerl module for Bio::Tools::Run::Blat2
#
# Please direct questions and support issues to <bioperl-l@bioperl.org>
#
#
# You may distribute this module under the same terms as perl itself

# POD documentation - main docs before the code

=head1 NAME

Bio::Tools::Run::Blat2 - a run wrapper for the BLAT *BETA*

=head1 SYNOPSIS

 # create BLAT factory
 my $factory = Bio::Tools::Run::Blat2(  
			-sam_input => 1,
            -bam_output => 1 );
            
 $samt->run( -in => "mysam.fasta", -out => "mysam.psl" );

 # now create an assembly
 $assy = Bio::IO::Assembly->new( -file => "mysam.srt.bam",
                                 -refdb => "myref.fas" );

=head1 DESCRIPTION

This is a wrapper for running BLAT, written by Jim Kent.




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
package Bio::Tools::Run::Blat2;

use strict;
use warnings;

use Bio::Root::Root;
#TODO: need to update at some point
use Bio::Tools::Run::Blat2::Config;

use base qw(Bio::Tools::Run::WrapperBase Bio::Root::Root);
use Bio::Tools::Run::WrapperBase::CommandExts;

our $program_name = '*blat';
our $use_dash = 1;
our $join = '=';
our $default_cmd = 'blat';

=head2 new

 Title   : new
 Usage   : my $obj = new Bio::Tools::Run::Blat2();
 Function: Builds a new Bio::Tools::Run::Blat2 object
 Returns : an instance of Bio::Tools::Run::Blat2
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
	
	return $self->{'_result'}->{'file_name'};

}


=head2 result()

 Title   : result
 Usage   : $bowtiefac->result( [-want => $type|$format] )
 Function: return result in wanted format
 Returns : results
 Args    : [optional] hashref of wanted type

=cut

sub result {
	my ($self, @args) = @_;
use Data::Dumper;
#print Dumper($self);
	
	my $want = $self->want ? $self->want : $self->want($self->_rearrange([qw(WANT)],@args));
	my $cmd = $self->command if $self->can('command');
	my $format = $self->{'_result'}->{'format'};

	return $self->{'_result'}->{'format'} if (defined $want && $want eq 'format');
	return $self->{'_result'}->{'file_name'} if (!$want || $want eq 'raw');
	return $self->{'_result'}->{'file'} if ($want =~ m/^Bio::Root::IO/);
	
	for ($cmd) {
		m/(?:blat)/ && do {
			my $scaffold;
			for ($format) {
				$want =~ m/^Bio::Assembly::Scaffold/ && do {
					unless (defined $self->{'_result'}->{'object'} &&
						ref($self->{'_result'}->{'object'}) =~ m/^Bio::Assembly::Scaffold/) {
							$self->{'_result'}->{'object'} =
								$self->_export_results( $self->{'_result'}->{'file_name'},
								                       -index => $self->{'_result'}->{'index'},
								                       -keep_asm => 1 );
					}
					last;
				};
				$want =~ m/^GenericOutput/ && do {
					my @result = _blatToGeneric($self->{'_result'}->{'file_name'});
					$self->{'_result'}->{'object'} = \@result;					
					#print Dumper($self->{'_result'}->{'object'});
					last;
				};
				do {
					$self->warn("Don't know how to create a $want object for $cmd.");
					return;
				}
			};
			last;
		};
	}
	
	return $self->{'_result'}->{'object'};
}

sub version {
	my $self = shift;	
	my ($out, $err);
	IPC::Run::run([$self->executable], '>', \$out, '2>', \$err);
	my @out = split("\n", $out);
	my $version = join(';', grep( /^blat - Standalone BLAT/, @out));
	$version =~ m/(?<=blat - Standalone BLAT )(.*) fast sequence search/i;
	return $1;
	
}

1;
