package org.labkey.sequenceanalysis.run.reference;

import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceLibraryStep;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.pipeline.ReferenceGenomeImpl;

import java.io.File;
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
            super("SavedLibrary", "Saved Genome", null, "Select this option to reuse a previously saved reference genome", Arrays.asList(
                    ToolParameterDescriptor.create(LIBRARY_ID, "Choose Genome", "Select a previously saved reference genome from the list.", "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("width", 400);
                            put("schemaName", "sequenceanalysis");
                            put("queryName", "reference_libraries");
                            put("containerPath", "js:Laboratory.Utils.getQueryContainerPath()");
                            put("filterArray", "js:[LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)]");
                            put("displayField", "name");
                            put("valueField", "rowid");
                            put("allowBlank", false);
                        }}, null),
                    ToolParameterDescriptor.create(null, null, null, "ldk-linkbutton", new JSONObject()
                    {{
                            put("text", "Click here to view saved references");
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

    private File getExpectedFastaFile(File outputDirectory) throws PipelineJobException
    {
        if (PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            File originalFasta = getOriginalFastaFile();

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
        assert PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer : "This method can only be run on the webserver";

        Integer libraryId = ConvertHelper.convert(getProvider().getParameterByName(LIBRARY_ID).extractValue(getPipelineCtx().getJob(), getProvider()), Integer.class);
        if (libraryId == null)
        {
            throw new PipelineJobException("No Library Id Provided");
        }

        Container targetContainer = getPipelineCtx().getJob().getContainer().isWorkbook() ? getPipelineCtx().getJob().getContainer().getParent() : getPipelineCtx().getJob().getContainer();
        TableInfo ti = QueryService.get().getUserSchema(getPipelineCtx().getJob().getUser(), targetContainer, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, PageFlowUtil.set(FieldKey.fromString("fasta_file")));
        TableSelector ts = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null);
        Integer dataId = ts.getObject(Integer.class);
        if (dataId == null)
        {
            throw new PipelineJobException("The selected reference does not have a FASTA file associated with it");
        }

        ExpData data = ExperimentService.get().getExpData(dataId);
        if (data == null || data.getFile() == null)
        {
            throw new PipelineJobException("Could not find the FASTA file for the selected reference, expected");
        }

        if (!data.getFile().exists())
        {
            throw new PipelineJobException("Could not find the FASTA file for the selected reference, expected: " + data.getFile().getPath());
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
        File originalFasta = getOriginalFastaFile();
        ReferenceLibraryOutputImpl output = new ReferenceLibraryOutputImpl(new ReferenceGenomeImpl(originalFasta, getLibraryExpData(), getLibraryId()));
        output.addOutput(outputDirectory, "Reference Genome Folder");
        output.addInput(originalFasta, IndexOutputImpl.REFERENCE_DB_FASTA);

        return output;
    }

    public Integer getLibraryId() throws PipelineJobException
    {
        return ConvertHelper.convert(getProvider().getParameterByName(LIBRARY_ID).extractValue(getPipelineCtx().getJob(), getProvider()), Integer.class);
    }
}
