/************************************************************************
Create schema
Timeline,
TimelineItem,
Schedule,
TimelineAnimalJunction,
TimelinePojectItem


Do not have date bound reference from Timeline to TimelineAnimalJunction
Do not have date bound reference TimelineAnimalJunction to studyDataset_..._assignments


srr 10.30.18

Added qc and revisionNum columns
srr 11.01.2018

refactor keys, qcstate only in timeline.
pulled container from all tables

Changed FK to snd.Projects back to composite.
Dropped ProjectObjectId from Timeline table.
srr 11.07.2018

************************************************************************/



/*==============================================================*/
/* Table: Timeline                                              */
/*==============================================================*/
create table SNPRC_Scheduler.Timeline (
  TimelineId           int                  not null,
  RevisionNum          int                  not null,
  ProjectObjectId      entityId             not null,
  StartDate            date                 null,
  EndDate              date                 null,
  Description          nvarchar(255)        null,
  LeadTech             nvarchar(50)         null,
  Notes                nvarchar(Max)        null,
  SchedulerNotes       nvarchar(500)        null,
  QcState              int                  null,
  Created              datetime             null,
  CreatedBy            int                  null,
  Modified             datetime             null,
  ModifiedBy           int                  null,
  ObjectId             entityId             not null
)
go
execute sp_addextendedproperty 'MS_Description',
                               'Data Dict',
                               'Schema', 'SNPRC_Scheduler', 'table', 'timeline'
go

execute sp_addextendedproperty 'MS_Description',
                               'Added during 9/17/18 meeting. Size TBD',
                               'Schema', 'SNPRC_Scheduler', 'table', 'timeline', 'column', 'Notes'
go

execute sp_addextendedproperty 'MS_Description',
                               'Will handle isActive function',
                               'Schema', 'SNPRC_Scheduler', 'table', 'timeline', 'column', 'qcstate'
go


alter table SNPRC_Scheduler.Timeline
  add constraint PK_Timeline primary key nonclustered (TimelineId, RevisionNum)
go

alter table SNPRC_Scheduler.Timeline
  add constraint AK_TimelineObjectId unique (ObjectId)
go



/*==============================================================*/
/* Index: IDX_TimelineFK1                                       */
/*==============================================================*/
create clustered index IDX_TimelineFK1 on SNPRC_Scheduler.Timeline (
  ProjectObjectId ASC
)
go

alter table SNPRC_Scheduler.Timeline
  add constraint FK_Timeline_SNDProject foreign key (ProjectObjectId)
references snd.projects (Objectid)
go





-- %%  111111111111  %%%%%  end timeline


/*==============================================================*/
/* Table: TimelineItem       2                                   */
/*==============================================================*/
create table SNPRC_Scheduler.TimelineItem (
  TimelineItemId       int                  identity,
  ProjectitemId        int                  not null,
  TimelineObjectId     entityId             not null,
  StudyDay             int                  null,
  ScheduleDay          int                  null,
  Created              datetime             null,
  CreatedBy            int                  null,
  Modified             datetime             null,
  ModifiedBy           int                  null,
  ObjectId             entityId             not null
)

go
alter table SNPRC_Scheduler.TimelineItem
  add constraint PK_TimelineItem primary key nonclustered (TimelineItemId)

go

alter table SNPRC_Scheduler.TimelineItem
  add constraint AK_TimelineItemObjectId unique (ObjectId)
go


/*==============================================================*/
/* Index: IDXC_TimelineItemFK2                                  */
/*==============================================================*/
create clustered index IDXC_TimelineItemFK2 on SNPRC_Scheduler.TimelineItem (
  ProjectItemId ASC
)

/*==============================================================*/
/* Index: IDX_TimelineItemFK1                                   */
/*==============================================================*/
create index IDX_TimelineItemFK1 on SNPRC_Scheduler.TimelineItem (
  TimelineObjectId ASC
)
go

alter table SNPRC_Scheduler.Timelineitem
  add constraint FK_TimelineItem_Timeline foreign key (TimelineObjectId)
references SNPRC_Scheduler.Timeline (ObjectId)
go
alter table SNPRC_Scheduler.Timelineitem
  add constraint FK_TimelineItem_SNDProjectItem foreign key (ProjectItemId)
references snd.ProjectItems (ProjectItemId)
go
-- %% 22222222222 %%  end timelineItem

/*==============================================================*/
/* Table: Schedule  3                                            */
/*==============================================================*/
create table SNPRC_Scheduler.Schedule (
  ScheduleId           int                  identity,
  TimelineItemId       int                  not null,
  TargetDate           datetime             not null,
  Created              datetime             null,
  CreatedBy            int                  null,
  Modified             datetime             null,
  ModifiedBy           int                  null,
  ObjectId             EntityId             not null
)

go

alter table SNPRC_Scheduler.Schedule
  add constraint PK_Schedule primary key (ScheduleId)
go


/*==============================================================*/
/* Index: IDX_ScheduleFK1                                       */
/*==============================================================*/
create index IDX_ScheduleFK1 on SNPRC_Scheduler.Schedule (
  TimelineItemId ASC
)


alter table SNPRC_Scheduler.Schedule
  add constraint FK_ScheduleFK1 foreign key (TimelineItemId)
references SNPRC_Scheduler.TimelineItem (TimelineItemId)
go


-- %%% 333333 %%%  end Schedule

/*==============================================================*/
/* Table: TimelineProjectItem   4                                   */
/*==============================================================*/
create table SNPRC_Scheduler.TimelineProjectItem (
  ProjectItemId        int                  not null,
  TimelineObjectId     entityId             not null,
  TimelineId           int                  not null,
  TimelineRevisionNum  int                  not null,
  TimelineFootNotes    nvarchar(100)        null,
  SortOrder            int                  not null
)
go

alter table SNPRC_Scheduler.TimelineProjectItem
  add constraint PK_TimelineProjectItem primary key (TimelineObjectId, ProjectItemId)
go

/*==============================================================*/
/* Index: IDX_TimelineProjectItemFK1                            */
/*==============================================================*/
create index IDX_TimelineProjectItemFK1 on SNPRC_Scheduler.TimelineProjectItem (
  TimelineObjectid ASC
)
go

/*==============================================================*/
/* Index: IDX_TimelineItemFK2                                   */
/*==============================================================*/
create index IDX_TimelineProjectItemFK2 on SNPRC_Scheduler.TimelineProjectItem (
  ProjectItemId ASC
)
go

alter table SNPRC_Scheduler.TimelineProjectItem
  add constraint FK_TimelineProjectItem_SndProject foreign key (ProjectItemId)
references snd.ProjectItems (ProjectitemId)
go

alter table SNPRC_Scheduler.TimelineProjectItem
  add constraint FK_TimelineProjectItem foreign key (TimelineObjectId)
references SNPRC_Scheduler.Timeline (ObjectId)
go



-- %%%%%%%   4444444 %%%%%%%%%%%% end TimelineProjectItem

/*==============================================================*/
/* Table: timelineanimaljunction  5                              */
/*==============================================================*/
create table SNPRC_Scheduler.TimelineAnimalJunction (
  RowId                int                  identity,
  AnimalId             nvarchar(50)         not null,
  TimelineObjectId     entityId             null,
  StartDate            date                 null,
  StopDate             date                 null,
  Created              datetime             null,
  CreatedBy            int                  null,
  Modified             datetime             null,
  ModifiedBy           int                  null,
  ObjectId             entityId             not null
)

go

alter table SNPRC_Scheduler.TimelineAnimalJunction
  add constraint PK_TimelineAnimalJunction primary key nonclustered (RowId)
go


/*==============================================================*/
/* Index: IDXC_TimelineAnimalJunctionFK1                        */
/*==============================================================*/
create clustered index IDXC_TimelineAnimalJunctionFK1 on SNPRC_Scheduler.TimelineAnimalJunction (
  TimelineObjectId ASC
)
go

/*==============================================================*/
/* Index: IDX_TimelineAnimalJunctionFK2                         */
/*==============================================================*/
create index IDX_TimelineAnimalJunctionFK2 on SNPRC_Scheduler.TimelineAnimalJunction (
  AnimalID ASC
)
go


alter table SNPRC_Scheduler.TimelineAnimalJunction
  add constraint FK_TimelineJunction_Timeline foreign key (TimelineObjectId)
references SNPRC_Scheduler.Timeline (ObjectId)
go