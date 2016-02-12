/*
 * Copyright (c) 2015 LabKey Corporation
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
SELECT
total.id,
--TODO: if this was a string, like 'Y', it would better accept a URL
CASE
  WHEN max(total.hasGE) = 1 THEN true
  ELSE NULL
END as hasGenexData,
CASE
  WHEN max(total.hasMS) = 1 THEN true
  ELSE NULL
END as hasMicrosatData,
CASE
  WHEN max(total.hasPH) = 1 THEN true
  ELSE NULL
END as hasPhenoData,
CASE
  WHEN max(total.hasSNP) = 1 THEN true
  ELSE NULL
END as hasSNPData

FROM(
  SELECT a.id as id, 1 as hasGE, 0 as hasMS, 0 as hasPH, 0 as hasSNP FROM assay.General."Gene Expression".total_assays a
  UNION
  SELECT b.id as id, 0 as hasGE, 1 as hasMS, 0 as hasPH, 0 as hasSNP FROM assay.General.Microsatellites.total_assays b
  UNION
  SELECT c.id as id, 0 as hasGE, 0 as hasMS, 1 as hasPH, 0 as hasSNP FROM assay.General.Phenotypes.total_assays c
  UNION
  SELECT d.id as id, 0 as hasGE, 0 as hasMS, 0 as hasPH, 1 as hasSNP FROM assay.General.SNPs.total_assays d
  )as total GROUP BY id
