package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.filter.DuplicateReadFilter;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.analysis.AlignmentAggregator;
import org.labkey.sequenceanalysis.run.analysis.AvgBaseQualityAggregator;
import org.labkey.sequenceanalysis.run.analysis.BamIterator;
import org.labkey.sequenceanalysis.run.analysis.SequenceBasedTypingAlignmentAggregator;
import org.labkey.sequenceanalysis.run.analysis.SequenceBasedTypingAnalysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by bimber on 6/2/2015.
 */
public class UnmappedSequenceBasedGenotypeHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType(".bam", FileType.gzSupportLevel.NO_GZ);

    public UnmappedSequenceBasedGenotypeHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Export Unmapped SBT Reads", "This will re-run sequence-based genotyping on each input BAM and export the read pairs with at least one alignment, but no passing alignments, to a FASTQ files.  It will also collapse these reads into a single FASTA for all samples.", null, SequenceBasedTypingAnalysis.getDefaultParams(false));
    }

    @Override
    public boolean canProcess(SequenceOutputFile f)
    {
        return f.getFile() != null && _fileType.isType(f.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new Processor();
    }

    private static class FastqAggregate
    {
        private Map<String, Integer> _sampleMap = new HashMap<>();
        private Map<String, Integer> _sampleTotalReadsMap = new HashMap<>();
        private Integer _totalReads = 0;
        private String _sequence;

        public FastqAggregate(String sequence)
        {
            _sequence = sequence;
        }

        public void addSample(String sampleName, String header, int totalReads)
        {
            String[] tokens = header.split("-");

            Integer count = Integer.parseInt(tokens[tokens.length - 1]);
            Integer c = _sampleMap.getOrDefault(sampleName, 0);
            c += count;
            _totalReads += count;
            _sampleMap.put(sampleName, c);
            _sampleTotalReadsMap.put(sampleName, totalReads);
        }

        public double getAvgPct()
        {
            double sum = 0.0;
            for (String sn : _sampleMap.keySet())
            {
                sum += (double) _sampleMap.get(sn) / (double) _sampleTotalReadsMap.get(sn);
            }

            return (sum / (double)_sampleMap.size());
        }

        private List<Double> getAvgPcts()
        {
            List<Double> ret = new ArrayList<>();
            for (String sn : _sampleMap.keySet())
            {
                ret.add((double) _sampleMap.get(sn) / (double) _sampleTotalReadsMap.get(sn));
            }

            return ret;
        }

        public String getHeaderLine()
        {
            return "Samples-" + _sampleMap.keySet().size() + ";Reads-" + _totalReads + ";AvgPct-" + getAvgPct();
        }

        public static String[] getTSVHeader()
        {
            return new String[]{"NumSamples", "TotalReads", "AvgPct", "ReadCounts", "Percents", "SampleNames"};
        }

        public String[] getTSVLine()
        {
            return new String[]{String.valueOf(_sampleMap.keySet().size()), String.valueOf(_totalReads), String.valueOf(getAvgPct()), StringUtils.join(_sampleMap.values(), ";"), StringUtils.join(getAvgPcts(), ";"), StringUtils.join(_sampleMap.keySet(), ";")};
        }
    }

    public class Processor implements SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            //outDir will be the local webserver directory
            SequenceBasedTypingAnalysis.prepareLineageMapFiles(ctx.getSequenceSupport(), ctx.getJob().getLogger(), ctx.getOutputDir());
        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            PipelineJob job = ctx.getJob();
            JSONObject params = ctx.getParams();

            File jointUnmappedCollapsed = new File(ctx.getOutputDir(), "unmapped_collapsed.fasta");
            File jointUnmappedCollapsedTsv = new File(ctx.getOutputDir(), "unmapped_collapsed.txt");

            Map<String, FastqAggregate> uniqueReads = new HashMap<>();
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            int j = 0;
            Set<Integer> distinctGenomes = new HashSet<>();
            for (SequenceOutputFile so : inputFiles)
            {
                try
                {
                    j++;
                    String msg = "processing file: " + so.getFile().getName() + ", " + j + " of " + inputFiles.size();
                    job.getLogger().info(msg);
                    job.setStatus(PipelineJob.TaskStatus.running, msg);

                    action.addInput(so.getFile(), "Input BAM");

                    ReferenceGenome rg = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
                    if (rg == null)
                    {
                        throw new PipelineJobException("Unable to find reference genome for outputfile: " + so.getRowid());
                    }
                    distinctGenomes.add(rg.getGenomeId());

                    //first calculate avg qualities at each position
                    job.getLogger().info("Calculating avg quality scores");
                    AvgBaseQualityAggregator avgBaseQualityAggregator = new AvgBaseQualityAggregator(job.getLogger(), so.getFile(), rg.getWorkingFastaFile(), Arrays.asList(
                            new DuplicateReadFilter()
                    ));
                    avgBaseQualityAggregator.calculateAvgQuals();
                    job.getLogger().info("\tCalculation complete");

                    job.getLogger().info("Inspecting alignments in BAM");
                    BamIterator bi = new BamIterator(so.getFile(), rg.getWorkingFastaFile(), job.getLogger());

                    List<AlignmentAggregator> aggregators = new ArrayList<>();
                    Map<String, String> toolParams = new HashMap<>();
                    for (String param : params.keySet())
                    {
                        if (params.get(param) != null)
                            toolParams.put(param, params.get(param).toString());
                    }

                    SequenceBasedTypingAlignmentAggregator agg = new SequenceBasedTypingAlignmentAggregator(job.getLogger(), rg.getWorkingFastaFile(), avgBaseQualityAggregator, toolParams);
                    agg.setDoTrackIntervals(true);

                    File lineageMapFile = new File(ctx.getSourceDirectory(), rg.getGenomeId() + "_lineageMap.txt");
                    if (lineageMapFile.exists())
                    {
                        ctx.getLogger().debug("using lineage map: " + lineageMapFile.getName());
                        agg.setLineageMapFile(lineageMapFile);

                        Double minPctForLineageFiltering = ctx.getParams().optDouble("minPctForLineageFiltering");
                        if (minPctForLineageFiltering != null)
                        {
                            agg.setMinPctForLineageFiltering(minPctForLineageFiltering);
                        }
                    }
                    else
                    {
                        ctx.getLogger().debug("lineage map not found, skipping");
                    }

                    aggregators.add(agg);

                    File outputLog = null;
                    if (params.has("writeLog") && params.optBoolean("writeLog", false))
                    {
                        outputLog = new File(ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()) + ".sbt.txt.gz");
                        agg.setOutputLog(outputLog);
                    }

                    int minAlignmentLength = 0;
                    if (params.get("minAlignmentLength") != null)
                    {
                        minAlignmentLength = params.getInt("minAlignmentLength");
                    }

                    bi.addAggregators(aggregators);
                    bi.iterateReads();

                    String prefix = String.valueOf(so.getAnalysis_id());
                    if (so.getReadset() != null)
                    {
                        Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                        if (rs != null && rs.getSubjectId() != null)
                        {
                            prefix = rs.getSubjectId() + "|" + rs.getRowId() + "|";
                        }
                    }

                    agg.writeSummary();
                    job.getLogger().info("writing unmapped reads to: " + ctx.getOutputDir());
                    int minExportLength = ctx.getParams().optInt(SequenceBasedTypingAnalysis.MIN_EXPORT_LENGTH, 0);

                    Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                    if (rs == null)
                    {
                        throw new PipelineJobException("File lacks a readset");
                    }

                    List<Pair<File, File>> readData = new ArrayList<>();
                    for (ReadData d : rs.getReadData())
                    {
                        readData.add(Pair.of(d.getFile1(), d.getFile2()));
                    }

                    String suffix = so.getAnalysis_id() == null ? "" : "." + so.getAnalysis_id();
                    Pair<File, File> outputs = agg.outputUnmappedReads(so.getFile(), readData, ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()) + suffix, prefix, minExportLength);
                    if (outputs == null)
                    {
                        job.getLogger().info("no unmapped reads, skipping");
                        continue;
                    }

                    //append / merge
                    ctx.getLogger().info("parsing FASTA: " + outputs.second.getPath());
                    try (FastaDataLoader loader = new FastaDataLoader(outputs.second, false))
                    {
                        loader.setCharacterFilter(c -> ((c >= 'A') && (c <= 'Z')) || ((c >= 'a') && (c <= 'z')));

                        try (CloseableIterator<Map<String, Object>> i = loader.iterator())
                        {
                            while (i.hasNext())
                            {
                                Map<String, Object> map = i.next();
                                String sequence = map.get("sequence").toString().replaceAll("\\s+", "");
                                if (sequence.length() < minAlignmentLength)
                                {
                                    continue;
                                }

                                if (!uniqueReads.containsKey(sequence))
                                {
                                    uniqueReads.put(sequence, new FastqAggregate(map.get("sequence").toString()));
                                }

                                FastqAggregate fa = uniqueReads.get(sequence);
                                fa.addSample(so.getReadset().toString(), map.get("header").toString(), agg.getUniqueReads().size());
                            }
                        }
                    }

                    File unmappedGz = outputs.first;
                    File unmappedCollapsedGz = Compress.compressGzip(outputs.second);
                    if (outputs.second.exists())
                    {
                        outputs.second.delete();
                    }

                    action.addOutput(unmappedGz, "Unmapped SBT Reads (FASTQ)", false, true);
                    SequenceOutputFile so1 = new SequenceOutputFile();
                    so1.setCategory("Unmapped SBT Reads (FASTQ)");
                    so1.setFile(unmappedGz);
                    so1.setLibrary_id(rg.getGenomeId());
                    so1.setDescription("Unmapped SBT Reads");
                    so1.setAnalysis_id(so.getAnalysis_id());
                    so1.setReadset(so.getReadset());
                    so1.setName(rs.getName() + ", Unmapped SBT Reads (FASTQ)");
                    ctx.addSequenceOutput(so1);

                    action.addOutput(unmappedCollapsedGz, "Unmapped SBT Reads (Collapsed)", false, true);
                    SequenceOutputFile so2 = new SequenceOutputFile();
                    so2.setCategory("Unmapped SBT Reads (Collapsed)");
                    so2.setFile(unmappedCollapsedGz);
                    so2.setLibrary_id(rg.getGenomeId());
                    so2.setDescription("Unmapped SBT Reads (Collapsed)");
                    so2.setAnalysis_id(so.getAnalysis_id());
                    so2.setReadset(so.getReadset());
                    so2.setName(rs.getName() + ", Unmapped SBT Reads (Collapsed)");
                    ctx.addSequenceOutput(so2);

                    File referencesCovered = agg.outputReferencesCovered(ctx.getOutputDir(), FileUtil.getBaseName(so.getFile()), rg.getWorkingFastaFile(), prefix);
                    if (referencesCovered.exists())
                    {
                        action.addOutput(referencesCovered, "Reference Sequence Coverage FASTA", false, true);
                        SequenceOutputFile so3 = new SequenceOutputFile();
                        so3.setCategory("Reference Sequence Coverage FASTA");
                        so3.setFile(referencesCovered);
                        so3.setLibrary_id(rg.getGenomeId());
                        so3.setDescription("Reference Sequence Coverage FASTA");
                        so3.setAnalysis_id(so.getAnalysis_id());
                        so3.setReadset(so.getReadset());
                        if (rs != null)
                        {
                            so3.setName(rs.getName() + ", Reference Sequence Coverage FASTA");
                        }
                        else
                        {
                            so3.setName(so.getName() + ", Reference Sequence Coverage FASTA");
                        }
                        ctx.addSequenceOutput(so3);
                    }
                    else
                    {
                        ctx.getLogger().warn("unable to find expected FASTA: " + referencesCovered.getPath());
                    }

                    if (outputLog != null)
                    {
                        action.addOutput(outputLog, "SBT Detail Log", false, true);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            ctx.getLogger().info("building merged file: " + jointUnmappedCollapsed.getPath());
            try (BufferedWriter jointUnmappedCollapsedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jointUnmappedCollapsed), StandardCharsets.UTF_8)); CSVWriter jointUnmappedCollapsedTsvWriter = new CSVWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jointUnmappedCollapsedTsv), "UTF-8")), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                List<FastqAggregate> sorted = new ArrayList<>();
                sorted.addAll(uniqueReads.values());
                sorted.sort((o1, o2) -> o2._totalReads.compareTo(o1._totalReads));

                jointUnmappedCollapsedTsvWriter.writeNext(FastqAggregate.getTSVHeader());

                for (FastqAggregate fa : sorted)
                {
                    jointUnmappedCollapsedWriter.write(">" + fa.getHeaderLine() + "\n");
                    jointUnmappedCollapsedWriter.write(fa._sequence + "\n");

                    jointUnmappedCollapsedTsvWriter.writeNext(fa.getTSVLine());
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            action.addOutput(jointUnmappedCollapsed, "Combined Unmapped FASTA", false);
            action.addOutput(jointUnmappedCollapsedTsv, "Combined Unmapped Summaary", false);
            if (inputFiles.size() > 1)
            {
                SequenceOutputFile combinedCollapsed = new SequenceOutputFile();
                combinedCollapsed.setCategory("Unmapped SBT Reads (Collapsed)");
                combinedCollapsed.setFile(jointUnmappedCollapsed);
                if (distinctGenomes.size() == 1)
                {
                    combinedCollapsed.setLibrary_id(distinctGenomes.iterator().next());
                }
                combinedCollapsed.setDescription("Joint Unmapped SBT Reads (Collapsed), " + inputFiles.size() + " datasets");
                combinedCollapsed.setName("Joint Unmapped SBT Reads (Collapsed)");
                ctx.addSequenceOutput(combinedCollapsed);

                SequenceOutputFile combinedCollapsedTsv = new SequenceOutputFile();
                combinedCollapsedTsv.setCategory("Unmapped SBT Reads Summary Table");
                combinedCollapsedTsv.setFile(jointUnmappedCollapsedTsv);
                if (distinctGenomes.size() == 1)
                {
                    combinedCollapsedTsv.setLibrary_id(distinctGenomes.iterator().next());
                }
                combinedCollapsedTsv.setDescription("Joint Unmapped SBT Reads Summary Table, " + inputFiles.size() + " datasets");
                combinedCollapsedTsv.setName("Joint Unmapped SBT Reads Summary Table");
                ctx.addSequenceOutput(combinedCollapsedTsv);
            }

            action.setEndTime(new Date());
            ctx.addActions(action);

            //delete lineage files
            for (SequenceOutputFile so : inputFiles)
            {
                if (so.getLibrary_id() != null)
                {
                    ReferenceGenome referenceGenome = ctx.getSequenceSupport().getCachedGenome(so.getLibrary_id());
                    if (referenceGenome == null)
                    {
                        throw new PipelineJobException("Genome not found: " + so.getLibrary_id());
                    }

                    File lineageMapFile = new File(ctx.getSourceDirectory(), referenceGenome.getGenomeId() + "_lineageMap.txt");
                    if (lineageMapFile.exists())
                    {
                        ctx.getLogger().debug("deleting lineage map file: " + lineageMapFile.getName());
                        lineageMapFile.delete();
                    }
                }
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
        }
    }
}
