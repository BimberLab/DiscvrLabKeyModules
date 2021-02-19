package org.labkey.singlecell.pipeline.singlecell;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
                    SeuratToolParameter.create("expression", "Expression", "Filter Expression", "textarea", new JSONObject(){{
                        put("allowBlank", false);
                        put("height", 150);
                        put("delimiter", ",");
                    }}, null)
            ), null, null);
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

    final static String EXPRESSION = "<EXPRESSION>";

    @Override
    protected List<String> loadChunkFromFile() throws PipelineJobException
    {
        ToolParameterDescriptor pd = getProvider().getParameterByName("expression");
        final String val = StringUtils.trimToNull(pd.extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx()));

        return super.loadChunkFromFile().stream().map(x -> {
            if (x.contains(EXPRESSION))
            {
                x = x.replaceAll(EXPRESSION, val);
            }

            return x;
        }).collect(Collectors.toList());
    }

    @Override
    public String getFileSuffix()
    {
        return "subset";
    }
}
