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

print "Mock Preprocessing:\n";
print join("\n", @input_files);

my @fileparse = fileparse($input_files[0], qr/\.[^.]*/);
my $basename = $fileparse[0];

#spoof the output/normalization process
mkdir($basename);
mkdir($basename.'/Preprocessing');

#open(FILE, ">", $basename.'/'.$basename.'.fastq');
#print FILE "Output";
#close FILE;

open(FILE, ">", $basename.'/Preprocessing/'.$basename.'.fastq');
print FILE "Output";
close FILE;

open(FILE, ">", $basename.'/Preprocessing/'.$basename.'.adapter.txt');
print FILE "Output";
close FILE;
