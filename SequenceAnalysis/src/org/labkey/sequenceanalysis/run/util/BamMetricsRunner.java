package org.labkey.sequenceanalysis.run.util;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.reference.FastaSequenceIndex;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJobException;
import picard.analysis.AlignmentSummaryMetricsCollector;
import picard.analysis.MetricAccumulationLevel;
import picard.analysis.directed.InsertSizeMetricsCollector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bimber on 9/12/2014.
 */
public class BamMetricsRunner
{
    private Logger _log;
    private Map<String, ReferenceSequence> _references = new HashMap<>();

    public BamMetricsRunner(Logger log)
    {
        _log = log;
    }


    private ReferenceSequence getReferenceSequenceFromFasta(String refName, IndexedFastaSequenceFile indexedRef)
    {
        if (_references.containsKey(refName))
        {
            return _references.get(refName);
        }
        else
        {
            _references.put(refName, indexedRef.getSequence(refName));
            return _references.get(refName);
        }
    }

    public MetricsFile generateMetricsForFile(File inputBam, File refFasta) throws PipelineJobException
    {
        File fai = new File(refFasta.getPath() + ".fai");
        SamReaderFactory fact = SamReaderFactory.make();
        fact.validationStringency(ValidationStringency.SILENT);
        try (SamReader sam = fact.open(inputBam);IndexedFastaSequenceFile indexedRef = new IndexedFastaSequenceFile(refFasta, new FastaSequenceIndex(fai)))
        {
            AlignmentSummaryMetricsCollector collector = new AlignmentSummaryMetricsCollector(Collections.singleton(MetricAccumulationLevel.ALL_READS), sam.getFileHeader().getReadGroups(), true, new ArrayList<String>(), 50000, false);

            try (SAMRecordIterator it = sam.iterator())
            {
                int i = 0;
                long startTime = new Date().getTime();

                while (it.hasNext())
                {
                    i++;
                    SAMRecord r = it.next();

                    try
                    {
                        ReferenceSequence ref = r.getReadUnmappedFlag() ? null : getReferenceSequenceFromFasta(r.getReferenceName(), indexedRef);
                        if (ref != null && r.getBaseQualities().length > 0)
                        {
                            collector.acceptRecord(it.next(), ref);
                        }
                    }
                    catch (Exception e)
                    {
                        _log.warn(e.getMessage(), e);
                    }

                    if (i % 250000 == 0)
                    {
                        long newTime = new Date().getTime();
                        _log.info("processed " + i + " reads in " + ((newTime - startTime) / 1000) + " seconds");
                        startTime = newTime;
                    }
                }
            }

            collector.finish();

            MetricsFile metricsFile = new MetricsFile();
            collector.addAllLevelsToFile(metricsFile);

            indexedRef.close();

            return metricsFile;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public MetricsFile generateInsertSizeMetricsForFile(File inputBam, File refFasta) throws PipelineJobException
    {
        File fai = new File(refFasta.getPath() + ".fai");
        SamReaderFactory fact = SamReaderFactory.make();
        fact.validationStringency(ValidationStringency.SILENT);
        try (SamReader sam = fact.open(inputBam);IndexedFastaSequenceFile indexedRef = new IndexedFastaSequenceFile(refFasta, new FastaSequenceIndex(fai)))
        {
            InsertSizeMetricsCollector collector = new InsertSizeMetricsCollector(Collections.singleton(MetricAccumulationLevel.ALL_READS), sam.getFileHeader().getReadGroups(), 0.05, null, 10.0);

            try (SAMRecordIterator it = sam.iterator())
            {
                int i = 0;
                long startTime = new Date().getTime();

                while (it.hasNext())
                {
                    i++;
                    SAMRecord r = it.next();

                    try
                    {
                        ReferenceSequence ref = r.getReadUnmappedFlag() ? null : getReferenceSequenceFromFasta(r.getReferenceName(), indexedRef);
                        if (ref != null && r.getBaseQualities().length > 0)
                        {
                            collector.acceptRecord(it.next(), ref);
                        }
                    }
                    catch (Exception e)
                    {
                        _log.warn(e.getMessage(), e);
                    }

                    if (i % 250000 == 0)
                    {
                        long newTime = new Date().getTime();
                        _log.info("processed " + i + " reads in " + ((newTime - startTime) / 1000) + " seconds");
                        startTime = newTime;
                    }
                }
            }

            collector.finish();

            MetricsFile metricsFile = new MetricsFile();
            collector.addAllLevelsToFile(metricsFile);

            indexedRef.close();

            return metricsFile;
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }
}
