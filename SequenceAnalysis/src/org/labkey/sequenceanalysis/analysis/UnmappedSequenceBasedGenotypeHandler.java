package org.labkey.sequenceanalysis.analysis;

import htsjdk.samtools.filter.DuplicateReadFilter;
import htsjdk.samtools.filter.SamRecordFilter;
import org.json.JSONObject;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.FastaLoader;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.Compress;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.analysis.AlignmentAggregator;
import org.labkey.sequenceanalysis.run.analysis.AvgBaseQualityAggregator;
import org.labkey.sequenceanalysis.run.analysis.BamIterator;
import org.labkey.sequenceanalysis.run.analysis.SequenceBasedTypingAlignmentAggregator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 6/2/2015.
 */
public class UnmappedSequenceBasedGenotypeHandler extends AbstractParameterizedOutputHandler
{
    private FileType _fileType = new FileType(".bam", FileType.gzSupportLevel.NO_GZ);

    public UnmappedSequenceBasedGenotypeHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Export Unmapped SBT Reads", "This will re-run sequence-based genotyping on each input BAM and export the read pairs with at least one alignment, but no passing alignments, to a FASTQ files.  It will also collapse these reads into a single FASTA for all samples.", null, Arrays.asList(
                        ToolParameterDescriptor.create("minSnpQual", "Minimum SNP Qual", "Only SNPs with a quality score above this threshold will be included.", "ldk-integerfield", new JSONObject()
                        {{
                                put("minValue", 0);
                            }}, 17),
                        ToolParameterDescriptor.create("minSnpAvgQual", "Minimum SNP Avg Qual", "If provided, the average quality score of all SNPs of a give base at each position must be above this value.", "ldk-integerfield", new JSONObject()
                        {{
                                put("minValue", 0);
                            }}, 17),
                        ToolParameterDescriptor.create("onlyImportValidPairs", "Only Import Valid Pairs", "If selected, only alignments consisting of valid forward/reverse pairs will be imported.  Do not check this unless you are using paired-end sequence.", "checkbox", new JSONObject()
                        {{
                                put("checked", false);
                            }}, null),
                        ToolParameterDescriptor.create("minCountForRef", "Min Read # Per Reference", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this many reads across each sample.  This can be a way to reduce ambiguity among allele calls.", "ldk-integerfield", new JSONObject()
                        {{
                                put("minValue", 0);
                            }}, 5),
                        ToolParameterDescriptor.create("minPctForRef", "Min Read Pct Per Reference", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this percent of total from each sample.  This can be a way to reduce ambiguity among allele calls.  Value should between 0-100.", "ldk-numberfield", new JSONObject()
                        {{
                                put("minValue", 0);
                                put("maxValue", 100);
                            }}, 0.05),
                        ToolParameterDescriptor.create("minPctWithinGroup", "Min Read Pct Within Group", "If a value is provided, for a reference to be considered an allowable hit, it must be present in at least this percent of total reads within a set of hits.  For example, says 30 reads matched alleles A, B and C.  Within the whole sample, 300 reads aligned to allele B, 200 to allele B and only 30 aligned to C.  The latter represent 10% (30 / 300) of hits for that group.  If you set this filter above this, allele C would be discarded.  Value should between 0-100.", "ldk-numberfield", new JSONObject()
                        {{
                                put("minValue", 0);
                                put("maxValue", 100);
                            }}, 25),
                        ToolParameterDescriptor.create("minAlignmentLength", "Min Alignment Length", "If a value is provided, any alignment with a length less than this value will be discarded.", "ldk-integerfield", new JSONObject()
                        {{
                                put("minValue", 0);
                            }}, 40),
                        ToolParameterDescriptor.create("writeLog", "Write Detailed Log", "If checked, the analysis will write a detailed log file of read mapping and calls.  This is intended for debugging purposes", "checkbox", new JSONObject()
                        {{
                                put("checked", true);
                            }}, null))

        );

    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
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
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    private static class FastqAggregate
    {
        private Map<String, Integer> _sampleMap = new HashMap<>();
        private Integer _totalReads = 0;
        private String _sequence;

        public FastqAggregate(String sequence)
        {
            _sequence = sequence;
        }

        public void addSample(String sampleName, String header)
        {
            String[] tokens = header.split("-");

            Integer count = Integer.parseInt(tokens[tokens.length - 1]);
            Integer c = _sampleMap.containsKey(sampleName) ? _sampleMap.get(sampleName) : 0;
            c += count;
            _totalReads += count;
            _sampleMap.put(sampleName, c);
        }

        public String getHeaderLine()
        {
            return "Samples-" + _sampleMap.keySet().size() + ";Reads-" + _totalReads;
        }
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            File jointUnmappedCollapsed = new File(outputDir, "unmapped_collapsed.fasta");

            Map<String, FastqAggregate> uniqueReads = new HashMap<>();
            RecordedAction action = new RecordedAction(getName());
            action.setStartTime(new Date());

            int j = 0;
            for (SequenceOutputFile so : inputFiles)
            {
                try
                {
                    j++;
                    String msg = "processing file: " + so.getFile().getName() + ", " + j + " of " + inputFiles.size();
                    job.getLogger().info(msg);
                    job.setStatus(msg);

                    action.addInput(so.getFile(), "Input BAM");

                    ReferenceGenome rg = support.getCachedGenome(so.getLibrary_id());

                    //first calculate avg qualities at each position
                    job.getLogger().info("Calculating avg quality scores");
                    AvgBaseQualityAggregator avgBaseQualityAggregator = new AvgBaseQualityAggregator(job.getLogger(), so.getFile(), rg.getWorkingFastaFile(), Arrays.<SamRecordFilter>asList(
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
                    aggregators.add(agg);

                    File outputLog = null;
                    if (params.containsKey("writeLog") && "on".equals(params.getString("writeLog")))
                    {
                        outputLog = new File(outputDir, FileUtil.getBaseName(so.getFile()) + ".sbt.txt.gz");
                        agg.setOutputLog(outputLog);
                    }

                    int minAlignmentLength = 0;
                    if (params.get("minAlignmentLength") != null)
                    {
                        minAlignmentLength = params.getInt("minAlignmentLength");
                    }

                    bi.addAggregators(aggregators);
                    bi.iterateReads();

                    agg.writeSummary();
                    job.getLogger().info("writing unmapped reads");
                    Pair<File, File> outputs = agg.outputUnmappedReads(so.getFile(), outputDir, FileUtil.getBaseName(so.getFile()));
                    if (outputs == null)
                    {
                        job.getLogger().info("no unmapped reads, skipping");
                        continue;
                    }

                    //append / merge
                    try (FastaDataLoader loader = new FastaDataLoader(outputs.second, false))
                    {
                        loader.setCharacterFilter(new FastaLoader.CharacterFilter()
                        {
                            @Override
                            public boolean accept(char c)
                            {
                                return ((c >= 'A') && (c <= 'Z')) || ((c >= 'a') && (c <= 'z'));
                            }
                        });

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
                                fa.addSample(so.getReadset().toString(), map.get("header").toString());
                            }
                        }
                    }

                    File unmappedGz = Compress.compressGzip(outputs.first);
                    if (outputs.first.exists())
                    {
                        outputs.first.delete();
                    }

                    File unmappedCollapsedGz = Compress.compressGzip(outputs.second);
                    if (outputs.second.exists())
                    {
                        outputs.second.delete();
                    }

                    action.addOutput(unmappedGz, "Unmapped FASTA", false);
                    action.addOutput(unmappedCollapsedGz, "Unmapped Collapsed FASTA", false);

                    if (outputLog != null)
                    {
                        action.addOutput(outputLog, "SBT Detail Log", false);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            action.addOutput(jointUnmappedCollapsed, "Combined Unmapped FASTA", false);

            action.setEndTime(new Date());
            actions.add(action);

            try (BufferedWriter jointUnmappedCollapsedWriter = new BufferedWriter(new FileWriter(jointUnmappedCollapsed)))
            {
                List<FastqAggregate> sorted = new ArrayList<>();
                sorted.addAll(uniqueReads.values());
                Collections.sort(sorted, new Comparator<FastqAggregate>()
                {
                    @Override
                    public int compare(FastqAggregate o1, FastqAggregate o2)
                    {
                        return o2._totalReads.compareTo(o1._totalReads);
                    }
                });

                for (FastqAggregate fa : sorted)
                {
                    jointUnmappedCollapsedWriter.write(">" + fa.getHeaderLine() + "\n");
                    jointUnmappedCollapsedWriter.write(fa._sequence + "\n");
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}
