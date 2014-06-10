/*
 * Copyright (c) 2013 LabKey Corporation
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

SELECT IntKey2 AS TissueRecordId,
CreatedBy.UserId,
Key2 AS Status,
CASE WHEN NumResponses > 0 THEN TRUE ELSE FALSE END AS HasResponse
FROM (SELECT COUNT(BioTrustAuditEvent.Date) AS NumResponses,
	    BioTrustAuditEvent.IntKey2,
	    BioTrustAuditEvent.Key2,
	    BioTrustAuditEvent.CreatedBy,
	    FROM auditLog.BioTrustAuditEvent
	    WHERE BioTrustAuditEvent.IntKey2 IS NOT NULL
	      AND BioTrustAuditEvent.Key1 LIKE 'Reviewed,%' AND BioTrustAuditEvent.Key2 IS NOT NULL
	    GROUP BY BioTrustAuditEvent.IntKey2, BioTrustAuditEvent.Key2, BioTrustAuditEvent.CreatedBy
     ) AS ResponseByUser
WHERE NumResponses > 0