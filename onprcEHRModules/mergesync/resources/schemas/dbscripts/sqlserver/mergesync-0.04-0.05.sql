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
  automaticresults bit default 0,

  CONSTRAINT PK_mergetolkmapping PRIMARY KEY (mergetestname)
);


INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('ALB', 'Albumin', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('AMYL', 'Amylase', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('ANCULT', 'Anaerobic Culture', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('BASIC', 'BASIC Chemistry Panel in-house', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CBC', 'CBC with automated differential', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CBCMIN', 'CBC only, no differential', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CLOTPL', 'Clotting panel', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CMP', 'Comprehensive Chemistry Panel Send Out', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CMPHDL', 'Chemistry panel with high density lipoprotein send out', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('COMP', 'Comprehensive Chemistry panel in-house', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CRP', 'C-Reactive Protein', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFCC', 'CSF analysis (WBC count & differential)', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFPRO', 'CSF Total Protein', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFTP', 'Cerebral Spinal Fluid Total Protein – Send out', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CSFWBC', 'Cerebral Spinal Fluid white blood count with differential', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('CYTO', 'BAL Fluid Analysis with Cytology', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('DIFF', 'CBC with manual differential', 0);
--INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('DIFF', 'WBC differential', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('DTMFUN', 'Dermatophyte test medium culture', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('F_CULT', 'Fecal culture', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('F_PARA', 'Fecal parasite exam', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('FUNGAL', 'Fungus Culture', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('GCULT1', 'General culture', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('GCULT2', 'General culture', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('GCULT3', 'General culture', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('HDLLDL', 'High-density lipoprotein & Low-density lipoprotein (HDL & LDL)', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('HGBA1C', 'Glycosolated hemoglobin  (HGBA1C)', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('LDL', 'LDL', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('LIPID', 'Lipid panel in-house: Cholesterol, Triglyceride, HDL, LDL', 1);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('MALA', 'Malaria Screen', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('MIS IN', 'Miscellaneous in-house', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('MISOUT', 'Bile Acids Post-prarandial', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('OCCBLD', 'Occult Blood', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('RETIC', 'Reticulocyte count', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('SENS2', 'Antibiotic Sensitivity', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('SENS3', 'Antibiotic Sensitivity', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('SENSI', 'Antibiotic Sensitivity', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('URINE', 'Urinalysis', 0);
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename, automaticresults) VALUES ('YERSNA', 'Yersinia culture', 0);
