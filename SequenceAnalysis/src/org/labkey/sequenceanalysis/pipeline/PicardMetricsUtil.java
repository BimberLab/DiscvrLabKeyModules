package org.labkey.sequenceanalysis.pipeline;

import htsjdk.samtools.metrics.MetricBase;
import htsjdk.samtools.metrics.MetricsFile;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.pipeline.PipelineJobException;
import picard.analysis.AlignmentSummaryMetrics;
import picard.analysis.CollectWgsMetrics;
import picard.analysis.InsertSizeMetrics;
import picard.sam.DuplicationMetrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 5/5/2016.
 */
public class PicardMetricsUtil
{
    public static List<Map<String, Object>> processFile(File f, Logger log) throws PipelineJobException
    {
        if (!f.exists())
        {
            throw new PipelineJobException("Unable to find file: " + f.getPath());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), Charsets.UTF_8)))
        {
            MetricsFile metricsFile = new MetricsFile();
            metricsFile.read(reader);
            List<MetricBase> metrics = metricsFile.getMetrics();
            if (metrics.get(0).getClass() == DuplicationMetrics.class)
            {
                return processDuplicationMetrics(metricsFile, log);
            }
            else if (metrics.get(0).getClass() == InsertSizeMetrics.class)
            {
                log.info("Importing Picard InsertSize metrics from: " + f.getName());
                return processInsertSizeMetrics(metricsFile, log);
            }
            else if (metrics.get(0).getClass() == AlignmentSummaryMetrics.class)
            {
                log.info("Importing Picard AlignmentSummaryMetricsCollector metrics from: " + f.getName());
                return processAlignmentSummaryMetrics(metricsFile, log);
            }
            else if (metrics.get(0).getClass() == CollectWgsMetrics.WgsMetrics.class)
            {
                log.info("Importing Picard WgsMetrics for: " + f.getName());
                return processWgsMetrics(metricsFile, log);
            }
            else
            {
                throw new PipelineJobException("Unknown metric file type: " + metrics.get(0).getClass().getName());
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private static List<Map<String, Object>> processWgsMetrics(MetricsFile mf, Logger log) throws PipelineJobException
    {
        List<Map<String, Object>> ret = new ArrayList<>();
        List<CollectWgsMetrics.WgsMetrics> metrics = mf.getMetrics();

        for (CollectWgsMetrics.WgsMetrics m : metrics)
        {
            Map<String, Object> metricNames = new HashMap<>();
            metricNames.put("Mean Coverage", m.MEAN_COVERAGE);
            metricNames.put("Median Coverage", m.MEDIAN_COVERAGE);
            metricNames.put("Pct 10X", m.PCT_10X);
            metricNames.put("Pct 20X", m.PCT_20X);
            metricNames.put("Pct 30X", m.PCT_30X);
            metricNames.put("Pct 40X", m.PCT_40X);
            metricNames.put("Pct 50X", m.PCT_50X);
            metricNames.put("SD Coverage", m.SD_COVERAGE);

            for (String metricName : metricNames.keySet())
            {
                if (metricNames.get(metricName) == null || StringUtils.isEmpty(String.valueOf(metricNames.get(metricName))))
                {
                    log.debug("\tskipping empty metric: " + metricName);
                    continue;
                }

                Double val;
                try
                {
                    val = ConvertHelper.convert(metricNames.get(metricName), Double.class);
                }
                catch (ConversionException e)
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                if (Double.isNaN(val))
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                Map<String, Object> r = new HashMap<>();
                r.put("category", "WGS Metrics");
                r.put("metricname", metricName);
                r.put("metricvalue", val);

                ret.add(r);
            }
        }

        return ret;
    }

    private static List<Map<String, Object>> processDuplicationMetrics(MetricsFile metricsFile, Logger log) throws PipelineJobException
    {
        List<Map<String, Object>> ret = new ArrayList<>();

        List<DuplicationMetrics> metrics = metricsFile.getMetrics();
        for (DuplicationMetrics m : metrics)
        {
            Map<String, Object> metricNames = new HashMap<>();
            metricNames.put("UNPAIRED_READS_EXAMINED", m.UNPAIRED_READS_EXAMINED);
            metricNames.put("READ_PAIRS_EXAMINED", m.READ_PAIRS_EXAMINED);
            metricNames.put("SECONDARY_OR_SUPPLEMENTARY_RDS", m.SECONDARY_OR_SUPPLEMENTARY_RDS);
            metricNames.put("UNMAPPED_READS", m.UNMAPPED_READS);
            metricNames.put("UNPAIRED_READ_DUPLICATES", m.UNPAIRED_READ_DUPLICATES);
            metricNames.put("READ_PAIR_DUPLICATES", m.READ_PAIR_DUPLICATES);
            metricNames.put("READ_PAIR_OPTICAL_DUPLICATES", m.READ_PAIR_OPTICAL_DUPLICATES);
            metricNames.put("PERCENT_DUPLICATION", m.PERCENT_DUPLICATION);
            metricNames.put("ESTIMATED_LIBRARY_SIZE", m.ESTIMATED_LIBRARY_SIZE);

            for (String metricName : metricNames.keySet())
            {
                if (metricNames.get(metricName) == null || StringUtils.isEmpty(String.valueOf(metricNames.get(metricName))))
                {
                    log.debug("\tskipping empty metric: " + metricName);
                    continue;
                }

                Double val;
                try
                {
                    val = ConvertHelper.convert(metricNames.get(metricName), Double.class);
                }
                catch (ConversionException e)
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                if (Double.isNaN(val))
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                Map<String, Object> r = new HashMap<>();
                r.put("category", "WGS Metrics");
                r.put("metricname", metricName);
                r.put("metricvalue", val);

                ret.add(r);
            }
        }

        return ret;
    }

    private static List<Map<String, Object>> processInsertSizeMetrics(MetricsFile metricsFile, Logger log) throws PipelineJobException
    {
        List<Map<String, Object>> ret = new ArrayList<>();

        List<InsertSizeMetrics> metrics = metricsFile.getMetrics();
        for (InsertSizeMetrics m : metrics)
        {
            Map<String, Object> metricNames = new HashMap<>();
            metricNames.put("Mean Insert Size", m.MEAN_INSERT_SIZE);
            metricNames.put("Median Insert Size", m.MEDIAN_INSERT_SIZE);
            metricNames.put("Insert Size Std. Deviation", m.STANDARD_DEVIATION);

            for (String metricName : metricNames.keySet())
            {
                if (metricNames.get(metricName) == null || StringUtils.isEmpty(String.valueOf(metricNames.get(metricName))))
                {
                    log.debug("\tskipping empty metric: " + metricName);
                    continue;
                }

                Double val;
                try
                {
                    val = ConvertHelper.convert(metricNames.get(metricName), Double.class);
                }
                catch (ConversionException e)
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                if (Double.isNaN(val))
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                Map<String, Object> r = new HashMap<>();
                r.put("category", "Insert Size");
                r.put("metricname", metricName);
                r.put("metricvalue", val);

                ret.add(r);
            }
        }

        return ret;
    }

    private static List<Map<String, Object>> processAlignmentSummaryMetrics(MetricsFile metricsFile, Logger log) throws PipelineJobException
    {
        List<Map<String, Object>> ret = new ArrayList<>();

        List<AlignmentSummaryMetrics> metrics = metricsFile.getMetrics();
        for (AlignmentSummaryMetrics m : metrics)
        {
            Map<String, Object> metricNames = new HashMap<>();
            metricNames.put("Avg Sequence Length", m.MEAN_READ_LENGTH);
            metricNames.put("%Reads Aligned In Pairs", m.PCT_READS_ALIGNED_IN_PAIRS * 100.0);
            metricNames.put("Total Sequences", m.TOTAL_READS);
            metricNames.put("Total Sequences Passed Filter", m.PF_READS);
            metricNames.put("Reads Aligned", m.PF_READS_ALIGNED);
            metricNames.put("%Reads Aligned", m.PCT_PF_READS_ALIGNED * 100.0);

            metricNames.put("Reads Aligned (>Q20)", m.PF_HQ_ALIGNED_READS);
            metricNames.put("% Reads Aligned (>Q20)", (double) m.PF_HQ_ALIGNED_READS / (double) m.TOTAL_READS);
            metricNames.put("% Alignments (>Q20)", ((double) m.PF_HQ_ALIGNED_READS / (double) m.PF_READS_ALIGNED) * 100.0);

            metricNames.put("PF_INDEL_RATE", m.PF_INDEL_RATE);
            metricNames.put("PF_MISMATCH_RATE", m.PF_MISMATCH_RATE);
            metricNames.put("PF_HQ_MEDIAN_MISMATCHES", m.PF_HQ_MEDIAN_MISMATCHES);
            metricNames.put("PF_HQ_ALIGNED_BASES", m.PF_HQ_ALIGNED_BASES);
            metricNames.put("PF_HQ_ALIGNED_Q20_BASES", m.PF_HQ_ALIGNED_Q20_BASES);

            for (String metricName : metricNames.keySet())
            {
                if (metricNames.get(metricName) == null || StringUtils.isEmpty(String.valueOf(metricNames.get(metricName))))
                {
                    log.debug("\tskipping empty metric: " + metricName);
                    continue;
                }

                Double val;
                try
                {
                    val = ConvertHelper.convert(metricNames.get(metricName), Double.class);
                }
                catch (ConversionException e)
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                if (Double.isNaN(val))
                {
                    log.debug("\tmetric value not numeric: " + metricName + " [" + metricNames.get(metricName) + "]");
                    continue;
                }

                Map<String, Object> r = new HashMap<>();
                r.put("category", m.CATEGORY);
                r.put("metricname", metricName);
                r.put("metricvalue", metricNames.get(metricName));

                ret.add(r);
            }
        }

        return ret;
    }
}
