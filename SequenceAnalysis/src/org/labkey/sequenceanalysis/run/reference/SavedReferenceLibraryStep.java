package org.labkey.sequenceanalysis.run.reference;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceLibraryStep;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryTask;
import org.labkey.sequenceanalysis.util.ReferenceLibraryHelperImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 8:32 PM
 */
public class SavedReferenceLibraryStep extends AbstractPipelineStep implements ReferenceLibraryStep
{
    private static final String LIBRARY_ID = "libraryId";

    public SavedReferenceLibraryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SavedReferenceLibraryStep>
    {
        public Provider()
        {
            super("SavedLibrary", "Saved Library", null, "Select this option to reuse a previously saved reference genome", Arrays.asList(
                    ToolParameterDescriptor.create(LIBRARY_ID, "Choose Library", "Select a previously saved reference library from the list.", "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("width", 400);
                            put("schemaName", "sequenceanalysis");
                            put("queryName", "reference_libraries");
                            put("containerPath", "js:Laboratory.Utils.getQueryContainerPath()");
                            put("displayField", "name");
                            put("valueField", "rowid");
                        }}, null),
                    ToolParameterDescriptor.create(null, null, null, "ldk-linkbutton", new JSONObject()
                    {{
                            put("text", "Click here to view saved reference libraries");
                            put("linkCls", "labkey-text-link");
                            put("linkTarget", "_blank");
                            put("isToolParam", false);
                            put("href", "js:LABKEY.ActionURL.buildURL('query', 'executeQuery.view', Laboratory.Utils.getQueryContainerPath(), {schemaName: 'sequenceanalysis', 'query.queryName': 'reference_libraries'})");
                        }}, null)
            ), null, null);
        }

        @Override
        public SavedReferenceLibraryStep create(PipelineContext ctx)
        {
            return new SavedReferenceLibraryStep(this, ctx);
        }
    }

    @Override
    public File getExpectedFastaFile(File outputDirectory) throws PipelineJobException
    {
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            File originalFasta = getOriginalFastaFile();
            //TODO: GZ
            return new File(outputDirectory, originalFasta.getName());
        }
        else
        {
            //when not on the local server, we have to infer the filename
            Integer libraryId = ConvertHelper.convert(getProvider().getParameterByName(LIBRARY_ID).extractValue(getPipelineCtx().getJob(), getProvider()), Integer.class);
            List<File> matches = new ArrayList<>();
            FileType ft = new FileType("fasta");
            for (File f : outputDirectory.listFiles())
            {
                if (ft.isType(f) && f.getName().startsWith(libraryId.toString() + "_"))
                {
                    matches.add(f);
                }
            }

            if (matches.size() > 1)
            {
                throw new PipelineJobException("More than one matching FASTA file found");
            }
            else if (matches.size() == 0)
            {
                throw new PipelineJobException("No matching FASTA files found");
            }

            return matches.get(0);
        }
    }

    private ExpData getLibraryExpData() throws PipelineJobException
    {
        Integer libraryId = ConvertHelper.convert(getProvider().getParameterByName(LIBRARY_ID).extractValue(getPipelineCtx().getJob(), getProvider()), Integer.class);
        if (libraryId == null)
        {
            throw new PipelineJobException("No Library Id Provided");
        }

        Container targetContainer = getPipelineCtx().getJob().getContainer().isWorkbook() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer();
        TableInfo ti = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), targetContainer, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("fasta_file/RowId")));
        TableSelector ts = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null);
        Integer dataId = ts.getObject(Integer.class);
        if (dataId == null)
        {
            throw new PipelineJobException("The selected reference does not have a FASTA file associated with it");
        }

        ExpData data = ExperimentService.get().getExpData(dataId);
        if (data == null || data.getFile() == null || !data.getFile().exists())
        {
            throw new PipelineJobException("Could not find the FASTA file for the selected reference");
        }

        return data;
    }

    private File getOriginalFastaFile() throws PipelineJobException
    {
        return getLibraryExpData().getFile();
    }

    @Override
    public Output createReferenceFasta(File outputDirectory) throws PipelineJobException
    {
        ReferenceLibraryOutputImpl output = new ReferenceLibraryOutputImpl();

        output.addOutput(outputDirectory, "Reference Genome Folder");
        File originalFasta = getOriginalFastaFile();
        File outputFasta = getExpectedFastaFile(outputDirectory);
        if (!outputFasta.exists())
        {
            try
            {
                FileUtils.copyFile(originalFasta, outputFasta);
                assert originalFasta.exists() : "Original FASTA does not exist after copy";
                output.addOutput(outputFasta, ReferenceLibraryTask.REFERENCE_DB_FASTA);
                output.addDeferredDeleteIntermediateFile(outputFasta);

                File originalIndexFile = new File(originalFasta.getPath() + ".fai");
                if (originalIndexFile.exists())
                {
                    File outputIndex = new File(outputFasta.getPath() + ".fai");
                    FileUtils.copyFile(originalIndexFile, outputIndex);
                    assert originalFasta.exists() : "Original FASTA index does not exist after copy";
                    output.addOutput(outputIndex, ReferenceLibraryTask.REFERENCE_DB_FASTA_IDX);
                    output.addDeferredDeleteIntermediateFile(outputIndex);
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        return output;
    }

    @Override
    public void setLibraryId(PipelineJob job, ExpRun run, AnalysisModel model) throws PipelineJobException
    {
        Integer libraryId = ConvertHelper.convert(getProvider().getParameterByName(LIBRARY_ID).extractValue(getPipelineCtx().getJob(), getProvider()), Integer.class);
        model.setLibraryId(libraryId);

        ExpData d = getLibraryExpData();
        if (d != null)
        {
            model.setReferenceLibrary(d.getRowId());
        }
    }
}
