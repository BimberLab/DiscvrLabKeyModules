#
# Copyright (c) 2013 LabKey Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
import sys

def concatenate_file(input_file, output_file):
	fl = input_file.readline() # Discard the first line.
	#print(len(fl.split('\t')))
	for line in input_file:
		output_file.write(line)

debug = len(sys.argv) >= 2 and sys.argv[1] == 'debug'
if (not debug and len(sys.argv) >= 2):
    repository = sys.argv[1]
else:
    repository = ''

output_file = open("alldata.tsv", "wt")
print('adding breast dataset')
input_file = open("breast.tsv", "rt")
output_file.write(input_file.readline())
input_file.seek(0)
concatenate_file(input_file, output_file)
input_file.close();

print('adding ovarian dataset')
input_file = open("ovarian.tsv", "rt")
concatenate_file(input_file, output_file)
input_file.close();

print('adding prostate dataset')
input_file = open("prostate.tsv", "rt")
concatenate_file(input_file, output_file)
input_file.close();

if (debug or repository == 'lung'):
    print('adding lung dataset')
    input_file = open("lung.tsv", "rt")
    concatenate_file(input_file, output_file)
    input_file.close();

if (debug or repository == 'gyn'):
    print('adding gyn dataset')
    input_file = open("gyn.tsv", "rt")
    concatenate_file(input_file, output_file)
    input_file.close();

if (debug or repository == 'sarcoma'):
    print('adding sarcoma dataset')
    input_file = open("sarcoma.tsv", "rt")
    concatenate_file(input_file, output_file)
    input_file.close();

if (debug or repository == 'neuro'):
    print('adding neuro dataset')
    input_file = open("neuro.tsv", "rt")
    concatenate_file(input_file, output_file)
    input_file.close();

if (debug or repository == 'headneck'):
    print('adding headneck dataset')
    input_file = open("headNeck.tsv", "rt")
    concatenate_file(input_file, output_file)
    input_file.close();
