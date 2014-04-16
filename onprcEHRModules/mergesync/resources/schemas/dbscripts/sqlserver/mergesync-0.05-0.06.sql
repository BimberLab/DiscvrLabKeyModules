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

TRUNCATE TABLE mergesync.testnamemapping;
ALTER TABLE mergesync.testnamemapping DROP COLUMN rowid;
ALTER TABLE mergesync.testnamemapping ALTER COLUMN servicename varchar(100) NOT NULL;
GO
ALTER TABLE mergesync.testnamemapping ADD CONSTRAINT PK_testnamemapping PRIMARY KEY (servicename);
GO
ALTER TABLE mergesync.testnamemapping ADD automaticresults bit default 0;
GO

DROP TABLE mergesync.mergetolkmapping;
GO
CREATE TABLE mergesync.mergetolkmapping (
  mergetestname varchar(100) NOT NULL,
  servicename varchar(100),

  CONSTRAINT PK_mergetolkmapping PRIMARY KEY (mergetestname)
);

INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Albumin', 'ALB', 1);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Amylase', 'AMYL', 1);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Anaerobic Culture', 'ANCULT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('BASIC Chemistry Panel in-house', 'BASIC', 1);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('CBC with automated differential', 'CBC', 1);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Clotting panel', 'CLOTPL', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Comprehensive Chemistry Panel Send Out', 'CMP', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Chemistry panel with high density lipoprotein send out', 'CMPHDL', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Comprehensive Chemistry panel in-house', 'COMP', 1);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('C-Reactive Protein', 'CRP', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('CSF analysis (WBC count & differential)', 'CSFCC', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('CSF Total Protein', 'CSFPRO', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Cerebral Spinal Fluid Total Protein – Send out', 'CSFTP', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Cerebral Spinal Fluid white blood count with differential', 'CSFWBC', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('BAL Fluid Analysis with Cytology', 'CYTO', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Cytology Send out', 'CYTO', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('CBC with manual differential', 'DIFF', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('WBC differential', 'DIFF', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Dermatophyte test medium culture', 'DTMFUN', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Fecal culture', 'F_CULT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Fecal parasite exam', 'F_PARA', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Fungus Culture', 'FUNGAL', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('General culture', 'GCULT1', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('High-density lipoprotein & Low-density lipoprotein (HDL & LDL)', 'HDLLDL', 1);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Glycosolated hemoglobin  (HGBA1C)', 'HGBA1C', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('LDL', 'LDL', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Lipid panel in-house: Cholesterol, Triglyceride, HDL, LDL', 'LIPID', 1);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Malaria Screen', 'MALA', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Miscellaneous in-house', 'MIS IN', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Bile Acids Post-prarandial', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Bile Acids Pre-prarandial', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Calcium, ionized', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Calcium, total', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('CSF Glucose', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Lipase', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Miscellaneous send out', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Phenobarbital', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Protein Electrophoresis-Total Protein', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Thyroid Panel', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Vitamin D (1,25 Dihydroxy)', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Vitamin D (250H)', 'MISOUT', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('RBC Morphology', 'MORPH', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Occult Blood', 'OCCBLD', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Reticulocyte count', 'RETIC', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Antibiotic Sensitivity', 'SENSI', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Urinalysis', 'URINE', 0);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname, automaticresults) VALUES ('Yersinia culture', 'YERSNA', 0);

TRUNCATE TABLE mergesync.mergetolkmapping;
TRUNCATE TABLE mergesync.mergetolkmapping;
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('ALB', 'Albumin');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('AMYL', 'Amylase');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('ANCULT', 'Anaerobic Culture');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('BASIC', 'BASIC Chemistry Panel in-house');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CBC', 'CBC with automated differential');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CLOTPL', 'Clotting panel');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CMP', 'Comprehensive Chemistry Panel Send Out');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CMPHDL', 'Chemistry panel with high density lipoprotein send out');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('COMP', 'Comprehensive Chemistry panel in-house');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CRP', 'C-Reactive Protein');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CSFCC', 'CSF analysis (WBC count & differential)');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CSFPRO', 'CSF Total Protein');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CSFTP', 'Cerebral Spinal Fluid Total Protein – Send out');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CSFWBC', 'Cerebral Spinal Fluid white blood count with differential');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CYTO', 'BAL Fluid Analysis with Cytology');
--INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('CYTO', 'Cytology Send out');
--INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('DIFF', 'CBC with manual differential');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('DIFF', 'WBC differential');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('DTMFUN', 'Dermatophyte test medium culture');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('F_CULT', 'Fecal culture');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('F_PARA', 'Fecal parasite exam');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('FUNGAL', 'Fungus Culture');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('GCULT1', 'General culture');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('GCULT2', 'General culture');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('GCULT3', 'General culture');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('HDLLDL', 'High-density lipoprotein & Low-density lipoprotein (HDL & LDL)');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('HGBA1C', 'Glycosolated hemoglobin  (HGBA1C)');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('LDL', 'LDL');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('LIPID', 'Lipid panel in-house: Cholesterol, Triglyceride, HDL, LDL');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('MALA', 'Malaria Screen');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('MIS IN', 'Miscellaneous in-house');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('MISOUT', 'Miscellaneous send out');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('MORPH', 'RBC Morphology');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('OCCBLD', 'Occult Blood');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('RETIC', 'Reticulocyte count');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('SENS2', 'Antibiotic Sensitivity');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('SENS3', 'Antibiotic Sensitivity');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('SENSI', 'Antibiotic Sensitivity');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('URINE', 'Urinalysis');
INSERT INTO mergesync.mergetolkmapping (mergetestname, servicename) VALUES ('YERSNA', 'Yersinia culture');
