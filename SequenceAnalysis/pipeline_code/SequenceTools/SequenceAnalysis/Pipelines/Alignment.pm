#!/usr/bin/perl

=head1 NAME

PROGRAM  : Alignment.pm

=head1 SYNOPSIS


=head1 DESCRIPTION

PURPOSE  : This pipeline will take an input FASTA/Q file and perform an alignment

=head1 AUTHOR 

Ben Bimber

=cut

package SequenceAnalysis::Pipelines;

use strict;
use warnings;
use Cwd;
use Data::Dumper;
use File::Basename;	 
use File::Util;
use File::Path qw(make_path);

use SequenceAnalysis::Tasks;



sub alignment {

	my $args = shift;
	
	#sanity checking
	my @required = ('-aligners', '-input_file', '-baseUrl', '-containerPath');
	foreach (@required){
		if (!$$args{$_}){print "ERROR: Missing required param: $_";return;};		
	}	      

    print "Working Directory: " . getcwd . "\n";

	my $config = {};

	#allow user override of defaults
	foreach ((keys %{$$args{-config}})){
		$$config{$_} = ${$$args{-config}}{$_};
	}	
			
	if (!-e $$args{-input_file}){
		print "ERROR: File Missing: ".$$args{-input_file}."\n";
		next;	
	}
		
	# Prepare input file
	my @fileparse = fileparse($$args{-input_file}, qr/\.[^.]*/);
	my $basename;
	my $i = 1;
	if ($$args{-basename}){
		$basename = $$args{-basename};
	}
	else {
		$basename = $fileparse[0];			
	}		

	my $working_dir = $$args{-working_dir} ? $$args{-working_dir} : '';
	$working_dir = File::Spec->catfile('.', $working_dir, $basename, 'Alignment');	

	if (!-e $working_dir){
		make_path($working_dir) || die "Unable to make directory: $working_dir\n";
	}			
				
	my $shared_dir = $$args{-shared_dir};
	if (!-e $shared_dir){
		die "Unable to find folder: $shared_dir\n";
	}
	$shared_dir = File::Spec->rel2abs( $shared_dir );
	
	#create ref db if it does not exist
    my $db = Bio::Root::IO->catfile($shared_dir, 'Ref_DB'.($$config{'-dbprefix'} ? '.' . File::Util::escape_filename($$config{'-dbprefix'}) : '').'.fasta');

#	my $db = SequenceAnalysis::Utils::inferRefDb($args, $config, $shared_dir);
#	print SequenceAnalysis::Tasks::memoryUsage();

	# Run aligners
	my $bam = Bio::Root::IO->catfile($working_dir, $basename . '.sorted.bam');	
	if (!$$args{-reuseBam} || !-e $bam){
		$bam = SequenceAnalysis::Tasks::alignWrapper(
			-db_file => $db, 
			-db_prefix => File::Util::escape_filename($$config{'-dbprefix'}),
			-input_file => $$args{-input_file},
			-input2_file => $$args{-input2_file},			
			-working_dir => $working_dir,
			-shared_dir => $shared_dir,		
			-aligners => $$args{-aligners},
			-remakeRefDb => $$args{-remakeRefDb},
			-basename => $basename,
			-config => $$config{-align_config} || {},
			-minLength => $$config{-minLength},	
			-recalibrateBam => $$args{-recalibrateBam},	
			-deleteUnsortedBam => 1,
			-deleteIntermediateFiles => $$args{-deleteIntermediateFiles},
		);				
		print SequenceAnalysis::Tasks::memoryUsage();
	}
	else {
		print "Reusing saved BAM file\n";
	}	
				
	if (!$bam || !-e $bam){
		print "Something went wrong with the alignment.  Moving to next file\n";
		return 0;
	}
	
	return {
		-bam => $bam,
		-db => $db,
	};
}


1;