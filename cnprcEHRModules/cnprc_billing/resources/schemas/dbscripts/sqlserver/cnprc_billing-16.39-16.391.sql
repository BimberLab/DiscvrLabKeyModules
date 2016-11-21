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
CREATE TABLE cnprc_billing.eom_report_log (

  rowid INT IDENTITY(1,1) NOT NULL,
  report_log_sequence int,
  report_log_line_number int,
  procedure_datetime datetime,
  username nvarchar(30),
  procedure_name nvarchar(30),
  period_ending_date datetime,
  report_message_type nvarchar(4),
  report_message_text nvarchar(200),
  objectid nvarchar(100),
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,
  Container	entityId NOT NULL,

  CONSTRAINT PK_CNPRC_BILLING_EOM_REPORT_LOG PRIMARY KEY (rowid),
  CONSTRAINT FK_CNPRC_BILLING_EOM_REPORT_LOG_CONTAINER FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
GO

CREATE INDEX CNPRC_BILLING_EOM_REPORT_LOG_CONTAINER_INDEX ON cnprc_billing.eom_report_log (Container);
GO