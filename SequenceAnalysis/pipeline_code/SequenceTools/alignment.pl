#!/usr/bin/perl

use strict;
use warnings;
use Data::Dumper;	
 
use SequenceAnalysis::Pipelines::Alignment;

use File::Basename;
use XML::Simple;
use Env;

if(!@ARGV || @ARGV == 0 || !-e $ARGV[0]){
	print "ERROR: Must supply a valid shared folder path: $ARGV[0]\n";
	die;
}

if(!$ARGV[1] || !-e $ARGV[1]){
	print "ERROR: Must supply at least one valid input file: $ARGV[1]\n";
	die;
}

if($ARGV[2] && !-e $ARGV[2]){
	print "ERROR: File not found: $ARGV[2]\n";
	die;
}

my $shared_dir = File::Spec->rel2abs($ARGV[0]);
my $input_file = File::Spec->rel2abs($ARGV[1]);

my $input_file2;
if($ARGV[2]){
	$input_file2 = File::Spec->rel2abs($ARGV[2]);
} 

my $xml = new XML::Simple;	
my $xml_file = $xml->XMLin('sequencePipeline.xml');
my $config = SequenceAnalysis::Tasks::parseXml($xml_file);

print "Performing Alignment: ".$input_file."\n";
if($input_file2){
	print "and $input_file2\n";
}

my $cleanedFiles;
my $working_dir = '';

my $alignConfig;
if ($$config{'aligner'}){
	$alignConfig = {$$config{'aligner'}=>{-fact_params=>{}}};
	foreach(grep(/$$config{'aligner'}/, (keys %$config))){
		my @key = split(/\./, $_);
		$alignConfig->{$$config{'aligner'}}->{-fact_params}->{'-'.$key[1]} = $$config{$_};
	}
};


if($$config{doAlignment}){	
	my @fileparse = fileparse($input_file, qr/\.[^.]*/);
	my $basename = $fileparse[0];
	
	#we assume reference_library.pl was run first
	$$config{'remakeRefDb'} = 0;
		
	if(-s $input_file == 0){
		print "The input file does not appear to have sequences, skipping: $input_file\n";
		die;
	}

	if($input_file2 && -s $input_file2 == 0){
		print "The second input file does not appear to have sequences, skipping: $input_file2\n";
		die;
	}

	my $results;	

	my $params = {
		-working_dir => $working_dir,
		-basename => $basename,
		-input_file => $input_file, 
		-input2_file => $input_file2,
		-shared_dir => $shared_dir,
		-aligners => [$$config{'aligner'}],
		-recalibrateBam => $$config{'recalibrateBam'},
		-reuseBam => $$config{'reuseBam'},
		-reuseSnps => $$config{'reuseSnps'},	
		-remakeRefDb => $$config{'remakeRefDb'},		
		-virusStrain => $$config{'dna.subset'},
		-verbose => $$config{'verbose'},
		-containerId => $$config{'containerId'},
		-containerPath => $$config{'containerPath'},			
		-baseUrl => $$config{'baseUrl'},
		-quality_metrics => $$config{'qualityMetrics'},
		-lkDbImport => $$config{'lkDbImport'}, 	
		-deleteIntermediateFiles => $$config{'deleteIntermediateFiles'},
		-xmlConfig => $config,		
		-config => {
			-minLength => $$config{'preprocessing.minLength'},
			-virusStrain => $$config{'dna.subset'},
			-minQual => $$config{'snp.minQual'},
			-minIndelQual => $$config{'snp.minIndelQual'},
			-minPctQualIndel => $$config{'snp.minIndelPct'},
			-minAvgSnpQual => $$config{'snp.minAvgSnpQual'},
			-minAvgDipQual => $$config{'snp.minAvgDipQual'},
			-useNeighborhoodQual => $$config{'snp.neighborhoodQual'},	
			-align_config=>$alignConfig,			
			-refSequence => $$config{'snp.refSequence'},
			-dnaFilters=>$$config{'dnaFilters'},
			#-dbprefix=>$$config{'dbprefix'},
		}			
	};	
					
	SequenceAnalysis::Pipelines::alignment($params);
}

