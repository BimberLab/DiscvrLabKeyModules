package org.labkey.sequenceanalysis.run.reference;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceLibraryStep;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryTask;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 8:32 PM
 */
public class VirusReferenceLibraryStep extends DNAReferenceLibraryStep implements ReferenceLibraryStep
{
    private static final String subset = "subset";

    public VirusReferenceLibraryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<VirusReferenceLibraryStep>
    {
        public Provider()
        {
            super("Virus", "Viral Sequences", "Select this option to construct a new library by selecting a viral sequence from the server's DNA DB", Arrays.asList(
                    ToolParameterDescriptor.create(subset, "Virus Strain", null, "ldk-simplelabkeycombo", new JSONObject()
                    {{
                            put("schemaName", "sequenceanalysis");
                            put("queryName", "virus_strains");
                            put("multiSelect", false);
                            put("displayField", "virus_strain");
                            put("valueField", "virus_strain");
                        }}, null),
                    ToolParameterDescriptor.create("category", "Loci", "This library type will always include all categories", "hidden", null, "Virus"),
                    ToolParameterDescriptor.create(null, null, null, "ldk-linkbutton", new JSONObject()
                    {{
                            put("text", "Click here to view reference sequences");
                            put("linkCls", "labkey-text-link");
                            put("linkTarget", "_blank");
                            put("isToolParam", false);
                            put("href", "js:LABKEY.ActionURL.buildURL('query', 'executeQuery', null, {schemaName: 'sequenceanalysis', 'query.queryName': 'virus_strains'})");
                        }}, null)
            ), null, null);
        }

        @Override
        public VirusReferenceLibraryStep create(PipelineContext ctx)
        {
            return new VirusReferenceLibraryStep(this, ctx);
        }
    }

    @Override
    public File getExpectedFastaFile(File outputDirectory) throws PipelineJobException
    {
        String strainName = extractParamValue(subset, String.class);
        return new File(outputDirectory, strainName + ".fasta");
    }
}
