#!/usr/bin/perl

=head1 NAME

PROGRAM  : Utils.pm

=head1 SYNOPSIS


=head1 DESCRIPTION

# PURPOSE  : 

=head1 AUTHOR 

Ben Bimber

=cut


package SequenceAnalysis::Utils;

use strict;
use warnings;
use Cwd;
use Data::Dumper;

use Bio::Seq::Quality;
use Bio::SeqIO;
use File::Basename;
use File::Copy qw(cp mv);
use Getopt::Std;
use IPC::Run qw(run);
use List::Util qw[min max reduce sum];
use Math::Round;
use POSIX qw(ceil floor);
use String::Approx qw(amatch aindex aslice);


sub mergeFASTQ {
	my %args = @_;

	#sanity checking
	my @required = ( '-input_files', '-basename', '-working_dir');
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}
	
	my $newFile = Bio::Root::IO->catfile( $args{'-working_dir'}, $args{'-basename'}.".fastq" );
	$newFile = File::Spec->rel2abs($newFile);
	
	print "Merging FASTQ Files into Single File\n";
	 
	# Create output object	 
	my $output = Bio::SeqIO->new(
		-file => ">$newFile",
	 	-format => 'fastq',
 	);	 
	
	my $total = 0;
		
	foreach my $file (@{$args{'-input_files'}}){
		my $in_seq_obj = Bio::SeqIO->new(
			-file => $file,
	 		-format => 'fastq',
 		);	 
 		
		while (1){
			my $seq  = $in_seq_obj->next_seq || last;		 	 
			$output->write_seq($seq);
			$total++;	 			 	
		}
	}
	
	print "\t$total Sequences\n";
	
	if($args{'-deleteInputs'}){
		foreach my $file (@{$args{'-input_files'}}){
			print "\tDeleting input: $file\n";
			unlink $file;
		}	
	}
	
	elsif($args{'-compressInputs'}){
		foreach my $file (@{$args{'-input_files'}}){
			my $gzip = SequenceAnalysis::Utils::gzip($file);
			if(-e $gzip){
				print "\tCompressing input: $file\n";
				unlink $file;
			}		
		}			
	}
	return $newFile;	
}

#Quickly calculate hamming distance between two strings
#
#NOTE: Strings must be same length.
#      returns number of different characters.
#see  http://www.perlmonks.org/?node_id=500235
sub mismatch_count($$) { length( $_[ 0 ] ) - ( ( $_[ 0 ] ^ $_[ 1 ] ) =~ tr[\0][\0] ) }


sub _write_factory_log {
	my $fac = shift;
	my $opts = shift || {};
	my $output = {
		-stdout => $fac->stdout(),
		-stderr => $fac->stderr(),
		-command => '',
	};
	
	if($fac->can('last_execution') && !$$opts{-noShowCommand}){
		$$output{-command} = $fac->last_execution();
		print "\t".$fac->last_execution() . "\n" if $fac->last_execution();
	}
		
	if($fac->can('version') && !$$opts{-noShowVersion}){
		print "\tVersion: ".$fac->version ."\n" if $fac->version;		
	}	
	
	unless($$opts{-noShowSdtout}){				
		my $stdout = $fac->stdout();
		$stdout =~ s/(.*)/\t$1/g if $stdout;
		print $stdout if $stdout;
	}		

	unless($$opts{-noShowStderr}){
		my $err = $fac->stderr();
		$err =~ s/(.*)/\t$1/g if $err;
		print $err if $err;
	}	
	
	return $output;
		
}

sub addReadGroup2Bam {	
	my %args = @_;

	#sanity checking
	my @required = ( '-working_dir', '-input_file', '-RGPL', '-RGLB', '-RGPU', -'RGSM');
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	my($out, $err);	

	foreach ( grep( /_file$/, ( keys %args ) ) ) {
		if($args{$_}){
			unless ( File::Spec->file_name_is_absolute( $args{$_} ) ) {
				$args{$_} = File::Spec->rel2abs( $args{$_} );
			}
		}
	}
	
	my $prog_dir = $ENV{PICARDPATH} || '';
	if (!$prog_dir){
        $prog_dir = $ENV{SEQUENCEANALYSIS_TOOLS} || $ENV{PIPELINE_TOOLS_DIR};
	    if ($prog_dir){
	        $prog_dir = Bio::Root::IO->catfile($prog_dir, 'picard-tools');
	    }
	}
	my $jar_location = Bio::Root::IO->catfile($prog_dir, 'AddOrReplaceReadGroups.jar');
	if(!-e $jar_location){
		die("ERROR: Unable to find Picard-tools JAR: ".$jar_location);
	}
	
	my $cmd = ['java', '-jar', $jar_location,
		'I='.$args{'-input_file'},
		'O='.$args{'-input_file'}.'.tmp', 
		'SORT_ORDER=coordinate',
		'CREATE_INDEX=true'
	];
	
	my @params = ( 'RGID', 'RGLB', 'RGPL', 'RGPU', 'RGSM', 'RGCN', 'RGDS');
	foreach (@params) {
		if ( $args{'-'.$_} ) {
			push(@$cmd, $_.'='.$args{'-'.$_});	 
		}
	}

	print "\tWorking directory: " . getcwd . "\n";
	print "\t@$cmd\n";	
	run $cmd, '>', \$out, '2>', \$err;
	$out =~ s/(.*)/\t$1/g if $out;
	print "$out\n";
	
	$err =~ s/(.*)/\t$1/g if $err;
	print $err if $err;	
	
	if(-e $args{'-input_file'}.'.tmp' && -s $args{'-input_file'}.'.tmp' > 10){
		if($args{'-delete_input'}){		
			unlink $args{'-input_file'};
			unlink $args{'-input_file'}.'.bai';
		}
		else {
			mv($args{'-input_file'}, $args{'-input_file'}.'.old');
			mv($args{'-input_file'}.'.bai', $args{'-input_file'}.'.bai.old');
		}
		
		mv($args{'-input_file'}.'.tmp', $args{'-input_file'});
		mv($args{'-input_file'}.'.tmp.bai', $args{'-input_file'}.'.bai');			
	}	
	else {
		print "ERROR: Something went wrong adding read groups to file: ".($args{'-input_file'}.'.tmp')."\n";
		return;
	}

	return $args{'-input_file'};
}


sub addQual2Bam {

	my %args = @_;

	#sanity checking
	my @required = ( '-sam_file', '-fastq_file');
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	print "\tInserting quality scores back into SAM file\n";
	
	open(INFILE, "<", $args{'-sam_file'});
	open(OUTFILE, ">", $args{'-sam_file'}.".tmp");

	my $fastq_map = {};
	
	my $in_seq_obj = Bio::SeqIO->new(
		-file => $args{'-fastq_file'},
	 	-format => 'fastq',
 	);	 
	while (1){
		my $seq_obj  = $in_seq_obj->next_seq || last;
		$$fastq_map{$seq_obj->id} = '';
		foreach my $q (@{$seq_obj->qual}){
			$$fastq_map{$seq_obj->id} .= chr($q + 33);	
		}; 
		
	}
	
	while (<INFILE>){		
		if($_ =~ m/^@/){
			print OUTFILE $_;
		}
		else {
			my @line = split("\t", $_);
			$line[10] = $$fastq_map{$line[0]} || '*';
			if($line[1] == 16){
				$line[10] = reverse($line[10]);
			}			
			print OUTFILE join("\t", @line)."\n";
		}									
	}
	
	close INFILE;
	close OUTFILE;
	
	if(-s $args{'-sam_file'} < -s $args{'-sam_file'}.".tmp"){		
		unlink $args{'-sam_file'};
		mv($args{'-sam_file'}.".tmp", $args{'-sam_file'});
	}
	
	return $args{'-sam_file'};	
}	

sub gzip {
	my $fn = shift;
	#print "Compressing file: " . $fn . "\n";
	system("gzip $fn");# || die "Unable to gzip file: $fn. $!";	
	return $fn . ".gz";
}

sub gunzip {
	my $fn = shift;
	#print "Decompressing file: " . $fn . "\n";
	system("gunzip $fn");# || die "Unable to unzip file: $fn. $!";
	$fn =~ s/\.gz$//i;
	return $fn; 	
}

sub countBamAlignments {
	my $file = shift;
	$file = File::Spec->rel2abs($file);
	
	print "\tCounting alignments in file: $file\n";
	
	if ( !-e $file ) {
		print "ERROR: The file does not exist: $file\n";
		return 0;
	}

	if ( -s $file < 50 ) {
		print "The file is too small (".(-s $file).").  There's probably no alignments in it.\n";
		print "$file\n";
		return 0;
	}

		
 	my %config = ( 
 		-command => 'view',
        -program_dir => $ENV{SAMTOOLSPATH} || '',
    	-print_count => 1,    
    	-no_throw_on_crash   => 1,       		
 	);
 	
 	if($file =~ /\.sam$/){
 		$config{-sam_input} = 1;		
 	}
 	
	my $view = Bio::Tools::Run::Samtools2->new(%config);
		
	my $run = $view->run( -bam => $file );	
	my $count = $view->stdout();
	if(!$count){
		print "ERROR: No sequences found in file\n";
		return 0;
	}
	
	print "\tFound: $count\n";
	return $count;
}

1;
