CREATE TABLE [snprc_r24].[lookupSets](
	[RowId] [bigint] IDENTITY(1,1) NOT NULL,
	[SetName] [NVARCHAR](32) NOT NULL,
	[Label] [NVARCHAR](32) NOT NULL,
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

 CONSTRAINT [pk_snprc_r24_lookupsets] PRIMARY KEY (	[RowId] ASC)
 CONSTRAINT [fk_snprc_r24_lookupsets_container] FOREIGN KEY (Container) REFERENCES core.Containers (EntityId) );
GO

CREATE UNIQUE NONCLUSTERED INDEX [idx_snprc_r24_lookupSets_setname] ON [snprc_r24].[lookupSets] ( [SetName] ASC )
GO

ALTER TABLE [snprc_r24].[LookupSets] ADD  DEFAULT (NEWID()) FOR [ObjectId]
GO

CREATE TABLE [snprc_r24].[lookups](
	[RowId] [bigint] IDENTITY(1,1) NOT NULL,
	[SetName] [NVARCHAR](32) NOT NULL,
	[Value] [NVARCHAR](128) NOT NULL,
	[SortOrder] [INTEGER] NULL,
	[DateDisabled] [datetime] NULL,
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

 CONSTRAINT [pk_snprc_r24_lookups] PRIMARY KEY (	[RowId] ASC),
 CONSTRAINT [fk_snprc_r24_lookups_SetName] FOREIGN KEY (SetName) REFERENCES snprc_r24.LookupSets (SetName),
 CONSTRAINT [fk_snprc_r24_lookups_container] FOREIGN KEY (Container) REFERENCES core.Containers (EntityId) );
GO

CREATE UNIQUE NONCLUSTERED INDEX [idx_snprc_r24_lookups_setname] ON [snprc_r24].[lookups] ( [SetName] ASC, [VALUE] ASC)
GO

ALTER TABLE [snprc_r24].[Lookups] ADD  DEFAULT (NEWID()) FOR [ObjectId]
GO