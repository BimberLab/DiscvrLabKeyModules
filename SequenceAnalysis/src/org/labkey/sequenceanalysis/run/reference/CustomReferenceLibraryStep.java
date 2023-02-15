package org.labkey.sequenceanalysis.run.reference;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceLibraryStep;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.pipeline.ReferenceGenomeImpl;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

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

    private File getExpectedFastaFile(File outputDirectory) throws PipelineJobException
    {
        return new File(outputDirectory, "Custom.fasta");
    }

    @Override
    public Output createReferenceFasta(File outputDirectory) throws PipelineJobException
    {
        String name = StringUtils.trimToNull(extractParamValue(CustomReferenceName, String.class));
        String seq = StringUtils.trimToNull(extractParamValue(RefSequence, String.class));
        if (name == null || seq == null)
        {
            throw new PipelineJobException("Must provide both a name and sequence for custom reference sequences, cannot create library");
        }

        File refFasta = getExpectedFastaFile(outputDirectory);
        try (PrintWriter writer = PrintWriters.getPrintWriter(refFasta))
        {
            if (!refFasta.exists())
                refFasta.createNewFile();
            writer.write(">" + name + "\n");
            seq = seq.replaceAll("\\s+", "");

            int len = seq.length();
            int count = 0;
            for (int i = 0; i < len; i++)
            {
                if (count == 60)
                {
                    writer.write('\n');
                    count = 0;
                }

                writer.write(seq.charAt(i));
                count++;
            }

            //always terminate w/ a newline
            writer.write('\n');
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }


        ReferenceLibraryOutputImpl output = new ReferenceLibraryOutputImpl(new ReferenceGenomeImpl(refFasta, null, null, null));
        output.addOutput(refFasta, IndexOutputImpl.REFERENCE_DB_FASTA);
        output.addOutput(outputDirectory, "Reference Genome Folder");

        return output;
    }
}
