
CREATE TABLE [snprc_r24].[RowsToDelete](
[ObjectId] [dbo].[EntityId] NOT NULL,
[Modified] [DATETIME] NOT NULL
CONSTRAINT [pk_snprc_r24_RowsToDelete] PRIMARY KEY (	[ObjectId] ASC) );

GO

CREATE TABLE [snprc_r24].[WeightStaging] (
[AnimalId] [NVARCHAR](32) NOT NULL,
[Date] [DATETIME] NOT NULL,
[Weight] [NUMERIC](7,4) NOT NULL,
[ObjectId] [dbo].EntityId NOT NULL,
[Created] [DATETIME] NULL,
[CreatedBy] [dbo].[USERID] NULL,
[Modified] [DATETIME] NULL,
[ModifiedBy] [dbo].[USERID] NULL

CONSTRAINT [pk_snprc_r24_weight_staging] PRIMARY KEY (	[ObjectId] ASC) );

GO