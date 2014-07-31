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
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

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
            return Arrays.asList("Create Reference Library");
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
        TableInfo refNtTable = QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
        TableInfo libraryTable = QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        TableInfo libraryMembersTable = QueryService.get().getUserSchema(getJob().getUser(), getJob().getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS);

        //make sure sequence names are unique
        List<RefNtSequenceModel> sequences = new TableSelector(refNtTable, new SimpleFilter(FieldKey.fromString("rowid"), getPipelineJob().getSequenceIds(), CompareType.IN), null).getArrayList(RefNtSequenceModel.class);
        Set<String> names = new CaseInsensitiveHashSet();
        for (RefNtSequenceModel m : sequences)
        {
            if (names.contains(m.getName()))
            {
                throw new PipelineJobException("All sequence names must be unique.  Duplicate was: " + m.getName());
            }

            names.add(m.getName());
        }

        Integer rowId = null;
        File fasta = null;
        File idFile = null;

        try
        {
            //first create the partial library record
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
            rowId = (Integer)libraryRow.get("rowid");

            File outputDir = getPipelineJob().getOutputDir();

            fasta = new File(outputDir, rowId + "_" + getPipelineJob().getName() + ".fasta.gz");
            fasta.createNewFile();
            fasta = FileUtil.getAbsoluteCaseSensitiveFile(fasta);

            idFile = new File(outputDir, rowId + "_" + getPipelineJob().getName() + ".idKey.txt");
            idFile.createNewFile();
            idFile = FileUtil.getAbsoluteCaseSensitiveFile(idFile);

            //then gather sequences and create the FASTA
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(fasta)), "UTF-8")); BufferedWriter idWriter = new BufferedWriter(new FileWriter(idFile)))
            {
                idWriter.write("RowId\tName\n");

                for (RefNtSequenceModel model : sequences)
                {
                    getJob().getLogger().info("processing sequence: " + model.getName() + " (" + model.getRowid() + ")");

                    writer.write(">" + model.getName() + "\n");
                    model.writeSequence(writer, 60);

                    idWriter.write(model.getRowid() + "\t" + model.getName() + "\n");

                    model.clearCachedSequence();
                }
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

            //then insert children
            getJob().getLogger().info("updating database");
            List<Map<String, Object>> toInsert = new ArrayList<>();
            for (RefNtSequenceModel row : sequences)
            {
                CaseInsensitiveHashMap childRow = new CaseInsensitiveHashMap();
                childRow.put("library_id", rowId);
                childRow.put("ref_nt_id", row.getRowid());
                toInsert.add(childRow);
            }

            libraryMembersTable.getUpdateService().insertRows(getJob().getUser(), getJob().getContainer(), toInsert, errors, new HashMap<String, Object>());
            if (errors.hasErrors())
            {
                throw errors;
            }

            getJob().getLogger().info("complete");
        }
        catch (Exception e)
        {
            if (rowId != null)
            {
                Table.delete(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), rowId);

                Table.delete(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS), new SimpleFilter(FieldKey.fromString("library_id"), rowId));
            }

            if (fasta != null && fasta.exists())
                fasta.delete();

            if (idFile != null && idFile.exists())
                idFile.delete();
        }

        return new RecordedActionSet(new RecordedAction("Create Reference Library"));
    }

    private ReferenceLibraryPipelineJob getPipelineJob()
    {
        return (ReferenceLibraryPipelineJob)getJob();
    }
}
