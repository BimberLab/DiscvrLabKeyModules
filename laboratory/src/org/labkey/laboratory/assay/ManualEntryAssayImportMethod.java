/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.laboratory.assay;

import org.labkey.api.laboratory.assay.DefaultAssayImportMethod;

/**
 * User: bimber
 * Date: 9/17/12
 * Time: 7:01 AM
 */
public class ManualEntryAssayImportMethod extends DefaultAssayImportMethod
{
    public static final String NAME = "Manual Entry";

    public ManualEntryAssayImportMethod(String providerName)
    {
        super(providerName);
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getLabel()
    {
        return NAME;
    }

    @Override
    public String getTooltip()
    {
        return "Choose this option to manually enter the results into the browser, rather than using an excel template.  This is generally only best for assays with a small number of results";
    }

    @Override
    public boolean doEnterResultsInGrid()
    {
        return true;
    }
}

