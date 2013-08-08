#!/usr/bin/perl

use strict;
use warnings;
use Data::Dumper;	

use SequenceAnalysis::Pipelines::ReferenceLibrary;

use Env; 
use XML::Simple;

 
my $xml = new XML::Simple;	
my $xml_file = $xml->XMLin('sequencePipeline.xml');
my $config = SequenceAnalysis::Tasks::parseXml($xml_file);

print "Creating Reference Library:\n";

my $working_dir = '';

my $alignConfig;
if ($$config{'aligner'}){
	$alignConfig = {$$config{'aligner'}=>{-fact_params=>{}}};
	foreach(grep(/$$config{'aligner'}/, (keys %$config))){
		my @key = split(/\./, $_);
		$alignConfig->{$$config{'aligner'}}->{-fact_params}->{'-'.$key[1]} = $$config{$_};
	} 
};

SequenceAnalysis::Pipelines::create_reference_library({
	-working_dir => $working_dir,
	-aligners => [$$config{'aligner'}],
	-virusStrain => $$config{'dna.subset'},
	-verbose => $$config{'verbose'},
	-containerId => $$config{'containerId'},
	-containerPath => $$config{'containerPath'},			
	-baseUrl => $$config{'baseUrl'},
	-xmlConfig => $config,		
	-config => {
		-virusStrain => $$config{'dna.subset'},
		-align_config=>$alignConfig,			
		-refSequence => $$config{'snp.refSequence'},
		-dnaFilters=>$$config{'dnaFilters'},
	}			
});			
