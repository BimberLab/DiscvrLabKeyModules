CREATE TABLE sequenceanalysis.illumina_applications (
  name varchar(200),
  version integer,
  workflowName varchar(200),
  compatibleKits varchar(4000),
  settings varchar(4000),
  workflowParams varchar(4000),
  json text,

  CONSTRAINT PK_illumina_applications PRIMARY KEY (name)
);

DELETE FROM sequenceanalysis.illumina_applications;
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('Assembly', 1, 'Assembly', 'TruSeq LT,Nextera XT,Nextera,TruSeq HT', '{"OptionalGenome":null}', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('ChIP-Seq', 1, 'GenerateFASTQ', 'TruSeq LT', '', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('Clone Checking', 1, 'GenerateFASTQ', 'Nextera XT,Nextera,TruSeq HT,TruSeq LT', '', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('Enrichment', 1, 'GenerateFASTQ', 'Nextera Enrichment', '', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('FASTQ Only', 1, 'GenerateFASTQ', 'TruSeq HT,TruSeq LT,Nextera XT,Nextera,Nextera Enrichment,Small RNA', '', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('Library QC', 1, 'LibraryQC', 'TruSeq HT,TruSeq LT,Nextera XT,Nextera', '{"Genome":null}', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('Metagenomics 16S rRNA', 1, 'Metagenomics', 'Nextera XT,Nextera,TruSeq HT,TruSeq LT', '', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('PCR Amplicon', 1, 'PCR Amplicon', 'Nextera XT,Nextera', '{"NexteraManifest":null}', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('Resequencing', 1, 'Resequencing', 'TruSeq LT,TruSeq HT,Nextera,Nextera XT', '{"Genome":null}', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('RNA-Seq', 1, 'GenerateFASTQ', 'TruSeq LT,TruSeq HT', '', '', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('Small RNA', 1, 'SmallRNA', 'Small RNA', '', '[{"Label":"Genome Folder","Type":"GENOME","LabelInSampleSheet":"GenomeFolder","TrueVal":"-","FalseVal":"-","DefaultVal":"","Required":"FALSE","DisplayAsCol":"TRUE","DisplayEvenIfEmpty":"TRUE"},{"Label":"Contaminants","Type":"STRING","LabelInSampleSheet":"Contaminants","TrueVal":"-","FalseVal":"-","DefaultVal":"","Required":"TRUE","DisplayAsCol":"TRUE","DisplayEvenIfEmpty":"FALSE"},{"Label":"RNA","Type":"STRING","LabelInSampleSheet":"RNA","TrueVal":"-","FalseVal":"-","DefaultVal":"","Required":"TRUE","DisplayAsCol":"TRUE","DisplayEvenIfEmpty":"FALSE"},{"Label":"miRNA","Type":"STRING","LabelInSampleSheet":"miRNA","TrueVal":"-","FalseVal":"-","DefaultVal":"","Required":"TRUE","DisplayAsCol":"TRUE","DisplayEvenIfEmpty":"FALSE"}]', '');
INSERT INTO sequenceanalysis.illumina_applications (name,version,workflowname,compatiblekits,settings,workflowparams,json) VALUES ('TruSeq Amplicon', 1, 'Amplicon', 'TruSeq Amplicon', '{"Genome":null,"Manifest":null,"NoCustomPrimers":null}', '[{"Label":"Use Somatic Variant Caller (Recommended for Cancer Panel)","Type":"BOOL","LabelInSampleSheet":"VariantCaller","TrueVal":"Somatic","FalseVal":"NULL","DefaultVal":"FALSE","Required":"FALSE","DisplayAsCol":"FALSE"}]', '');

CREATE TABLE sequenceanalysis.illumina_genome_folders (
  label varchar(200),
  folder varchar(4000),

  CONSTRAINT PK_illumina_genome_folders PRIMARY KEY (label)
);

CREATE TABLE sequenceanalysis.illumina_param_types (
  param varchar(200),

  CONSTRAINT PK_illumina_param_types PRIMARY KEY (param)
);

CREATE TABLE sequenceanalysis.illumina_sample_kits (
  name varchar(200),
  json text,

  CONSTRAINT PK_illumina_sample_kits PRIMARY KEY (name)
);

DELETE FROM sequenceanalysis.illumina_sample_kits;
INSERT INTO sequenceanalysis.illumina_sample_kits (name,json) VALUES ('Nextera', '{"Settings":[["NexteraManifest"],["Adapter","CTGTCTCTTATACACATCT"],["ManifestExtension","AmpliconManifest"]]}');
INSERT INTO sequenceanalysis.illumina_sample_kits (name,json) VALUES ('Nextera Enrichment', '{"Settings":[["Adapter","CTGTCTCTTATACACATCT"]]}');
INSERT INTO sequenceanalysis.illumina_sample_kits (name,json) VALUES ('Nextera XT', '{"Settings":[["NexteraManifest"],["Adapter","CTGTCTCTTATACACATCT"],["ManifestExtension","AmpliconManifest"]]}');
INSERT INTO sequenceanalysis.illumina_sample_kits (name,json) VALUES ('Small RNA', null);
INSERT INTO sequenceanalysis.illumina_sample_kits (name,json) VALUES ('TruSeq Amplicon', '{"Settings":[["CAT"],["IndexOnly"],["PairedEndOnly"],["ManifestExtension","txt"]]}');
INSERT INTO sequenceanalysis.illumina_sample_kits (name,json) VALUES ('TruSeq HT', '{"Settings":[["Adapter","AGATCGGAAGAGCACACGTC"]]}');
INSERT INTO sequenceanalysis.illumina_sample_kits (name,json) VALUES ('TruSeq LT', '{"Settings":[["Adapter","AGATCGGAAGAGCACACGTC"]]}');

DROP TABLE sequenceanalysis.illumina_templates;