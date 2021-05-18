package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
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
            super("SubsetSeurat", "Subset", "CellMembrane/Seurat", "The seurat object will be subset based on the expression below, which is passed directly to Seurat's subset(subset = X).", Arrays.asList(
                    ToolParameterDescriptor.create("expression", "Expression", "Filter Expression(s)", "sequenceanalysis-trimmingtextarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", DELIM);
                    }}, null)
            ), Arrays.asList("/sequenceanalysis/field/TrimmingTextArea.js"), null);
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

    final static String EXPRESSION = "<SUBSETS>";
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
                    String toSub = "seuratObj <- subset(seuratObj, subset = " + subset + ")";
                    ret.add(line.replaceAll(EXPRESSION, toSub));
                    ret.add(line.replaceAll(EXPRESSION, "print(paste0('Cells after subset: ', ncol(seuratObj)))"));
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
