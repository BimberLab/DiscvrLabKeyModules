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

-- Create schema, tables, indexes, and constraints used for MergeSync module here
-- All SQL VIEW definitions should be created in mergesync-create.sql and dropped in mergesync-drop.sql
CREATE SCHEMA mergesync;
GO

CREATE TABLE mergesync.testnamemapping (
  rowid int identity(1,1),
  servicename varchar(100),
  mergetestname varchar(100)
);

CREATE TABLE mergesync.orderssynced (
  rowid int identity(1,1),
  objectid varchar(100),
  merge_accession varchar(100),
  container entityid,
  created DATETIME,
  createdby int,
  resultsreceived bit
);

INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Albumin', 'ALB');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Amylase', 'AMYL');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Anaerobic Culture', 'ANCULT');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Antibiotic Sensitivity', 'SENSI');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('BAL Fluid Analysis with Cytology', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('BASIC Chemistry Panel in-house', 'BASIC');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Bile Acids Post-prarandial', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Bile Acids Pre-prarandial', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('C-Reactive Protein', 'CRP');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('CBC only, no differential', 'CBC');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('CBC with automated differential', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('CBC with manual differential', 'DIFF');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Chemistry panel with high density lipoprotein send out', 'CMPHDL');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Clotting panel', 'CLOTPL');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Comprehensive Chemistry panel in-house', 'COMP');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Comprehensive Chemistry Panel Send Out', 'CMP');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Compromised SPF Surveillance', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('CSF analysis (WBC count & differential)', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('CSF Glucose', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('CSF Total Protein', 'CSF');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Cytology Send out', 'CYTO');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Dermatophyte test medium culture', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('ESPF Surveillance - Monthly', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('ESPF Surveillance - Quarterly', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Exam for Yeast', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Fecal culture', 'F_CULT');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Fecal parasite exam', 'F_PARA');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Filovirus', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Fungus Culture', 'FUNGAL');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('General culture', 'G-CULT');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Glycosolated hemoglobin  (HGBA1C)', 'HGBA1C');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Gram Stain', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('HDL', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Herpes B', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Herpes B Post-Exposure Baseline Serology', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Herpes B Post-Exposure Baseline Virology', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Herpes B Post-Exposure Follow-Up Serology', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Herpes B Status Confirmation', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('High-density lipoprotein & Low-density lipoprotein (HDL & LDL)', 'HDLLDL');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Intuitive Basic Array', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Intuitive Expanded Array', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Japanese Macaque Surveillance', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('LDL', 'LDL');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Lipase', 'Lipase');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Lipid panel in-house: Cholesterol, Triglyceride, HDL, LDL', 'LIPID');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Malaria Screen', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Miscellaneous in-house', 'MISIN');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Miscellaneous send out', 'MISOUT');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Occult Blood', 'OCCBLD');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Other Clinpath', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Other SPF Surveillance', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL ABSCN-5', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL ABSCN-8', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL PCR', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL SIV WB', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL SRV1 WB', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL SRV2 WB', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL SRV5 WB', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('PDL STLV WB', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Phenobarbital', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Primagam for TB', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Protein Electrophoresis-Total Protein', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('RBC Morphology', 'MORPH');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Reticulocyte count', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Specific Gravity', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('SPF Surveillance - Annual', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('SRV', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('SRV Virus Isolation', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Terminal Serum Surveillance', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Thyroid Panel', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Urinalysis', 'URINE');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Vitamin D (1,25 Dihydroxy)', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Vitamin D (250H)', 'VITD');
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('WBC differential', null);
INSERT INTO mergesync.testnamemapping (servicename, mergetestname) VALUES ('Yersinia culture', 'YERSNA');
