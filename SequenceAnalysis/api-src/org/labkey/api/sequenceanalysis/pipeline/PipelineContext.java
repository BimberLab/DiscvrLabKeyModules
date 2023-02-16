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
package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.WorkDirectory;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:21 PM
 */
public interface PipelineContext
{
    Logger getLogger();

    PipelineJob getJob();

    WorkDirectory getWorkDir();

    SequenceAnalysisJobSupport getSequenceSupport();

    /**
     * This is the directory where most of the work should take place, usually the remote pipeline working folder.
     */
    File getWorkingDirectory();

    /**
     * This is the directory where the source files were located and where we expect to deposit the files on completion.
     */
    File getSourceDirectory();

    /**
     * This is the directory where the source files were located.  In the situation where this is a split job, forceParent=true will return the parent job's sourceDirectory.  This can be important if files are written here prior to split.
     */
    File getSourceDirectory(boolean forceParent);
}
