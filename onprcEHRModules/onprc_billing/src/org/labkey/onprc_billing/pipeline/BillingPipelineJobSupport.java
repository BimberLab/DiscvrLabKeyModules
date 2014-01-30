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
package org.labkey.onprc_billing.pipeline;

import java.io.File;
import java.util.Date;

/**
 * User: bimber
 * Date: 9/14/13
 * Time: 9:27 PM
 */
public interface BillingPipelineJobSupport
{
    public Date getStartDate();

    public Date getEndDate();

    public String getComment();

    public String getName();

    public File getAnalysisDir();
}
