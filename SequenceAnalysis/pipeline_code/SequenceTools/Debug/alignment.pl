#!/usr/bin/perl

use strict;
use warnings;
use Data::Dumper;	
use Cwd 'abs_path';
use File::Basename;
use File::Spec;
use File::Copy 'cp';

my @input_files = @ARGV;

if(!@ARGV || !-e $ARGV[0]){
	print "ERROR: Must supply a valid input file\n";
	die;
}

print "Running Mock Alignment:\n";
print join("\n", @input_files);

my $basename;
my @fileparse = fileparse($input_files[0], qr/\.[^.]*/);
$basename = $fileparse[0];

#spoof the alignment output
mkdir($basename);
mkdir("$basename/Alignment");
open(FILE, ">", $basename."/Alignment/".$basename.".bam");
print FILE "Output";
close FILE;

open(FILE, ">", $basename."/Alignment/".$basename.".bam.bai");
print FILE "Output";
close FILE;


