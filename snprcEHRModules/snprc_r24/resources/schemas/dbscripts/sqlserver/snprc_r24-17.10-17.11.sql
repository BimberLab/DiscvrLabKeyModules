CREATE TABLE [snprc_r24].[SampleInventory](
	[RowId] [bigint] IDENTITY(1,1) NOT NULL,
	[AnimalId] [NVARCHAR](32) NOT NULL,
	[Date] [DATETIME] NOT NULL,
	[SampleId] [NVARCHAR](32) NOT NULL,
	[Aim] [NVARCHAR](128) NULL,
	[SampleType] [NVARCHAR](128) NOT NULL,
	[ObjectId] nvarchar(128) NULL,
	[Created] [DATETIME] NULL,
	[CreatedBy] [dbo].[USERID] NULL,
	[Modified] [DATETIME] NULL,
	[ModifiedBy] [dbo].[USERID] NULL,
	[DiCreated] [DATETIME] NULL,
	[DiModified] [DATETIME] NULL,
	[DiCreatedBy] [dbo].[USERID] NULL,
	[DiModifiedBy] [dbo].[USERID] NULL,
	Container	entityId NOT NULL

 CONSTRAINT [pk_snprc_r24_sampleinventory] PRIMARY KEY (	[SampleId] ASC)
 CONSTRAINT [fk_snprc_r24_sampleinventory_container] FOREIGN KEY (Container) REFERENCES core.Containers (EntityId) );

GO

ALTER TABLE [snprc_r24].[SampleInventory] ADD  DEFAULT (NEWID()) FOR [ObjectId]
GO