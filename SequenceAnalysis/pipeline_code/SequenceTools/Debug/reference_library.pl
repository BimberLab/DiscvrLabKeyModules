#!/usr/bin/perl

use strict;
use warnings;
use Data::Dumper;	
use Cwd 'abs_path';
use File::Basename;
use File::Spec;
use File::Copy 'cp';

print "Mock Creating Reference Library:\n";

#spoof a Ref DB
mkdir('Shared');
open(FILE, ">", 'Shared/Ref_DB.fasta');
print FILE "Output";
close FILE;

open(FILE, ">", 'Shared/Ref_DB.fe');
print FILE "Output";
close FILE;
