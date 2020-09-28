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

import com.fasterxml.jackson.databind.JavaType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.Readset;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 8/7/2014.
 */
public interface SequenceAnalysisJobSupport extends Serializable
{
    public void cacheExpData(ExpData data);

    public File getCachedData(int dataId);

    public Map<Integer, File> getAllCachedData();

    public Readset getCachedReadset(Integer rowId);

    public AnalysisModel getCachedAnalysis(int rowId);

    public List<Readset> getCachedReadsets();

    public void cacheReadset(int readsetId, User u);

    public void cacheReadset(int readsetId, User u, boolean allowReadsetsWithArchivedData);

    public List<AnalysisModel> getCachedAnalyses();

    public void cacheGenome(ReferenceGenome m);

    public ReferenceGenome getCachedGenome(int genomeId);

    public Collection<ReferenceGenome> getCachedGenomes();

    public void cacheObject(String key, Serializable object);

    public <T> T getCachedObject(String key, Class<T> clazz) throws PipelineJobException;

    /**
     * Allows deserialization of generics.  For example, a cached Map<Integer, Integer> could be accessed using:
     *
     * Map<Integer, Integer> myMap = support.getCachedObject("cachedMap", PipelineJob.createObjectMapper().getTypeFactory().constructParametricType(Map.class, Integer.class, Integer.class))
     *
     */
    public <T> T getCachedObject(String key, JavaType type) throws PipelineJobException;
}
