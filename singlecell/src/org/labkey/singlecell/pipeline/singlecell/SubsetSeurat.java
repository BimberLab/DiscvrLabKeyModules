package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SubsetSeurat extends AbstractCellMembraneStep
{
    public SubsetSeurat(PipelineContext ctx, SubsetSeurat.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SubsetSeurat", "Subset", "CellMembrane/Seurat", "The seurat object will be subset based on the expression below, which is passed directly to Seurat's subset(subset = X).", List.of(
                    ToolParameterDescriptor.create("expression", "Expression", "Filter Expression(s)", "sequenceanalysis-trimmingtextarea", new JSONObject()
                    {{
                        put("allowBlank", false);
                        put("replaceAllWhitespace", false);
                        put("height", 150);
                        put("width", 600);
                        put("delimiter", DELIM);
                    }}, null)
            ), List.of("/sequenceanalysis/field/TrimmingTextArea.js"), null);
        }


        @Override
        public SubsetSeurat create(PipelineContext ctx)
        {
            return new SubsetSeurat(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        Set<String> ret = new HashSet<>();
        ret.add("Seurat");
        ret.addAll(super.getRLibraries());

        return ret;
    }

    final static String EXPRESSION = "<SUBSET_CODE>";
    final static String DELIM = "<>";

    @Override
    protected List<String> loadChunkFromFile() throws PipelineJobException
    {
        ToolParameterDescriptor pd = getProvider().getParameterByName("expression");
        final String val = StringUtils.trimToNull(pd.extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));
        if (val == null)
        {
            throw new PipelineJobException("A blank subset was provided. This should have been caught upstream");
        }

        final String[] values = val.split(DELIM);

        List<String> ret = new ArrayList<>();
        for (String line : super.loadChunkFromFile())
        {
            if (line.contains(EXPRESSION))
            {
                for (String subset : values)
                {
                    String subsetEscaped = subset.replace("'", "\\'");

                    ret.add("\tif (!is.null(seuratObj)) {");
                    ret.add("\tprint(paste0('Subsetting dataset: ', datasetId, ' with the expression: " + subsetEscaped + "'))");
                    ret.add("\t\tcells <- c()");
                    ret.add("\t\ttryCatch({");
                    ret.add("\t\t\tcells <- WhichCells(seuratObj, expression = " + subset + ")");
                    ret.add("\t\t}, error = function(e){");
                    ret.add("\t\t\tif (!is.null(e) && e$message == 'Cannot find cells provided') {");
                    ret.add("\t\t\t\tprint(paste0('There were no cells remaining after the subset: ', '" + subsetEscaped + "'))");
                    ret.add("\t\t\t}");
                    ret.add("\t\t})");
                    ret.add("");
                    ret.add("\t\tif (length(cells) == 0) {");
                    ret.add("\t\t\tprint(paste0('There were no cells after subsetting for dataset: ', datasetId, ', with subset: ', '" + subsetEscaped + "'))");
                    ret.add("\t\t\tseuratObj <- NULL");
                    ret.add("\t\t} else {");
                    ret.add("\t\t\tseuratObj <- subset(seuratObj, cells = cells)");
                    ret.add("\t\t\tprint(paste0('Cells after subset: ', ncol(seuratObj)))");
                    ret.add("\t\t}");
                    ret.add("\t}");
                    ret.add("");
                }
            }
            else
            {
                ret.add(line);
            }
        }

        return ret;
    }

    @Override
    public String getFileSuffix()
    {
        return "subset";
    }
}
