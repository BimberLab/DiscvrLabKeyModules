CREATE SCHEMA extscheduler;
GO

CREATE TABLE extscheduler.Resources
(
  Id INT IDENTITY(1,1) NOT NULL,
  Name VARCHAR(255) NOT NULL,
  Color VARCHAR(20),

  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,

  CONSTRAINT PK_resources PRIMARY KEY (Id),
  CONSTRAINT UQ_resources_ContainerName UNIQUE (Container, Name)
);

CREATE TABLE extscheduler.Events
(
  Id INT IDENTITY(1,1) NOT NULL,
  ResourceId INT NOT NULL,
  Name VARCHAR(255) NOT NULL,
  StartDate DATETIME NOT NULL,
  EndDate DATETIME NOT NULL,
  UserId USERID NOT NULL,

  Container ENTITYID NOT NULL,
  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,

  CONSTRAINT PK_events PRIMARY KEY (Id),
  CONSTRAINT FK_events_ResourceId FOREIGN KEY (ResourceId) REFERENCES extscheduler.resources(Id),
  CONSTRAINT UQ_events_ContainerResourceName UNIQUE (Container, ResourceId, Name)
);