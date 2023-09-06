package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.List;

public class RunConga extends AbstractRDiscvrStep
{
    public RunConga(PipelineContext ctx, RunConga.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("RunConga", "Run CoNGA", "Rdiscvr/CoNGA", "This will run CoNGA on the input object(s), and save the results in metadata.", Arrays.asList(
                    SeuratToolParameter.create("organism", "Organism", "The organism to use", "ldk-simplecombo", new JSONObject()
                    {{
                        put("multiSelect", false);
                        put("allowBlank", false);
                        put("storeValues", "human;rhesus");
                        put("delimiter", ";");
                        put("joinReturnValue", true);
                    }}, null, null, false, true),
                    SeuratToolParameter.create("congaMetadataPrefix", "CoNGA Metadata Prefix", "This string will be used as a prefix for the resulting metadata fields. Note: this will always have a period added to the end, so avoid delimiters in this field", "textfield", new JSONObject(){{

                    }}, "conga"),
                    SeuratToolParameter.create("fieldToIterate", "Field to Iterate", "If provided, in addition to running CoNGA for the entire dataset, it will iterate the values of this field, subset the data by this value, and run CoNGA on that subset. The resulting metadata will be saved with the field name pattern: {congaMetadataPrefix}{FieldValue}", "textfield", new JSONObject(){{

                    }}, "SubjectId", "fieldToIterate", true),
                    SeuratToolParameter.create("assayName", "Assay Name", "The name of the assay holding the GEX data", "textfield", new JSONObject(){{

                    }}, "RNA")
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }

        @Override
        public RunConga create(PipelineContext ctx)
        {
            return new RunConga(ctx, this);
        }
    }

    // TODO: can we include the HTML in the actual seurat report?

    @Override
    public String getFileSuffix()
    {
        return "conga";
    }
}
