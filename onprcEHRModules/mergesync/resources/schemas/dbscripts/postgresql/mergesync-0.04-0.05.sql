/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE TABLE mergesync.mergetolkmapping (
  mergetestname varchar(100),
  servicename varchar(100),
  automaticresults bool default false,

  CONSTRAINT PK_mergetolkmapping PRIMARY KEY (mergetestname)
);

INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('ALB', 'Albumin', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('AMYL', 'Amylase', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('ANCULT', 'Anaerobic Culture', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('BASIC', 'BASIC Chemistry Panel in-house', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CBC', 'CBC with automated differential', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CBCMIN', 'CBC only, no differential', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CLOTPL', 'Clotting panel', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CMP', 'Comprehensive Chemistry Panel Send Out', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CMPHDL', 'Chemistry panel with high density lipoprotein send out', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('COMP', 'Comprehensive Chemistry panel in-house', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CRP', 'C-Reactive Protein', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFCC', 'CSF analysis (WBC count & differential)', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFPRO', 'CSF Total Protein', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFTP', 'Cerebral Spinal Fluid Total Protein – Send out', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFWBC', 'Cerebral Spinal Fluid white blood count with differential', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CYTO', 'BAL Fluid Analysis with Cytology', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('DIFF', 'CBC with manual differential', false);
--INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('DIFF', 'WBC differential', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('DTMFUN', 'Dermatophyte test medium culture', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('F_CULT', 'Fecal culture', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('F_PARA', 'Fecal parasite exam', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('FUNGAL', 'Fungus Culture', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('GCULT1', 'General culture', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('GCULT2', 'General culture', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('GCULT3', 'General culture', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('HDLLDL', 'High-density lipoprotein & Low-density lipoprotein (HDL & LDL)', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('HGBA1C', 'Glycosolated hemoglobin  (HGBA1C)', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('LDL', 'LDL', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('LIPID', 'Lipid panel in-house: Cholesterol, Triglyceride, HDL, LDL', true);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('MALA', 'Malaria Screen', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('MIS IN', 'Miscellaneous in-house', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('MISOUT', 'Bile Acids Post-prarandial', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('OCCBLD', 'Occult Blood', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('RETIC', 'Reticulocyte count', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('SENS2', 'Antibiotic Sensitivity', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('SENS3', 'Antibiotic Sensitivity', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('SENSI', 'Antibiotic Sensitivity', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('URINE', 'Urinalysis', false);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('YERSNA', 'Yersinia culture', false);
