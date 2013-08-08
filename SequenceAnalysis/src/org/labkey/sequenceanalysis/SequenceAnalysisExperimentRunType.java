/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.sequenceanalysis;

import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.query.ExpSchema;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 11/16/11
 * Time: 8:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class SequenceAnalysisExperimentRunType extends ExperimentRunType
{
    public SequenceAnalysisExperimentRunType()
    {
        super(SequenceAnalysisModule.PROTOCOL, ExpSchema.SCHEMA_NAME, ExpSchema.TableType.Runs.toString());
    }

//    @Override
//    public void populateButtonBar(ViewContext context, ButtonBar bar, DataView view, ContainerFilter containerFilter)
//    {
//    }

    public Priority getPriority(ExpProtocol protocol)
    {
        return Priority.LOW;
    }
}