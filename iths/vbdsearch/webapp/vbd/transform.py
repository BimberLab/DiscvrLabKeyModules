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
PERSON_ID = 0
PERSON_AGE = 1
PERSON_GENDER = 2
PERSON_RACE = 3
PERSON_ETHNICITY = 4
PERSON_PATH_DIAGNOSIS = 6
PERSON_PRIMARY_SITE = 8
SPECIMEN_SITE = 11
SPECIMEN_TYPE = 12
SPECIMEN_ID = 10
SPECIMEN_PATH_DIAGNOSIS = 14
REPOSITORY_SHORT_NAME = 20
DATA_ORIGIN = 21

import hashlib

def get_person_id(row):
	races = {
		'african american/black': 'b',
		'asian': 'a',
		'caucasian/white': 'c',
		'hawaiian/pacific islander': 'h',
		'multiple': 'm',
		'mixed race': 'm',
		'native american/native alaskan': 'i',
		'unknown':'x',
		'':'o',
		'other':'o',
		'a': 'a',
		'b': 'b',
		'c': 'c',
		'h': 'h',
		'i': 'i',
		'o': 'o',
		'x': 'x'
	}

	ethnicities = {
		'ashkenazi': 'aj',
		'ashkenazi jewish': 'aj',
		'hispanic/latino': 'hl',
		'not hispanic/latino': 'nhl',
		'': 'u',
		'unknown/prefer not to answer': 'u',
		'unknown': 'u',
		'mixed ethnicity': 'm',
		'other': 'o'
	}

	person_diagnoses = {
		'cancer':'c',
		'not cancer': 'nc',
		'benign': 'b',
		'lmp': 'lmp',
		'n/a': 'na',
		'normal': 'n'
	}

	gender = row[PERSON_GENDER].lower().strip()
	race = races[row[PERSON_RACE].lower().strip()]
	ethnicity = ethnicities[row[PERSON_ETHNICITY].lower().strip()]
	if (row[PERSON_PATH_DIAGNOSIS] != ''):
	    person_diagnosis = row[PERSON_PATH_DIAGNOSIS].lower().strip()
	else:
	    person_diagnosis = 'unknown'

	return gender + '_' + race + '_' + ethnicity + '_' + person_diagnosis


def get_specimen_category(row, specimen_out, person_primary_site):
	specimen_diagnosis = row[SPECIMEN_PATH_DIAGNOSIS].lower().strip()
	specimen_type = row[SPECIMEN_TYPE].lower().strip()
	specimen_site = row[SPECIMEN_SITE].lower().strip()

	blood_specimen_types = {'serum', 'plasma', 'buffy coat', 'wbc pellet'}

	if('met' in specimen_type):
		return 'metastatic'

	if('tissue' in specimen_type or specimen_type.startswith('lymph node')):

		specimen_out.write(specimen_diagnosis + '\n')
		
		if( 'benign' in specimen_type or 'not cancer' in specimen_type or
			'benign' in specimen_diagnosis or 'other' in specimen_diagnosis or
			'norm' in specimen_diagnosis or "functional cyst" in specimen_diagnosis or
			'corpus luteum' in specimen_diagnosis or 'necrosis only' in specimen_diagnosis or
			'inflammatory lesion' in specimen_diagnosis or 'not cancer' in specimen_diagnosis or
			'no cancer' in specimen_diagnosis):
			return "tissue non cancer"
		else:
			return 'tissue cancer'


	if('blood' in specimen_type or specimen_type in blood_specimen_types):
		return 'blood'

	return 'other'

def get_person_primary_site(row):
	person_primary_site = row[PERSON_PRIMARY_SITE].lower().strip()
	if (len(row) > REPOSITORY_SHORT_NAME):
	    repository = row[REPOSITORY_SHORT_NAME].lower().strip()
	else:
	    print('repository short name not in row')
	    repository = ''

	if('ovar' in person_primary_site or 'endom' in person_primary_site or 'fallopian' in person_primary_site):
		person_primary_site = 'ovarian'
	elif('breast' in person_primary_site):
		person_primary_site = 'breast'
	elif('gyn' in repository or 'pocrc' in repository):
		person_primary_site = 'ovarian'

	return person_primary_site

def get_person_category(row, person_primary_site):
	person_diagnosis = row[PERSON_PATH_DIAGNOSIS].lower().strip()
	gender = row[PERSON_GENDER].lower().strip()

	if(person_primary_site == 'cns'):
		person_primary_site = 'neuro'

	if(gender == 'f'):
		gender = 'female'
	else:
		gender = 'male'

	if(person_diagnosis == 'cancer'):
		return person_primary_site + ' ' + 'cancer'
	else:
		return 'non-cancer ' + gender

def normalize_ethnicity(row):
	ethnicities = {
		'ashkenazi': 'Ashkenazi',
		'ashkenazi jewish': 'Ashkenazi',
		'hispanic/latino': 'Hispanic/Latino',
		'not hispanic/latino': 'Not Hispanic/Latino',
		'': 'Unknown',
		'unknown/prefer not to answer': 'Unknown',
		'unknown': 'Unknown',
		'mixed ethnicity': 'Mixed Ethnicity',
		'other': 'Other'
	}
	value = row[PERSON_ETHNICITY].lower().strip()
	if (value in ethnicities):
	    return ethnicities[value]
	else:
	    print('Unknown ethnicity ' + value)
	    return 'Unknown'

def normalize_race(row):
	races = {
		'african american/black': 'african american/black',
		'asian': 'asian',
		'caucasian/white': 'caucasian/white',
		'caucasian': 'caucasian/white',
		'hawaiian/pacific islander': 'hawaiian/pacific islander',
		'asian/pac islander': 'hawaiian/pacific islander',
		'multiple': 'multiple',
		'mixed race': 'multiple',
		'native american/native alaskan': 'native american/native alaskan',
		'native american': 'native american/native alaskan',
		'unknown':'unknown',
		'unk':'unknown',
		'':'unknown',
		'other':'other',
		'a': 'asian',
		'b': 'african american/black',
		'aa': 'african american/black',
		'c': 'caucasian/white',
		'h': 'hawaiian/pacific islander',
		'i': 'native american/native alaskan',
		'o': 'other',
		'x': 'unknown'
	}
	value = row[PERSON_RACE].lower().strip()
	if (value in races):
	    return races[value]
	else:
	    print('Unknown race ' + value)
	    return 'unknown'

def normalize_gender(row):
	genders = {
	    'm' : 'm',
	    'f' : 'f',
	    'male' : 'm',
	    'female' : 'f'
	}

	value = row[PERSON_GENDER].lower().strip()
	if (value in genders):
	    return genders[value]
	else:
	    print('Unknown gender ' + value)
	    return 'unknown'

def transform(input_file, output_file, debug):
	print('Transforming...')
	if (debug):
	    print('In Debug mode')
	specimen_out = open('specimen_types.txt', 'wt')
	input_file.readline() #discard first line

	m = hashlib.sha1()

	for line in input_file:
		row = line.split('\t')

#		if (row[PERSON_AGE] != '' and not row[PERSON_AGE].isdigit()):
#		    print('person age not a number ' + row[PERSON_AGE])
		if (row[PERSON_AGE] != '' and not row[PERSON_AGE].isnumeric()):
		    print('person age not a number (' + row[PERSON_AGE], ')')
		    if (not debug):
		        row[PERSON_AGE] = -1
		    else:
		        continue

		if (not debug and row[PERSON_AGE] != '' and int(row[PERSON_AGE]) > 89):
		    print('Person age greater than 89 (' + row[PERSON_AGE] + '), ignoring')
		    continue

		person_id = row[PERSON_ID].strip()
		if(person_id == ''):
			print('Generating a fake person ID')
			person_id = get_person_id(row)

		row[PERSON_ID] = str(abs(hash(person_id)))

		specimen_id = row[SPECIMEN_ID].strip()
		if (specimen_id != ''):
		    m.update(specimen_id.encode('utf-8'))
#		    row[SPECIMEN_ID] = str(abs(hash(specimen_id)))
#		    row[SPECIMEN_ID] = str(m.hexdigest())[:12]
		    row[SPECIMEN_ID] = str(m.hexdigest())

		person_primary_site = get_person_primary_site(row)
		specimen_category = get_specimen_category(row, specimen_out, person_primary_site)
		person_category = get_person_category(row, person_primary_site)
		row[PERSON_RACE] = normalize_race(row)
		row[PERSON_GENDER] = normalize_gender(row)

		if (row[PERSON_PATH_DIAGNOSIS] != ''):
		    row[PERSON_PATH_DIAGNOSIS] = row[PERSON_PATH_DIAGNOSIS].lower().strip()

		row[PERSON_ETHNICITY] = normalize_ethnicity(row)
		row[PERSON_PRIMARY_SITE] = person_primary_site
		output_file.write(specimen_category + '\t' + person_category + '\t' + '\t'.join(row))

	specimen_out.close()


import sys

debug = len(sys.argv) >= 2 and sys.argv[1] == 'debug'

input_file = open("alldata.tsv", "rt")
output_file = open("transformed_data.tsv", "wt")
output_file.write('specimenCategory	personCategory	personId	personAgeAtSpecimenCollection	personGender	personRace	personEthnicity	personPathStage	personPathDiagnosis	personPrimaryHistDiagnosis	personPrimarySite	personPriorCancerHistory	specimenId	specimenSite	specimenType	specimenPreservationMethod	specimenPathDiagnosis	specimenHistDiagnosis	specimenPathGrade	specimenTumorMarkers	specimenPriorTx	specimenNotes	repositoryShortName\n');
transform(input_file, output_file, debug)
input_file.close()
output_file.close()