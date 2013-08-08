#!/usr/bin/perl

=head1 NAME

PROGRAM  : Conversion.pm

=head1 SYNOPSIS


=head1 DESCRIPTION

# PURPOSE  : 

=head1 AUTHOR 

Ben Bimber

=cut


package SequenceAnalysis::Conversion;

use strict;
use warnings;
use Data::Dumper;

use Bio::SeqIO;
use Bio::Seq::Quality;
use Bio::Tools::Run::Samtools2;
use File::Basename;	 
use Getopt::Std;

sub sam2bam {
	
	my %args = @_;

	#sanity checking
	my @required = ('-index', '-basename');	
	foreach (@required){
		if (!$args{$_}){die "ERROR: Missing required param: $_"};		
	}
	
	print "Converting SAM file to BAM:\n";
	
	my $sam = $args{'-basename'} . ".sam";
	if (-s $sam < 50){
		print "The SAM file is too small (".(-s $sam).").  There's probably no alignments in it.\n";
		print "Expected to find file: " . $sam . "\n";
		return 0;		
	}

	
	if (!-e $args{'-index'} . '.fai'){	
	 	my $ind = Bio::Tools::Run::Samtools2->new( 
	 		-command => 'faidx',
	        -program_dir => $ENV{SAMTOOLSPATH} || '',       		
			);
			
		$ind->run( -fas => $args{'-index'} );
		
		SequenceAnalysis::Utils::_write_factory_log($ind);
	};
	
	$args{'-basename'} = File::Spec->rel2abs($args{'-basename'});
		
 	my $bam = Bio::Tools::Run::Samtools2->new( 
 		-command => 'import',
        -program_dir => $ENV{SAMTOOLSPATH} || '',
		);
	
	$bam->run(
		-ref => $args{'-index'} . '.fai',		
		-sam => $args{'-basename'} . ".sam", 
		-bam => $args{'-basename'} . ".bam",  
		);	
		
	SequenceAnalysis::Utils::_write_factory_log($bam, {-noShowVersion=>1});
		
	if ($args{'-deleteSam'} && $args{'-deleteSam'} == 1){
		unlink($args{'-basename'} . ".sam");	
	}
	
	return $args{'-basename'} . ".bam";
}

1;