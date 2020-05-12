/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis;


import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 12:53 PM
 */
abstract public class SequenceAnalysisService
{
    static SequenceAnalysisService _instance;

    public static SequenceAnalysisService get()
    {
        return _instance;
    }

    static public void setInstance(SequenceAnalysisService instance)
    {
        _instance = instance;
    }

    abstract public ReferenceLibraryHelper getLibraryHelper(File refFasta);

    abstract public void registerGenomeTrigger(GenomeTrigger trigger);

    abstract public void registerFileHandler(SequenceOutputHandler<SequenceOutputHandler.SequenceOutputProcessor> handler);

    abstract public void registerReadsetHandler(SequenceOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor> handler);

    //abstract public File createTabixIndex(File input, @Nullable Logger log) throws PipelineJobException;

    abstract public void registerDataProvider(SequenceDataProvider p);

    abstract public List<NavItem> getNavItems(Container c, User u, SequenceDataProvider.SequenceNavItemCategory category);

    abstract public ReadData getReadData(int rowId, User u);

    abstract public Readset getReadset(int readsetId, User u);

    abstract public ReferenceGenome getReferenceGenome(int rowId, User u) throws PipelineJobException;

    abstract public File ensureVcfIndex(File vcf, Logger log) throws IOException;

    abstract public File ensureVcfIndex(File vcf, Logger log, boolean forceRecreate) throws IOException;

    abstract public File bgzipFile(File input, Logger log) throws PipelineJobException;

    abstract public void ensureFastaIndex(File fasta, Logger log) throws PipelineJobException;

    abstract public String getUnzippedBaseName(String filename);

    abstract public Integer getExpRunIdForJob(PipelineJob job, boolean throwUnlessFound) throws PipelineJobException;

    abstract public List<PedigreeRecord> generatePedigree(Collection<String> sampleNames, Container c, User u);

    abstract public String getVCFLineCount(File vcf, Logger log, boolean passOnly) throws PipelineJobException;

    abstract public File writeAllCellHashingBarcodes(File webserverDir, User u, Container c) throws PipelineJobException;

    abstract public String createReferenceLibrary(List<Integer> sequenceIds, Container c, User u, String name, String assemblyId, String description, boolean skipCacheIndexes, boolean skipTriggers) throws IOException;

    abstract public String createReferenceLibrary(List<Integer> sequenceIds, Container c, User u, String name, String assemblyId, String description, boolean skipCacheIndexes, boolean skipTriggers, Set<GenomeTrigger> extraTriggers) throws IOException;

    abstract public File combineVcfs(List<File> files, File outputGz, ReferenceGenome genome, Logger log, boolean multiThreaded, @Nullable Integer compressionLevel) throws PipelineJobException;

    abstract public String getScriptPath(String moduleName, String path) throws PipelineJobException;
}
