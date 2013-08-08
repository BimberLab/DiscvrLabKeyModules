/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD pvalue_a double precision
ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD pvalue_t double precision
ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD pvalue_g double precision
ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD pvalue_c double precision
ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD pvalue_n double precision
ALTER TABLE sequenceAnalysis.sequence_coverage
  ADD pvalue_del double precision
;

ALTER TABLE sequenceAnalysis.sequence_readsets
  add raw_input_file integer
;