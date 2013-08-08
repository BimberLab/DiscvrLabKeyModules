#!/usr/bin/perl

use strict;
use warnings;
use Data::Dumper;	
use Cwd 'abs_path';
use File::Basename;
use File::Spec;
use File::Copy 'cp';

my @input_files = @ARGV;

if(!@ARGV || !-e $ARGV[0] || !-e $ARGV[1]){
	print "ERROR: Must supply a valid input file\n";
	die;
}

print "Running Mock SNP Caller:\n";
print $input_files[0] . "\n";
print $input_files[1] . "\n";

my @fileparse = fileparse($input_files[0], qr/\.[^.]*/);
my $basename = $fileparse[0];

#spoof the alignment output
mkdir($basename);
mkdir($basename.'/SNPs');

my $xml_file = File::Spec->catfile($basename, $basename.'.seqout.xml');
print "Creating XML: " . $xml_file . "\n";
open(XML, ">", $xml_file);
print XML '<?xml version="1.0" encoding="UTF-8"?>
<sequence xmlns="http://sequence.labkey.org/sequencedata/xml/">
  <readset name="test3" filename="testFile.fastq" barcode5="" barcode3="" platform="LS454" sampleId="" subjectId=""></readset>
  <analysis readset_name="test3" type="Virus">
    <coverage ref_nt_id="1" ref_name="SIVmac239">
      <coverage_position ref_nt_position="2669" ref_nt_insert_index="0" depth="1" adj_depth="1" wt="1" total_a="1" total_t="0" total_g="0" total_c="0" total_n="0" total_del="0" avg_qual_a="40" avg_qual_t="0" avg_qual_g="0" avg_qual_c="0" avg_qual_n="0" avg_qual_del="0"></coverage_position>
      <coverage_position ref_nt_position="3567" ref_nt_insert_index="0" depth="1" adj_depth="1" wt="1" total_a="1" total_t="0" total_g="0" total_c="0" total_n="0" total_del="0" avg_qual_a="23" avg_qual_t="0" avg_qual_g="0" avg_qual_c="0" avg_qual_n="0" avg_qual_del="0"></coverage_position>
    </coverage>
    <alignment ref_nt_id="1" ref_nt_origName="SIVmac239" readname="F4EFUJ010GACQX" orientation="0" q_start="20" q_stop="285" ref_start="4053" ref_stop="4317" cigar="19S72M1D34M1D33M1I58M1I45M1I21M19S" matchqual="222" md_tag="72^A34^A157" num_mismatches="0">
      <nt_snp ref_nt_id="1" ref_nt="A" ref_nt_position="4125" ref_nt_insert_index="0" quality_score="20" avg_qual="0" q_nt="N" q_nt_position="92" q_nt_insert_index="0">
        <aa_snp ref_nt_id="1" ref_aa_id="2" q_aa=":" q_aa_position="548" q_aa_insert_index="0" q_codon="NAA" ref_aa="K" ref_aa_position="548" ref_aa_insert_index="0" avg_qual="0" translation_string="NAA...:" raw_reads="3" adj_reads="1" raw_depth="3" adj_depth="1" raw_percent="30" adj_percent="37.5" nt_positions="4125" min_pvalue="0"></aa_snp>
      </nt_snp>
      <nt_snp ref_nt_id="1" ref_nt="A" ref_nt_position="4160" ref_nt_insert_index="0" quality_score="18" avg_qual="0" q_nt="N" q_nt_position="126" q_nt_insert_index="0">
        <aa_snp ref_nt_id="1" ref_aa_id="2" q_aa="G" q_aa_position="559" q_aa_insert_index="0" q_codon="GGN" ref_aa="G" ref_aa_position="559" ref_aa_insert_index="0" avg_qual="0" translation_string="GGN...G" raw_reads="2" adj_reads="1" raw_depth="2" adj_depth="1" raw_percent="22.2222222222222" adj_percent="25" nt_positions="4160" min_pvalue="0"></aa_snp>
      </nt_snp>
    </alignment>
    <alignment ref_nt_id="1" ref_nt_origName="SIVmac239" readname="F4EFUJ010GIUI7" orientation="0" q_start="20" q_stop="335" ref_start="4562" ref_stop="4876" cigar="19S230M1I85M19S" matchqual="232" md_tag="315" num_mismatches="0"></alignment>
  </analysis>
</sequence>';
close XML;
