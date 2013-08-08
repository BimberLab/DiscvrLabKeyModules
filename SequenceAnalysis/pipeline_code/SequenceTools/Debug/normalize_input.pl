#!/usr/bin/perl

use strict;
use warnings;
use Data::Dumper;	
use Cwd 'abs_path';
use File::Basename;
use File::Spec;
use File::Copy 'cp';
use Env;

my $input_files = \@ARGV;

if(!@ARGV || !-e $ARGV[0]){
	print "ERROR: Must supply a valid input file\n";
	die;
}

#print "Environment:\n";
#print Dumper(%ENV);

print "Mock Normalizing: \n";
print join("\n", @$input_files);

my $basename;
if(@$input_files > 1){
    $basename = "Merged";
}
else {
    my @fileparse = fileparse($$input_files[0], qr/\.[^.]*/);
    $basename = $fileparse[0];
}

#spoof the output/normalization process
mkdir('Inputs');
mkdir(File::Spec->catfile('Inputs', $basename));
open(FILE, ">", File::Spec->catfile('Inputs', $basename.'.fastq'));
print FILE "Output";
close FILE;

