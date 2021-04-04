package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViralSnpUtil
{
    public static int resolveGene(int refNtId, String aaName) throws PipelineJobException
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("ref_nt_id"), refNtId);
        filter.addCondition(FieldKey.fromString("name"), aaName);

        TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES), PageFlowUtil.set("rowid"), filter, null);
        if (!ts.exists())
        {
            throw new PipelineJobException("Unable to find AA: " + aaName);
        }

        return ts.getObject(Integer.class);
    }

    public static Map<Integer, List<VariantContext>> readVcfToMap(File vcf)
    {
        Map<Integer, List<VariantContext>> consensusMap = new HashMap<>();
        try (VCFFileReader reader = new VCFFileReader(vcf); CloseableIterator<VariantContext> it = reader.iterator())
        {
            while (it.hasNext())
            {
                VariantContext vc = it.next();

                if (vc.isFiltered())
                {
                    continue;
                }

                if (!vc.hasAttribute("IN_CONSENSUS"))
                {
                    continue;
                }

                if (!consensusMap.containsKey(vc.getStart()))
                {
                    consensusMap.put(vc.getStart(), new ArrayList<>());
                }

                consensusMap.get(vc.getStart()).add(vc);
            }
        }

        return consensusMap;
    }

    public static void deleteExistingMetrics(PipelineJob job, int analysisId, String category) throws PipelineJobException
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("category"), category);
        deleteExistingValues(job, analysisId, SequenceAnalysisSchema.TABLE_QUALITY_METRICS, filter);
    }

    public static void deleteExistingValues(PipelineJob job, int analysisId, String queryName, SimpleFilter filter) throws PipelineJobException
    {
        Container targetContainer = job.getContainer().isWorkbook() ? job.getContainer().getParent() : job.getContainer();
        TableInfo ti = QueryService.get().getUserSchema(job.getUser(), targetContainer, SequenceAnalysisSchema.SCHEMA_NAME).getTable(queryName);

        if (filter == null)
        {
            filter = new SimpleFilter(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
        }
        else
        {
            filter.addCondition(FieldKey.fromString("analysis_id"), analysisId, CompareType.EQUAL);
        }

        List<Integer> toDelete = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null).getArrayList(Integer.class);
        if (!toDelete.isEmpty())
        {
            job.getLogger().info("Deleting " + toDelete.size() + " existing metric rows");
            TableInfo schemaTable = SequenceAnalysisSchema.getTable(queryName);
            toDelete.forEach(x -> Table.delete(schemaTable, x));
        }
    }


}
