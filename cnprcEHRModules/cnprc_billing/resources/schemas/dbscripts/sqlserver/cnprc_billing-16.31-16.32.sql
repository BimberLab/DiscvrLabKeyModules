/*
 * Copyright (c) 2016 LabKey Corporation
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

CREATE TABLE cnprc_billing.core_service_rate_component (
  rowid INT IDENTITY(1,1) NOT NULL,
  Csrc_pk	      int,
  Csri_fk	      int,
  Csbc_fk	      int,
  Seq	          int,
  Component_qty	float,
  Component_uom	nvarchar (10),
  Cost_units	  float,
  Cost_rate	    float,
  Objectid      nvarchar(100),
  Created       DATETIME,
  CreatedBy     USERID,
  Modified      DATETIME,
  ModifiedBy    USERID,
  Container	    entityId NOT NULL,

  CONSTRAINT PK_CNPRC_CSRC PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_CSRC_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_CSRC_CONTAINER_INDEX ON cnprc_billing.core_service_rate_component (Container);
GO





CREATE TABLE cnprc_billing.core_service_rate_item (
  rowid INT IDENTITY(1,1) NOT NULL,
  Csri_pk	      int,
  Item_code	    NVARCHAR(6),
  Description	  NVARCHAR(40),
  Service_code	NVARCHAR(2),
  Rc_code	      NVARCHAR(2),
  Category    	NVARCHAR(20),
  Uom	          NVARCHAR(10),
  Active_yn	    NVARCHAR(1),
  Item_cost_amt	float,
  Objectid      nvarchar(100),
  Created           DATETIME,
  CreatedBy         USERID,
  Modified          DATETIME,
  ModifiedBy        USERID,
  Container	        entityId NOT NULL,

  CONSTRAINT PK_CNPRC_CSRI PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_CSRI_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_CSRI_CONTAINER_INDEX ON cnprc_billing.core_service_rate_item (Container);
GO

CREATE TABLE cnprc_billing.core_service_line_item (
  rowid INT IDENTITY(1,1) NOT NULL,
  Csli_pk	          int,
  Csbm_fk	          int,
  Service_code	    NVARCHAR(2),
  Rc_code	          NVARCHAR(2),
  Line_no	          int,
  Reference_comment	NVARCHAR(20),
  Item_code	        NVARCHAR(6),
  Description	      NVARCHAR(40),
  Qty	              float,
  Uom	              NVARCHAR(10),
  Item_cost	        float,
  Charge_amt	      float,
  Exempt_amt	      float,
  Objectid          nvarchar(100),
  Created           DATETIME,
  CreatedBy         USERID,
  Modified          DATETIME,
  ModifiedBy        USERID,
  Container	        entityId NOT NULL,

  CONSTRAINT PK_CNPRC_CSLI PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_CSLI_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)

);
GO

CREATE INDEX CNPRC_CSLI_CONTAINER_INDEX ON cnprc_billing.core_service_line_item (Container);
GO

CREATE TABLE cnprc_billing.perdiem_rate_names (
  rowid INT IDENTITY(1,1) NOT NULL,
  Rate_code	              NVARCHAR(1),
  Rate_name	              NVARCHAR(40),
  Rate_name_singular	    NVARCHAR(20),
  Objectid                nvarchar(100),
  Created                 DATETIME,
  CreatedBy               USERID,
  Modified                DATETIME,
  ModifiedBy              USERID,
  Container	              entityId NOT NULL,

  CONSTRAINT PK_CNPRC_PRN PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_PRN_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_PRN_CONTAINER_INDEX ON cnprc_billing.perdiem_rate_names (Container);
GO

CREATE TABLE cnprc_billing.perdiem_rates (
  rowid INT IDENTITY(1,1) NOT NULL,
  Rate_pk	            int,
  Location_rate_class	NVARCHAR(1),
  Component_01	      float,
  Component_02	      float,
  Component_03	      float,
  Component_04	      float,
  Component_05	      float,
  Component_06	      float,
  Component_07	      float,
  Component_08	      float,
  Component_09	      float,
  Component_10	      float,
  Component_11	      float,
  Component_12	      float,
  Component_13	      float,
  Component_14	      float,
  Component_15	      float,
  Component_16	      float,
  Daily_rate	        float,
  Start_date	        DATETIME,
  End_date	          DATETIME,
  Comments            NVARCHAR(200),
  Rate_tier_code_fk	  NVARCHAR(4),
  Objectid            nvarchar(100),
  Created             DATETIME,
  CreatedBy           USERID,
  Modified            DATETIME,
  ModifiedBy          USERID,
  Container	          entityId NOT NULL,

  CONSTRAINT PK_CNPRC_PR PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_PR_CONTAINER FOREIGN KEY (container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_PR_CONTAINER_INDEX ON cnprc_billing.perdiem_rates (Container);
GO
