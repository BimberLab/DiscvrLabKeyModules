#!/usr/bin/perl

=head1 NAME

PROGRAM  : Tasks.pm

=head1 SYNOPSIS


=head1 DESCRIPTION

PURPOSE  : This is a collection of tools for sequence analysis that either wrap command line utilities or process their outputs

=head1 AUTHOR 

Ben Bimber

=cut

package SequenceAnalysis::Tasks;

use strict;
use warnings;
use Data::Dumper;

use Bio::AlignIO;
use Bio::DB::Sam;
use Bio::Root::IO;
use Bio::SeqIO;
use Bio::SeqIO;
use Bio::SimpleAlign;
use Bio::Tools::Run::Bfast;
use Bio::Tools::Run::Blat2;
use Bio::Tools::Run::Bowtie;
use Bio::Tools::Run::BWA2;
use Bio::Tools::Run::Cap3;
use Bio::Tools::Run::CDHit;
use Bio::Tools::Run::FastxToolkit;
use Bio::Tools::Run::Lastz;
use Bio::Tools::Run::Mosaik;
use Bio::Tools::Run::SFFTools;
use Bio::Tools::Run::Usearch;
use Data::Dumper;
use Env;
use File::Basename;
use File::Copy qw(cp mv);
use File::Path qw(make_path remove_tree);
use IO::Handle;
use IPC::Run qw(run);
use List::Util qw[min max sum];
use Math::BigFloat;
use Math::Round;
use POSIX qw(ceil floor);
use Proc::ProcessTable;
use SequenceAnalysis::Conversion;
use SequenceAnalysis::Utils;
use SequenceAnalysis::Version;
#use Storable;
use Time::localtime;
use XML::Writer;

#$ENV{IPCRUNDEBUG} = 'details';

#set the max depth in Bio-Samtools:
Bio::DB::Sam->max_pileup_cnt(2000000);

=head1 run_bowtie()

	my $bowtie = SequenceAnalysis::Tasks::run_bowtie(
		-db_file => $db, 
		-input_file => $input,
		-working_dir => $working_dir,
		-unaligned_file => $basename.'.bowtie.unaligned.fastq',
		-fact_params => {-max_mismatches => 3},
		-output_file => $basename . ".bowtie",
		-index => Bio::Root::IO->catfile($pipeline_dir, 'Shared', 'bowtie.index'),
		-taskName => 'Bowtie',
		);		

=cut

sub run_bowtie {
	my %args = @_;

	#sanity checking
	my @required = (
		'-working_dir', '-input_file', -'unaligned_file', '-output_file',
		'-db_file'
	);
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	my $working_dir = Bio::Root::IO->catfile( $args{'-working_dir'} );

	my $basename = basename( $args{'-input_file'}, qw(.sff) );
	$basename = Bio::Root::IO->catfile( $working_dir, $basename );

	foreach ( grep( /_file$/, ( keys %args ) ) ) {
		if ($args{$_}){
			unless ( File::Spec->file_name_is_absolute( $args{$_} ) ) {
				$args{$_} = Bio::Root::IO->catfile( $working_dir, $args{$_} );
			}
		}
	}

	#create the Bowtie index if not specified or if it does not exist
#	if ( !-e $args{'-index'} . '.1.ebwt' || $args{-remakeRefDb} ) {
#		my %params = ( -db_file => $args{'-db_file'} );
#		if ( $args{'-index'} ) {
#			$params{-out_file} = $args{'-index'};
#		}
#
##TODO
#		$args{'-index'} = SequenceAnalysis::Tasks::bowtie_index(%params);
#	}

	my %fact_params = (
		-command     => 'single',
		-program_dir => $ENV{BOWTIEPATH} || '',

		#-v mode
		#-max_mismatches 	 => 0,

		#-n mode
		-max_seed_mismatches => 3,
		-seed_length         => 20,
		#-max_qual_mismatch   => 300,

		-quiet               => 1,
		-best                => 1,
		-report_n_alignments => 100,
		-offset_base         => 1,
		-want                => 'raw',
		-sam_format          => 1,
		-unaligned_file      => $args{-unaligned_file},
		-no_throw_on_crash   => 1,
	);

	#allow user override of defaults
	foreach ( ( keys %{ $args{-fact_params} } ) ) {
		$fact_params{$_} = ${ $args{-fact_params} }{$_};
	}

	# create factory & set user-specified global blast parameters
	print "Running Bowtie:\n";
	my $bowtiefac = Bio::Tools::Run::Bowtie->new(%fact_params);

	my $run = $bowtiefac->run(
		-ind => $args{-index},
		-seq => $args{-input_file},
		-out => $args{-output_file},
	);

	SequenceAnalysis::Utils::_write_factory_log($bowtiefac);

	if ( !$run ) {
		return 0;
	}

	#convert to BAM
	my $bam = SequenceAnalysis::Conversion::sam2bam(
		-index     => $args{-db_file},
		-basename  => $args{-output_file},
		-deleteSam => 1,
	);

	$bowtiefac->cleanup();

	return $bam;

}

#sub bowtie_index {
#	my %args = @_;
#
#	#sanity checking
#	my @required = ('-db_file');
#	foreach (@required) {
#		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
#	}
#
#	my $db_fac = Bio::Tools::Run::Bowtie->new(
#		-command     => 'build',
#		-program_dir => $ENV{BOWTIEPATH} || '',
#		-fasta       => '1',
#	);
#
#	my %run_params = ( -ref => $args{'-db_file'}, );
#
#	if ( $args{'-out_file'} ) {
#		$run_params{'-ind'} = $args{'-out_file'};
#	}
#
#	print "Creating Bowtie Index\n";
#	my $db = $db_fac->run(%run_params);
#
#	SequenceAnalysis::Utils::_write_factory_log($db_fac);
#
#	return $db;
#
#}

=head1 run_mosaik()


=cut

sub run_mosaik {
	my %args = @_;

	#sanity checking
	my @required =
	  ( '-working_dir', '-input_file', '-output_file', -'db_file', '-index' );
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	my $working_dir = Bio::Root::IO->catfile( $args{'-working_dir'} );

	foreach ( grep( /_file$/, ( keys %args ) ) ) {
		if( $args{$_} && !File::Spec->file_name_is_absolute( $args{$_} ) ) {
			$args{$_} = Bio::Root::IO->catfile( $working_dir, $args{$_} );
			$args{$_} = File::Spec->rel2abs($args{$_});
		}
	}
	
	print "Running Mosaik:\n";

#	#create the Mosaik index if not specified or if it does not exist
#	if ( !-e $args{'-index'} || -s $args{'-index'} == 0 ||$args{-remakeRefDb} ) {
#		my %params = ( -db_file => $args{'-db_file'} );
#		if ( $args{'-index'} ) {
#			$params{-out_file} = $args{'-index'};
#		}
#print Dumper(%params);
#		$args{'-index'} = SequenceAnalysis::Tasks::mosaik_index(%params);
#	}
#
	#convert input FASTQ into mosaik format
	my $out = $args{'-input_file'};
	$out =~ s/\.fastq$/\.dat/i;
	
	my %build_params = (
		-command     => 'MosaikBuild',
		-program_dir => $ENV{MOSAIKPATH} || '',
		-fastq       => $args{'-input_file'},
		-out_file    => $out,
		-technology  => $args{'-sequence_technology'} || '454',
		-quiet		 => 1,
	);
	
	if($args{'-input2_file'}){
		print "\tPerforming Paired End Alignment\n";
		print "\tSecond file: [" . $args{'-input2_file'} . "]\n"; 		
		$build_params{-fastq2} = $args{'-input2_file'}; 
	}
	
	my $mosaik = Bio::Tools::Run::Mosaik->new(%build_params);
	my $run = $mosaik->run();
	
	SequenceAnalysis::Utils::_write_factory_log($mosaik, {-noShowSdtout=>0});
	
	if ( !$run ) {
		print "Error: alignment did not run correctly\n";
		return 0;
	}
	
	$mosaik->cleanup();
		
	my %fact_params = (
		-command           => 'MosaikAligner',
		-program_dir       => $ENV{MOSAIKPATH} || '',
		-in_file           => $out,
		-hash_size         => 32,
		-out_file          => $args{-output_file},
		-index             => $args{-index},
		-banded            => 51,
		-mode              => 'all',
		-no_throw_on_crash => 1,
		-quiet		 	   => 1,
		-pe_neural_network => Bio::Root::IO->catfile($ENV{MOSAIK_NETWORKFILEPATH}, '2.1.26.pe.100.0065.ann'),
		-se_neural_network => Bio::Root::IO->catfile($ENV{MOSAIK_NETWORKFILEPATH}, '2.1.26.se.100.005.ann'),
		-output_multiple   => 1,
		-processors        => $ENV{SEQUENCEANALYSIS_MAX_THREADS} || 1,
	);

	#allow user override of defaults
	foreach ( ( keys %{ $args{-fact_params} } ) ) {
		$fact_params{$_} = ${ $args{-fact_params} }{$_};
	}

	# create factory & set user-specified global blast parameters	
	my $fac = Bio::Tools::Run::Mosaik->new(%fact_params);
	$run = $fac->run();

	my $sdtout = SequenceAnalysis::Utils::_write_factory_log($fac, {-noShowSdtout=>0, -noShowVersion=>1});
	my @lines = split("\n", $$sdtout{-stdout});
	foreach (grep(/- Using /, @lines)){
		print "\t$_\n";
	};
	foreach (grep(/#/, @lines)){
		print "\t$_\n";
	};
		
	$fac->cleanup();

	if ( !$run ) {
		print "Error: alignment did not run correctly\n";
		return 0;
	}

	my $output_name;
	if($fact_params{-output_multiple}){
		$output_name = $args{-output_file} . '.multiple.bam';
	}
	else {
		$output_name = $args{-output_file} . '.bam';
	}

    my $mosaik_stat = $args{-output_file} . '.stat';
    if (-e $mosaik_stat) {
        print "Deleteing mosaik stat file\n";
        unlink $mosaik_stat;
    }

	if (!-e $output_name) {
		print "No BAM produced by mosaik, something went wrong with the alignment.  Either check your data or consider relaxing alignment params.\n";
		print "$output_name\n";
		return 0;
	}
	else {
		print "\tOutput file found: $output_name\n";
		unlink $out;
	}
	
	if(!SequenceAnalysis::Utils::countBamAlignments($output_name)){
		return 0;
	};

	if($fact_params{-output_multiple}){
		my $single_file = $args{-output_file} . '.bam';
		unlink $single_file;
		mv($output_name, $single_file) || die "Unable to move file: $output_name to $single_file. $!\n";
		$output_name = $single_file;
	}

	return $output_name;

}


sub run_bwasw {
	my %args = @_;

	#sanity checking
	my @required = ( '-working_dir', '-input_file', '-output_file' );
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	my $working_dir = Bio::Root::IO->catfile( $args{'-working_dir'} );

	foreach ( grep( /_file$/, ( keys %args ) ) ) {
		if ($args{$_}){
			unless ( File::Spec->file_name_is_absolute( $args{$_} ) ) {
				$args{$_} = Bio::Root::IO->catfile( $working_dir, $args{$_} );
				$args{$_} = File::Spec->rel2abs($args{$_});
			}
		}
	}

	#create the index if not specified or if it does not exist
#	if ( !-e $args{'-index'} . '.bwt' || $args{-remakeRefDb} ) {
#		my %params = ( -db_file => $args{'-db_file'} );
#		if ( $args{'-index'} ) {
#			$params{-out_file} = $args{'-index'};
#		}
#
#		$args{'-index'} = SequenceAnalysis::Tasks::bwa_index(%params);
#	}

	my %fact_params = (
		-command           => 'bwasw',
		-program_dir       => $ENV{BWAPATH} || '',
		-min_seeds_to_skip => 100,
		-no_throw_on_crash => 1,
		-n_threads         => $ENV{SEQUENCEANALYSIS_MAX_THREADS} || 1,
	);

	#allow user override of defaults
	foreach ( ( keys %{ $args{-fact_params} } ) ) {
		$fact_params{$_} = ${ $args{-fact_params} }{$_};
	}
	
	my $input = $args{-input_file};

	if($args{-input2_file}){
		print "\tMerging paired end FASTQ files into single FASTQ for alignment\n";
		$input = SequenceAnalysis::Utils::mergeFASTQ(
			-basename => $args{'-basename'},
			-working_dir => $working_dir,
			-input_files => [$args{-input_file}, $args{-input2_file}]	
		);	
	}

	# create factory & set user-specified global blast parameters
	print "Running BWA-SW:\n";
	my $bwafac = Bio::Tools::Run::BWA2->new(%fact_params);

	my $run = $bwafac->run(
		'-faq' => $input,
		'-fas' => $args{-index},
		'-sam' => $args{-output_file} . '.sam',
	);

	SequenceAnalysis::Utils::_write_factory_log($bwafac);
	
	if ( !$run ) {
		print "Error: alignment did not run correctly\n";
		return 0;
	}
	
	if(!SequenceAnalysis::Utils::countBamAlignments($args{-output_file} . ".sam")){
		return 0;
	};
	
	#remove merged file
	if($args{-input2_file}){
		unlink $input;	
	}
	
	
	#convert to BAM
	my $bam = SequenceAnalysis::Conversion::sam2bam(
		-index     => $args{-db_file},
		-basename  => $args{-output_file},
		-deleteSam => 1,
	);

	return $bam;

}


sub run_bwa {
	my %args = @_;

	#sanity checking
	my @required = ( '-working_dir', '-input_file', '-output_file', '-index' );
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	my $working_dir = Bio::Root::IO->catfile( $args{'-working_dir'} );

	foreach ( grep( /_file$/, ( keys %args ) ) ) {
		if( $args{$_} && !File::Spec->file_name_is_absolute( $args{$_} ) ) {
			$args{$_} = Bio::Root::IO->catfile( $working_dir, $args{$_} );
		}
	}

	#create the index if not specified or if it does not exist
#	if ( !-e $args{'-index'} . '.bwt' || $args{-remakeRefDb} ) {
#		my %params = ( -db_file => $args{'-db_file'} );
#		if ( $args{'-index'} ) {
#			$params{-out_file} = $args{'-index'};
#		}
#
#		$args{'-index'} = SequenceAnalysis::Tasks::bwa_index(%params);
#	}

	my %fact_params = (
		-command           => 'aln',
		-program_dir       => $ENV{BWAPATH} || '',
		-min_seeds_to_skip => 100,
		-n_threads         => $ENV{SEQUENCEANALYSIS_MAX_THREADS} || 1,
	);
	
	#allow user override of defaults
	foreach ( ( keys %{ $args{-fact_params} } ) ) {
		$fact_params{$_} = ${ $args{-fact_params} }{$_};
	}

	print "Running BWA:\n";
	my $bwafac = Bio::Tools::Run::BWA2->new(%fact_params);

	my $sai = $args{-input_file} . '.sai'; 
	my $run = $bwafac->run(
		'-faq' => $args{-input_file},
		'-fas' => $args{-index},
		'-sai' => $sai,
	);
	
	if ( !$run ) {
		print "Error: alignment did not run correctly\n";
		return 0;
	}

	SequenceAnalysis::Utils::_write_factory_log($bwafac);

	my $sai2; 
	if($args{-input2_file}){
		$sai2 = $args{-input2_file} . '.sai';
		print "\tAligning second file:\n";
		my $bwafac2 = Bio::Tools::Run::BWA2->new(%fact_params);
		my $run = $bwafac2->run(
			'-faq' => $args{-input2_file},
			'-fas' => $args{-index},
			'-sai' => $sai2,
		);
			
		SequenceAnalysis::Utils::_write_factory_log($bwafac2);
		if ( !$run ) {
			print "Error: alignment did not run correctly\n";
			return 0;
		}
	}
	
	#Convert to SAM
	print "\tConvert SAI to SAM:\n";
	my $conversionFac;
	$run = 0;
	if(!$args{-input2_file}){
		$conversionFac = Bio::Tools::Run::BWA2->new(
			-command   => 'samse',
			-hit_limit => 100
		  );
		  $run = $conversionFac->run(
			'-faq' => $args{-input_file},
			'-fas' => $args{-index},
			'-sai' => $sai,
			'-sam' => $args{-output_file} . '.sam',
		  );
		  
		  if(-e $args{-output_file} . '.sam'){
		  	unlink $sai;	
		  }
	}
	else {
		$conversionFac = Bio::Tools::Run::BWA2->new(
			-command   => 'sampe',
			-hit_limit => 100
		  );
		  $run = $conversionFac->run(
			'-faq1' => $args{-input_file},
			'-faq2' => $args{-input2_file},
			'-fas' => $args{-index},
			'-sai1' => $sai,
			'-sai2' => $sai2,
			'-sam' => $args{-output_file} . '.sam',
		  );		

		  if(-e $args{-output_file} . '.sam'){
		  	unlink $sai;
		  	unlink $sai2;	
		  }
	}
	
	if ( !$run ) {
		return 0;
	}		
	SequenceAnalysis::Utils::_write_factory_log($conversionFac);

	if(!SequenceAnalysis::Utils::countBamAlignments($args{-output_file} . ".sam")){
		return 0;
	};

	#convert to BAM
	my $bam = SequenceAnalysis::Conversion::sam2bam(
		-index     => $args{-db_file},
		-basename  => $args{-output_file},
		-deleteSam => 1,
	);

	return $bam;
}


=head1 run_lastz()


=cut

sub run_lastz {
	my %args = @_;

	#sanity checking
	my @required = ( '-working_dir', '-input_file', '-output_file', '-db_file');
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	my $working_dir = Bio::Root::IO->catfile( $args{'-working_dir'} );

	foreach ( grep( /_file$/, ( keys %args ) ) ) {
		if ($args{$_}){
			unless ( File::Spec->file_name_is_absolute( $args{$_} ) ) {
				$args{$_} = File::Spec->rel2abs( $args{$_} );
			}
		}
	}
		
	my %fact_params = (
		-command => 'lastz',
		-program_dir => $ENV{LASTZPATH} || '',		
	    -strand => 'both',
		-ambiguous => 'n',
#		-maxwordcount => 1000,
    	-recoverseeds => 1,
#		-mismatch => 10,		
		#-hspthresh => 
		-identity => 98,
		-continuity => 90,
		#-coverage
		#-matchcount => 
		-output => $args{-output_file}.".sam",
		-format	=> 'softsam',
		-no_throw_on_crash   => 1,	
	);

	#allow user override of defaults
	foreach ( ( keys %{ $args{-fact_params} } ) ) {
		$fact_params{$_} = ${ $args{-fact_params} }{$_};
	}

	# create factory & set user-specified global blast parameters
	print "Running LASTZ:\n";
	my $fac = Bio::Tools::Run::Lastz->new(%fact_params);

	my $input = $args{-input_file};
	if($args{-input2_file}){
		print "\tMerging paired end FASTQ files into single FASTQ for alignment\n";
		$input = SequenceAnalysis::Utils::mergeFASTQ(
			-basename => $args{'-basename'},
			-working_dir => $working_dir,
			-input_files => [$args{-input_file}, $args{-input2_file}]	
		);	
	}

	my $fasta_convert = SequenceAnalysis::Tasks::run_fastX(
		-command     => 'fastq_to_fasta',
		-input_file  => $input,
		-output_file => Bio::Root::IO->catfile($args{-working_dir}, $args{-basename} . '.lastz.input.fasta'),
		-working_dir => $args{-working_dir},
		-taskName    => 'FNtoFQ',
	);

	my $run = $fac->run(
		'-query' => $fasta_convert,
		'-target' => $args{-db_file}.'[multiple,nameparse=darkspace]',
	);
	
	SequenceAnalysis::Utils::_write_factory_log($fac);
	
	if ( !$run ) {
		return 0;
	}

	if ( !-e $args{-output_file}.".sam") {
		print "ERROR: No SAM file was created.  Expected to find file: " .$args{-output_file}."\n";
		return 0;
	}

#	if ( !-e $args{-output_file}.".sam" || -s $args{-output_file}.".sam" < 500 ) {
#		print "The SAM file is too small (".(-s $args{-output_file}.".sam").").  There's probably no alignments in it.\n";
#		print "Expected to find file: " . $args{-output_file} . ".sam\n";
#		return 0;
#	}
	if(!SequenceAnalysis::Utils::countBamAlignments($args{-output_file} . ".sam")){
		return 0;
	};
	

	unlink $fasta_convert;
	
	#merge quality scores with BAM
	my $sam = SequenceAnalysis::Utils::addQual2Bam(
		-sam_file => $args{-output_file}.".sam",
		-fastq_file => $input,
	);
	
	if($args{-input2_file}){
		unlink $input; #delete merged file
	}
	
	#convert to BAM
	my $bam = SequenceAnalysis::Conversion::sam2bam(
		-index     => $args{-db_file},
		-basename  => $args{-output_file},
		-deleteSam => 1,
	);
	
	return $bam;

}

sub run_fastX {
	my %args = @_;

	#sanity checking
	my @required =
	  ( '-working_dir', '-input_file', '-command', '-output_file' );
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}

	my $working_dir = $args{'-working_dir'};

	foreach ( grep( /_file$/, ( keys %args ) ) ) {
		if ($args{$_}){
			unless ( File::Spec->file_name_is_absolute( $args{$_} ) ) {
				$args{$_} = File::Spec->rel2abs( $args{$_} );
			}
		}
	}

	# create factory & set user-specified parameters
	my %fact_params = (
		-command     => $args{'-command'},
		-verbose     => 1,
		-asciiOffset => 33,
		-program_dir => $ENV{FASTXPATH} || '',
		-out_file    => $args{'-output_file'},
		-in_file     => $args{'-input_file'},
		-keepNs      => 1,
	);

	#allow user override of defaults
	foreach ( ( keys %{ $args{-fact_params} } ) ) {
		$fact_params{$_} = ${ $args{-fact_params} }{$_};
	}

	my %run_params = ();

	#allow user override of defaults
	foreach ( ( keys %{ $args{-run_params} } ) ) {
		$run_params{$_} = ${ $args{-run_params} }{$_};
	}

	print "Running FASTX Toolkit: $args{'-command'}:\n";
	my $factory = Bio::Tools::Run::FastxToolkit->new(%fact_params);

	my $run = $factory->run(%run_params);

	SequenceAnalysis::Utils::_write_factory_log($factory);

	return $args{'-output_file'};
}

sub _combineAligners {

	my %args = @_;

	#sanity checking
	my @required = (
		'-working_dir', '-input_file', '-aligners', '-db_file',
		'-shared_dir',  '-basename'
	);
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}
	
	if (!@{ $args{-aligners} }){
		die "ERROR: No aligner supplied.  Cannot run alignment\n"; 	
	}
	
	my $input     = $args{'-input_file'};
	my $input2    = $args{'-input2_file'};
	my $unaligned = '';
	my $output = {
		-bams => [],
		-unalignedFile => '',	
	};
	
	foreach ( @{ $args{-aligners} } ) {
		if(-s $input == 0){
			print "Size of input file: $input is zero, skipping.\n";			
			return 0;	
		}
		if($input2 && -s $input2 == 0){
			print "Size of second input file: $input2 is zero, skipping.\n";			
			$input2 = undef;
		}
		
		if ( $_ eq 'bowtie' ) {
			my $config = $args{-config}{bowtie} || {};
			$unaligned = $args{-basename} . '.bowtie.unaligned.fastq';
			$unaligned = File::Spec->rel2abs(Bio::Root::IO->catfile($args{-working_dir}, $unaligned));

			my $bowtie = SequenceAnalysis::Tasks::run_bowtie(
				-db_file        => $args{-db_file},
				-input_file     => $input,
				-input2_file     => $input2,
				-working_dir    => $args{-working_dir},
				-basename    => $args{-basename},
				-unaligned_file => $unaligned,
				-output_file => $args{-basename} . ".bowtie",
				-remakeRefDb => $args{-remakeRefDb},
				-index => Bio::Root::IO->catfile( $args{-shared_dir}, 'Ref_DB.' . ($args{-db_prefix} ? $args{-db_prefix}.'.' : '').'bowtie.index' ),
				-taskName    => 'Bowtie',
				-run_params  => $$config{-run_params} || {},
				-fact_params => $$config{-fact_params} || {},
			);
			#return $output unless $bowtie;
			push( @{$$output{-bams}}, $bowtie ) if $bowtie;
		}
		elsif ( $_ eq 'bwasw' ) {
			my $config = $args{-config}{bwasw} || {};

			$unaligned = '';
			my $bwa = SequenceAnalysis::Tasks::run_bwasw(
				-db_file => $args{'-db_file'},
				-index => Bio::Root::IO->catfile( $args{-shared_dir}, 'Ref_DB.' . ($args{-db_prefix} ? $args{-db_prefix}.'.' : '').'bwa.index' ),
				-input_file  => $input,
				-input2_file => $input2,
				-output_file => $args{-basename} . '.bwasw',
				-basename    => $args{-basename},
				-remakeRefDb => $args{-remakeRefDb},
				-working_dir => $args{-working_dir},
				-taskName    => 'BWA-SW',
				-run_params  => $$config{-run_params} || {},
				-fact_params => $$config{-fact_params} || {},
			);
			#return $output unless $bwa;	
			push( @{$$output{-bams}}, $bwa ) if $bwa;
		}
		elsif ( $_ eq 'bwa' ) {
			my $config = $args{-config}{bwa} || {};

			$unaligned = '';
			my $bwa = SequenceAnalysis::Tasks::run_bwa(
				-db_file => $args{'-db_file'},
				-index => Bio::Root::IO->catfile( $args{-shared_dir}, 'Ref_DB.' . ($args{-db_prefix} ? $args{-db_prefix}.'.' : '').'bwa.index' ),
				-input_file  => $input,
				-input2_file => $input2,
				-output_file => $args{-basename} . '.bwa',
				-remakeRefDb => $args{-remakeRefDb},
				-working_dir => $args{-working_dir},
				-taskName    => 'BWA',
				-run_params  => $$config{-run_params} || {},
				-fact_params => $$config{-fact_params} || {},
			);
			#return $output unless $bwa;
			push( @{$$output{-bams}}, $bwa ) if $bwa;
		}
		elsif ( $_ eq 'lastz' ) {
			my $config = $args{-config}{lastz} || {};
			
			$unaligned = '';
			my $lastz = SequenceAnalysis::Tasks::run_lastz(
				-db_file => $args{'-db_file'},
				-input_file  => $input,
				-input2_file => $input2,
				-output_file => Bio::Root::IO->catfile($args{-working_dir}, $args{-basename}).".lastz",
				-remakeRefDb => $args{-remakeRefDb},
				-basename    => $args{-basename},
				-working_dir => $args{-working_dir},
				-taskName    => 'LASTZ',
				-run_params  => $$config{-run_params} || {},
				-fact_params => $$config{-fact_params} || {},
			);
			#return $output unless $lastz;
						
			push( @{$$output{-bams}}, $lastz ) if $lastz;
		}			
		elsif ( $_ eq 'mosaik' ) {
			my $config = $args{-config}{mosaik} || {};
			$unaligned = '';

			my $mosaik = SequenceAnalysis::Tasks::run_mosaik(
				-db_file => $args{-db_file},
				-index => Bio::Root::IO->catfile( $args{-shared_dir}, 'Ref_DB.' . ($args{-db_prefix} ? $args{-db_prefix}.'.' : '').'mosaik' ),
				-input_file  => $input,
				-input2_file => $input2,
				-output_file => $args{-basename} . '.mosaik',
				-remakeRefDb => $args{-remakeRefDb},
				-basename    => $args{-basename},
				-working_dir => $args{-working_dir},
				-taskName    => 'Mosaik',
				-run_params  => $$config{-run_params} || {},
				-fact_params => $$config{-fact_params} || {},
			);
			#return $output unless $mosaik;
			push( @{$$output{-bams}}, $mosaik ) if $mosaik;

		}
		$input = $unaligned;
		$output->{-unalignedFile} = $input;
		
		last unless $input;
	}
		
	if($unaligned){
		open(FH, '<', $unaligned) || die "Unable to open file: $unaligned";
		my $total = @{[<FH>]} / 4;			
		close FH;
		print "\t$total sequences did not align\n"; 	
	}
	
	return $output;
	
}

sub alignWrapper {
	
	my %args = @_;
	my $bams;
	
	#sanity checking
	my @required = ('-working_dir', '-input_file', '-aligners', '-db_file', '-shared_dir',  '-basename');
	foreach (@required) {
		if ( !$args{$_} ) { die "ERROR: Missing required param: $_" }
	}
			
	my $align = SequenceAnalysis::Tasks::_combineAligners(
		-db_file => $args{-db_file},
		-db_prefix => $args{-db_prefix},  
		-input_file => $args{-input_file},
		-input2_file => $args{-input2_file},
		-working_dir => $args{-working_dir},
		-shared_dir => $args{-shared_dir},		
		-aligners => $args{-aligners},
		-remakeRefDb => $args{-remakeRefDb},
		-basename => $args{-basename},	
		-config => $args{-config} || {},		
	); 
	
	$bams = $align->{-bams};
	
	if(!$bams){
		print "ERROR: No BAM was returned\n";
		return 0;	
	}

	foreach (@$bams){	
		if(!SequenceAnalysis::Utils::countBamAlignments($_)){
			return 0;
		};
	}
	
	# Combine all bams into single file
	my $bam = SequenceAnalysis::Tasks::processBams(
		-bams => $bams,
		-working_dir => $args{-working_dir},
		-basename => $args{-basename},	
		-db_file => $args{-db_file},
		-deleteUnsortedBam => $args{-deleteUnsortedBam},					
		);		
	
	if($args{-recalibrateBam}){		
		$bam = SequenceAnalysis::Tasks::recalibrate_bam(
		    -input_file => $bam,
		    -working_dir => $args{-working_dir},
		    -basename => $args{-basename},
		    -db_file =>	$args{-db_file},
		);		 
	};
	
	return $bam;	
}


sub processBams {
	my %args = @_;
	my $bam;
	print "Combining/Sorting BAM Files:\n";
	
	if ( @{$args{-bams}} > 1 ) {
		$bam = Bio::Root::IO->catfile($args{-working_dir}, $args{-basename} . '.combined.bam');
		my $fact = Bio::Tools::Run::Samtools2->new(
			-command     => 'merge',
			-program_dir => $ENV{SAMTOOLSPATH} || '',
			-no_throw_on_crash   => 1,
			#-headers_in => $headers,
		  );
		  
		my $new_bam = $fact->run(
			-obm => $bam,
			-ibm => $args{-bams},
		);
		  
		SequenceAnalysis::Utils::_write_factory_log($bam);

	}
	else {
		$bam = @{$args{-bams}}[0] || '';
	} 	
	
	if($bam){
		#sort and call MD tags
	 	my $md_bam = Bio::Root::IO->catfile($args{-working_dir}, $args{-basename} . '.md.bam');
	 	$md_bam = File::Spec->rel2abs($md_bam);
	 	my $factory = Bio::Tools::Run::Samtools2->new( 
	 		-command => 'calmd',
	        -program_dir => $ENV{SAMTOOLSPATH} || '',
	        -output_uncompressed => 1,
	        -no_throw_on_crash   => 1,
		); 	
			
		$factory->run(
			-out => $md_bam,
			-fas => $args{-db_file}, 
			-bam => $bam,  
		);	

		SequenceAnalysis::Utils::_write_factory_log($factory);
		
		#delete previous BAM
#		if(-s $md_bam == 0){
#			print "ERROR: size of BAM file is zero: $md_bam\n"; 
#			return;	
#		}
		
		if(!SequenceAnalysis::Utils::countBamAlignments($md_bam)){
			return;
		};
		
		if(-e $md_bam && $args{-deleteUnsortedBam}){
			unlink($bam);
		}	
		
		my $rg_bam = SequenceAnalysis::Utils::addReadGroup2Bam(
			-input_file => $md_bam,
			-working_dir => $args{-working_dir},
			-delete_input => 1,
			-RGPL => 'LS454',
			-RGLB => $args{-basename},
			-RGPU => $args{-basename},
			-RGSM => $args{-basename},					
		);	
		
		if(!$rg_bam || !-e $rg_bam){
			print "BAM does not exist: $rg_bam\n";
			return;
		}	
		
	 	
	 	my $sorted_bam_name = Bio::Root::IO->catfile($args{-working_dir}, $args{-basename}); 	 	
	 	my $sortfactory = Bio::Tools::Run::Samtools2->new( 
	 		-command => 'sort',
	        -program_dir => $ENV{SAMTOOLSPATH} || '',
	        -no_throw_on_crash   => 1,
		);
		
		$sortfactory->run(
			-pfx => $sorted_bam_name, 
			-bam => $rg_bam,  
		);	
		$sorted_bam_name .= '.bam';
		
		SequenceAnalysis::Utils::_write_factory_log($sortfactory, {-noShowVersion=>1});

		if($args{-deleteUnsortedBam}){		
			unlink($rg_bam);
			unlink($rg_bam . '.bai');
		};		
		
		my $idx = $sorted_bam_name . '.bai';
		if(-e $idx){		
			unlink($idx);
		};	
	 	my $ind = Bio::Tools::Run::Samtools2->new( 
	 		-command => 'index',
	        -program_dir => $ENV{SAMTOOLSPATH} || '',
	        -no_throw_on_crash   => 1,       		
		);
				
		$ind->run( -bam => $sorted_bam_name );			
		SequenceAnalysis::Utils::_write_factory_log($ind, {-noShowVersion=>1});
				
		my $count = Bio::Tools::Run::Samtools2->new(
			-command     => 'flagstat',
			-program_dir => $ENV{SAMTOOLSPATH} || '',
			-no_throw_on_crash   => 1,
		);	  
		$count->run(-bam => $sorted_bam_name);	
		
		print "\tBAM summary:\n";
		print "\tNote: some aligners do not include unmapped reads in the BAM file.\n";
		print "\tTherefore the following number may show an artificially high percent mapped.\n";
		print "\tCompare the number mapped against the total input sequences, listed above.\n";		
		
		my $output = $count->stdout();
		$output =~ s/(.*)/\t$1/g if $output;
		print $output if $output;

		return $sorted_bam_name;
	}
	else {
		print "ERROR: BAM not found: $bam\n";
	}
}


sub showVersions {
	print "Installed Software Versions:\n";
	
	print "\tPerl: $]\n";		
	print "\tBioPerl: ".$Bio::Root::Version::VERSION."\n";
	print "\tBio-SamTools: ".$Bio::DB::Sam::VERSION."\n";
    print "\tSequenceAnalysis Pipeline: ".$SequenceAnalysis::Version::VERSION."\n";

	my ($out, $err);
	my $path = $ENV{SEQUENCEANALYSIS_CODELOCATION} || $0;
	run ["svn",  "info", $path], '>', \$out, '2>', \$err;
	my @out = split("\n", $out);
	my $version = join(';', grep( /Rev:/, @out));
	$version =~ s/\D//g;
	print "\tPipeline Subversion Revision: $version\n";
}


sub parseXml {
	my $xml_file = shift;
	 
	my $config = {};
	foreach (@{$$xml_file{note}}){
		my $val = $$_{content};	
		if($val && $val eq 'true'){
			$val = 1;
		}		
		elsif($val && $val eq 'false'){
			$val = 0;
		}		
		$config->{$$_{label}} = $val;	
	}
	
	my @samples = grep(/sample_/, (keys %$config));	
	$$config{-sampleMap} = {};
	foreach(@samples){
		my $sample = JSON->new->utf8->decode( $$config{$_});
		my $basename = $$sample{fileName};		
		if($basename =~ m/\.gz$/){
			$basename =~ s/\.gz$//i;
		}
		
		my @fileparse = fileparse($basename, qr/\.[^.]*/);
		$basename = $fileparse[0];

		my $key = [$basename];
		if($$sample{mid5}){push(@$key, $$sample{mid5})};
		if($$sample{mid3}){push(@$key, $$sample{mid3})};
		#if($$sample{fileName2}){push(@$key, $$sample{fileName2})};
		$key = join("_", @$key);		
		$$config{-sampleMap}->{$key}={};
		$$config{-sampleMap}->{$key}->{-basename} = $basename;
		$$config{-sampleMap}->{$key}->{-origFile} = $$sample{fileName};
		$$config{-sampleMap}->{$key}->{-origFile2} = $$sample{fileName2};
		$$config{-sampleMap}->{$key}->{-mid5} = $$sample{mid5};
		$$config{-sampleMap}->{$key}->{-mid3} = $$sample{mid3};
		$$config{-sampleMap}->{$key}->{-readset} = $$sample{readset};
		$$config{-sampleMap}->{$key}->{-readset_name} = $$sample{readsetname};
		$$config{-sampleMap}->{$key}->{-platform} = $$sample{platform};
		$$config{-sampleMap}->{$key}->{-instrument_run_id} = $$sample{instrument_run_id};
		$$config{-sampleMap}->{$key}->{-subjectId} = $$sample{subjectId};
		$$config{-sampleMap}->{$key}->{-sampleId} = $$sample{sampleId};
		$$config{-sampleMap}->{$key}->{-fileId} = $$sample{fileId};
		$$config{-sampleMap}->{$key}->{-fileId2} = $$sample{fileId2};
	};

	#save to XML
	push(@{$$xml_file{note}}, {label=>'sampleMap', type=>'input', content=>JSON->new->utf8->encode($$config{-sampleMap})});
		
	$$config{-barcodes} = [];
	foreach(grep(/barcode_/, (keys %$config))){
		my $bc = JSON->new->utf8->decode( $$config{$_});;
		my $hash = {
			-name => $$bc[0],
			-sequence => $$bc[1],
			-trim5 => $$bc[2],
			-trim3 => $$bc[3],
		};
		push(@{$$config{-barcodes}}, $hash);
	}
	
	$$config{-adapters} = [];
	if($$config{'preprocessing.trimAdapters'}){	
		foreach (grep(/adapter_/, (keys %$config))){
			my $tmp = JSON->new->utf8->decode( $$config{$_});;
			my $hash = {-name=> $$tmp[0], -sequence=>$$tmp[1]};				
			$$hash{-trim5} = $$tmp[2];
			$$hash{-trim3} = $$tmp[3];
			push(@{$$config{-adapters}}, $hash);
		}
	}
	
	$$config{'dnaFilters'} = [];	
	foreach (grep(/dna\./, (keys %$config))){
		my $val = $$config{$_};		
		if($val && $val ne 'All'){
			my ($junk, $fn) = split(/\./, $_);	
			$val =~ s/,/;/g;
			push(@{$$config{'dnaFilters'}}, [$fn, 'in', $val])	
		}
	};
	
	if($ENV{SEQUENCEANALYSIS_BASEURL}){
		print "Using different baseUrl: " . $ENV{SEQUENCEANALYSIS_BASEURL} . "\n";
		$$config{baseUrl} = $ENV{SEQUENCEANALYSIS_BASEURL};
	}
	return $config;
}

#adapted from:
#http://www.perlmonks.org/?node_id=235757
sub memoryUsage {
	my $t = new Proc::ProcessTable;
	foreach my $got ( @{$t->table} ) {
    	next if not $got->pid eq $$;
    	
    	#print $got->size / 1024 . "\n";
    	return "Memory usage: " . (($got->size) /1024/1024) . "M\n";
	}	
}

1;
