/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;
import org.labkey.sequenceanalysis.run.util.CreateSequenceDictionaryWrapper;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * User: bbimber
 * Date: 8/6/12
 * Time: 12:57 PM
 */
public class CreateReferenceLibraryTask extends PipelineJob.Task<CreateReferenceLibraryTask.Factory>
{
    protected CreateReferenceLibraryTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(CreateReferenceLibraryTask.class);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        public List<String> getProtocolActionNames()
        {
            return Arrays.asList("Create Reference Genome");
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            CreateReferenceLibraryTask task = new CreateReferenceLibraryTask(this, job);

            return task;
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        TableInfo libraryTable = QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);

        List<ReferenceLibraryMember> libraryMembers;
        if (getPipelineJob().isCreateNew())
        {
            libraryMembers = getPipelineJob().getLibraryMembers();
        }
        else
        {
            TableInfo libraryMembersTable = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS);
            libraryMembers = new TableSelector(libraryMembersTable, new SimpleFilter(FieldKey.fromString("library_id"), getPipelineJob().getLibraryId()), null).getArrayList(ReferenceLibraryMember.class);
        }

        getJob().getLogger().info("there are " + libraryMembers.size() + " sequences to process");

        //make sure sequence names are unique
        Set<String> names = new CaseInsensitiveHashSet();
        for (ReferenceLibraryMember lm : libraryMembers)
        {
            RefNtSequenceModel m = lm.getSequenceModel();
            if (m == null)
            {
                throw new PipelineJobException("Unable to find reference sequence with rowid: " + lm.getRef_nt_id());
            }

            if (names.contains(lm.getHeaderName()))
            {
                throw new PipelineJobException("All sequence names must be unique.  Duplicate was: " + m.getName());
            }

            names.add(lm.getHeaderName());
        }

        Integer rowId = null;
        File fasta = null;
        File idFile = null;

        try
        {
            //first create the partial library record
            if (getPipelineJob().isCreateNew())
            {
                Map<String, Object> libraryRow = new CaseInsensitiveHashMap();
                libraryRow.put("name", getPipelineJob().getName());
                libraryRow.put("description", getPipelineJob().getLibraryDescription());

                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> inserted = libraryTable.getUpdateService().insertRows(getJob().getUser(), getJob().getContainer(), Arrays.asList(libraryRow), errors, new HashMap<String, Object>());
                if (errors.hasErrors())
                {
                    throw errors;
                }
                libraryRow = new CaseInsensitiveHashMap<>(inserted.get(0));
                rowId = (Integer) libraryRow.get("rowid");
            }
            else
            {
                rowId = getPipelineJob().getLibraryId();
            }
            getPipelineJob().setLibraryId(rowId);

            String basename = rowId + "_" + getPipelineJob().getName().replace(" ", "_");
            File outputDir = new File(getPipelineJob().getOutputDir(), rowId.toString());
            if (!outputDir.exists())
            {
                outputDir.mkdirs();
            }

            fasta = new File(outputDir, basename + ".fasta");
            if (fasta.exists())
            {
                fasta.delete();
            }
            fasta.createNewFile();
            fasta = FileUtil.getAbsoluteCaseSensitiveFile(fasta);

            idFile = new File(outputDir, rowId + "_" + getPipelineJob().getName().replace(" ", "_") + ".idKey.txt");
            if (idFile.exists())
            {
                idFile.delete();
            }
            idFile.createNewFile();
            idFile = FileUtil.getAbsoluteCaseSensitiveFile(idFile);

            //then gather sequences and create the FASTA
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fasta), "UTF-8")); BufferedWriter idWriter = new BufferedWriter(new FileWriter(idFile)))
            {
                idWriter.write("RowId\tName\tAccession\tStart\tStop\n");

                for (ReferenceLibraryMember lm : libraryMembers)
                {
                    RefNtSequenceModel model = lm.getSequenceModel();
                    String name = lm.getHeaderName();

                    getJob().getLogger().info("processing sequence: " + name + " [" + model.getRowid() + "]");

                    writer.write(">" + name + "\n");
                    model.writeSequence(writer, 60, lm.getStart(), lm.getStop());

                    idWriter.write(model.getRowid() + "\t" + model.getName() + "\t" + (model.getGenbank() == null ? "" : model.getGenbank()) + "\t" + (lm.getStart() == null ? "" : lm.getStart()) + "\t" + (lm.getStop() == null ? "" : lm.getStop()) + "\n");

                    model.clearCachedSequence();
                }
            }

            try
            {
                FastaIndexer indexer = new FastaIndexer(getJob().getLogger());
                indexer.execute(fasta);
            }
            catch (PipelineJobException e)
            {
                getJob().getLogger().warn("Unable to create FASTA index");
            }

            try
            {
                CreateSequenceDictionaryWrapper wrapper = new CreateSequenceDictionaryWrapper(getJob().getLogger());
                wrapper.execute(fasta, false);
            }
            catch (PipelineJobException e)
            {
                getJob().getLogger().warn("Unable to create sequence dictionary");
            }

            ExpData d = ExperimentService.get().createData(getJob().getContainer(), new DataType("ReferenceLibrary"));
            d.setName(fasta.getName());
            d.setDataFileURI(fasta.toURI());
            d.save(getJob().getUser());

            //then update the library record
            Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
            toUpdate.put("rowid", rowId);
            toUpdate.put("fasta_file", d.getRowId());
            Map<String, Object> existingKeys = new CaseInsensitiveHashMap<>();
            existingKeys.put("rowid", rowId);
            libraryTable.getUpdateService().updateRows(getJob().getUser(), getJob().getContainer(), Arrays.asList(toUpdate), Arrays.asList(existingKeys), new HashMap<String, Object>());

            //then insert children, only if not already present
            List<Map<String, Object>> toInsert = new ArrayList<>();
            for (ReferenceLibraryMember row : libraryMembers)
            {
                if (row.getRowid() == 0)
                {
                    CaseInsensitiveHashMap childRow = new CaseInsensitiveHashMap();
                    childRow.put("library_id", rowId);
                    childRow.put("ref_nt_id", row.getRef_nt_id());
                    childRow.put("start", row.getStart());
                    childRow.put("stop", row.getStop());
                    toInsert.add(childRow);
                }
            }

            if (!toInsert.isEmpty())
            {
                getJob().getLogger().info("updating database");
                BatchValidationException errors = new BatchValidationException();
                TableInfo libraryMembersTable = QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS);
                libraryMembersTable.getUpdateService().insertRows(getJob().getUser(), getJob().getContainer(), toInsert, errors, new HashMap<String, Object>());
                if (errors.hasErrors())
                {
                    throw errors;
                }
            }

            getJob().getLogger().info("creation complete");

            Set<GenomeTrigger> triggers = SequenceAnalysisServiceImpl.get().getGenomeTriggers();
            if (!triggers.isEmpty())
            {
                for (GenomeTrigger t : triggers)
                {
                    getJob().getLogger().info("running genome trigger: " + t.getName());
                    if (getPipelineJob().isCreateNew())
                    {
                        t.onCreate(getJob().getContainer(), getJob().getUser(), getJob().getLogger(), rowId);
                    }
                    else
                    {
                        t.onRecreate(getJob().getContainer(), getJob().getUser(), getJob().getLogger(), rowId);
                    }
                }
            }
        }
        catch (Exception e)
        {
            if (getPipelineJob().isCreateNew() && rowId != null)
            {
                getJob().getLogger().info("deleting partial DB records");
                Table.delete(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), rowId);
                Table.delete(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS), new SimpleFilter(FieldKey.fromString("library_id"), rowId));
            }

            if (fasta != null && fasta.exists())
                fasta.delete();

            if (idFile != null && idFile.exists())
                idFile.delete();
        }

        return new RecordedActionSet(new RecordedAction("Create Reference Genome"));
    }

    private ReferenceLibraryPipelineJob getPipelineJob()
    {
        return (ReferenceLibraryPipelineJob)getJob();
    }
}
