CREATE TABLE [snprc_R24].[Biomarkers](
  [RowId] [bigint] IDENTITY(1,1) NOT NULL,
  [SampleId] [NVARCHAR](32) NOT NULL,
  [Lab] [nvarchar](128) NULL,
  [Analyte] [nvarchar](128) NOT NULL,
  [ObjectId] nvarchar(128),
  [Value] numeric(6,2),
  [Created] [DATETIME] NULL,
  [CreatedBy] [dbo].[USERID] NULL,
  [Modified] [DATETIME] NULL,
  [ModifiedBy] [dbo].[USERID] NULL,
  [DiCreated] [DATETIME] NULL,
  [DiModified] [DATETIME] NULL,
  [DiCreatedBy] [dbo].[USERID] NULL,
  [DiModifiedBy] [dbo].[USERID] NULL,
  Container	entityId NOT NULL


    CONSTRAINT PK_snprc_r24_Biomarkers PRIMARY KEY ([SampleId], [Analyte])
    CONSTRAINT FK_snprc_r24_Biomarkers_container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId) );

go

ALTER TABLE [snprc_r24].[Biomarkers] ADD  DEFAULT (NEWID()) FOR [ObjectId]
GO
