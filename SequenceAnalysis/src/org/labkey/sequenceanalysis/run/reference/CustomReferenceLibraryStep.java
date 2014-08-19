package org.labkey.sequenceanalysis.run.reference;

import org.json.JSONObject;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.AbstractPipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.PipelineContext;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.ReferenceLibraryStep;
import org.labkey.sequenceanalysis.api.run.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 8:32 PM
 */
public class CustomReferenceLibraryStep extends AbstractPipelineStep implements ReferenceLibraryStep
{
    private static final String CustomReferenceName = "customReferenceName";
    private static final String RefSequence = "refSequence";

    public CustomReferenceLibraryStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<CustomReferenceLibraryStep>
    {
        public Provider()
        {
            super("Custom", "Custom Library", null, "Note: if selected, AA translations will not be calculated. The name of this reference should not match one of the other reference sequences.  You will also be unable to import into the DB", Arrays.asList(
                    ToolParameterDescriptor.create(CustomReferenceName, "Reference Name", null, "textfield", new JSONObject()
                    {{
                            put("allowBlank", false);
                            put("width", 450);
                        }}, null),
                    ToolParameterDescriptor.create(RefSequence, "Sequence", "Paste the sequence only (ie. no FASTA header, title, etc)", "textarea", new JSONObject()
                    {{
                            put("allowBlank", false);
                            put("width", 450);
                            put("height", 250);
                            put("maskRe", "js:new RegExp('[ATGCN]', 'i')");
                        }}, null)
            ), null, null);
        }

        @Override
        public CustomReferenceLibraryStep create(PipelineContext ctx)
        {
            return new CustomReferenceLibraryStep(this, ctx);
        }
    }

    @Override
    public File getExpectedFastaFile(File outputDirectory) throws PipelineJobException
    {
        return new File(outputDirectory, "Custom.fasta");
    }

    @Override
    public Output createReferenceFasta(File outputDirectory) throws PipelineJobException
    {
        ReferenceLibraryOutputImpl output = new ReferenceLibraryOutputImpl();

        String name = StringUtils.trimToNull(extractParamValue(CustomReferenceName, String.class));
        String seq = StringUtils.trimToNull(extractParamValue(RefSequence, String.class));
        if (name == null || seq == null)
            throw new PipelineJobException("Must provide both a name and sequence for custom reference sequences, cannot create library");

        File refFasta = getExpectedFastaFile(outputDirectory);
        try (FileWriter writer = new FileWriter(refFasta))
        {
            if (!refFasta.exists())
                refFasta.createNewFile();
            writer.write(">" + name + System.getProperty("line.separator"));
            writer.write(seq);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
        output.addOutput(refFasta, ReferenceLibraryTask.REFERENCE_DB_FASTA);
        output.addOutput(outputDirectory, "Reference Genome Folder");

        return output;
    }

    public void setLibraryId(PipelineJob job, ExpRun run, AnalysisModel model)
    {
        List<? extends ExpData> datas = run.getInputDatas(ReferenceLibraryTask.REFERENCE_DB_FASTA, null);
        if (datas.size() > 0)
        {
            for (ExpData d : datas)
            {
                if (d.getFile() == null)
                {
                    job.getLogger().debug("No file found for ExpData: " + d.getRowId());
                }
                else if (d.getFile().exists())
                {
                    model.setReferenceLibrary(d.getRowId());
                    break;
                }
                else
                {
                    job.getLogger().debug("File does not exist: " + d.getFile().getPath());
                }
            }
        }
    }
}
