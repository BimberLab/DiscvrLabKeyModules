package org.labkey.sequenceanalysis.run.analysis;

import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.Table;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.sequenceanalysis.SequenceAnalysisManager;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.api.picard.CigarPositionIterable;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.run.util.NTSnp;
import picard.analysis.AlignmentSummaryMetrics;
import picard.analysis.AlignmentSummaryMetricsCollector;
import picard.analysis.MetricAccumulationLevel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 11/24/12
 * Time: 10:00 AM
 */
public class MetricsAggregator implements AlignmentAggregator
{
    private AlignmentSummaryMetricsCollector _collector = null;
    private Logger _log;

    public MetricsAggregator(Logger log, File inputFile)
    {
        SAMFileReader sam = null;
        _log = log;

        try
        {
            sam = new SAMFileReader(inputFile);
            _collector = new AlignmentSummaryMetricsCollector(Collections.singleton(MetricAccumulationLevel.ALL_READS), sam.getFileHeader().getReadGroups(), true, new ArrayList<String>(), 0, false);
        }
        finally
        {
            sam.close();
        }

        if (_collector == null)
        {
            throw new IllegalArgumentException("Unable to read BAM file: " + inputFile.getPath());
        }
    }

    @Override
    public void inspectAlignment(SAMRecord record, ReferenceSequence ref, Map<Integer, List<NTSnp>> snps, CigarPositionIterable cpi)
    {
        _collector.acceptRecord(record, ref);
    }

    public List<AlignmentSummaryMetrics> getMetrics()
    {
        MetricsFile metricsFile = new MetricsFile<AlignmentSummaryMetrics, Comparable<?>>();
        _collector.addAllLevelsToFile(metricsFile);

        return metricsFile.getMetrics();
    }

    @Override
    public void saveToDb(User u, Container c, AnalysisModel model)
    {
        ExpData d = ExperimentService.get().getExpData(model.getAlignmentFile());
        _log.info("\tSaving BAM summary metrics");

        List<AlignmentSummaryMetrics> metrics = getMetrics();
        for (AlignmentSummaryMetrics m : metrics)
        {
            Map<String, Object> row = new HashMap<>();
            row.put("Avg Sequence Length", m.MEAN_READ_LENGTH);
            row.put("%Reads Aligned In Pairs", m.PCT_READS_ALIGNED_IN_PAIRS);
            row.put("Total Sequences", m.TOTAL_READS);
            row.put("Total Sequences Passed Filter", m.PF_READS);
            row.put("Reads Aligned", m.PF_READS_ALIGNED);
            row.put("%Reads Aligned", m.PCT_PF_READS_ALIGNED);

            for (String metricName : row.keySet())
            {
                Map<String, Object> r = new HashMap<>();
                r.put("metricname", metricName);
                r.put("metricvalue", row.get(metricName));
                r.put("dataid", d.getRowId());
                r.put("analysis_id", model.getAnalysisId());
                r.put("container", c.getEntityId());
                r.put("createdby", u.getUserId());

                Table.insert(u, SequenceAnalysisManager.get().getTable(SequenceAnalysisSchema.TABLE_QUALITY_METRICS), r);
            }
        }
    }

    @Override
    public String getSynopsis()
    {
        return "MetricsAggregator\n";
    }
}