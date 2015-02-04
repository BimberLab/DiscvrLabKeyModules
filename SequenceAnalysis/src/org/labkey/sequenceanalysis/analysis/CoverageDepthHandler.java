package org.labkey.sequenceanalysis.analysis;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.filter.AggregateFilter;
import htsjdk.samtools.filter.AlignedFilter;
import htsjdk.samtools.filter.DuplicateReadFilter;
import htsjdk.samtools.filter.FilteringIterator;
import htsjdk.samtools.filter.NotPrimaryAlignmentFilter;
import htsjdk.samtools.filter.SamRecordFilter;
import htsjdk.samtools.filter.SecondaryOrSupplementaryFilter;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.IntervalList;
import htsjdk.samtools.util.PeekableIterator;
import htsjdk.samtools.util.SamLocusIterator;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.RScriptEngineFactory;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.SequenceOutputHandler;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by bimber on 1/26/2015.
 */
public class CoverageDepthHandler implements SequenceOutputHandler
{
    private FileType _bamFileType = new FileType("bam", false);

    public CoverageDepthHandler()
    {

    }

    @Override
    public String getName()
    {
        return "Coverage / Copy # Calculations";
    }

    @Override
    public String getDescription()
    {
        return "This can be used to generate either a simple plot of genome coverage, or more complicated calculations like copy number variation relative to a control sample.";
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _bamFileType.isType(o.getFile());
    }

    @Override
    public String getButtonJSHandler()
    {
        return null;
    }

    @Override
    public ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds)
    {
        return DetailsURL.fromString("/sequenceanalysis/coverageDepth.view?outputFileIds=" + StringUtils.join(outputFileIds, ";"), c).getActionURL();
    }

    @Override
    public Module getOwningModule()
    {
        return ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return null;
    }

    @Override
    public void processFiles(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
    {
        CoverageSettings settings = new CoverageSettings(params);
        Set<File> rawDataFiles = new HashSet<>();
        try
        {
            Map<SequenceOutputFile, RecordedAction> actionMap = new HashMap<>();

            //step 1: define windows
            job.getLogger().info("generating windows");
            WindowStrategy windowStrategy = settings.getWindowStrategy();
            windowStrategy.generateWindows(inputFiles, outputDir);

            //step 3: using windows, iterate BAMs to generate raw data
            Map<SequenceOutputFile, File> rawDataMap = new HashMap<>();
            Map<SequenceOutputFile, File> normalizedDataMap = new HashMap<>();

            for (SequenceOutputFile outputFile : inputFiles)
            {
                RecordedAction action = new RecordedAction(getName());
                action.setStartTime(new Date());
                actionMap.put(outputFile, action);

                File bam = outputFile.getFile();
                if (bam == null || !bam.exists())
                {
                    job.getLogger().error("Unable to find BAM for output file: " + outputFile.getName());
                    continue;
                }

                File intervalsFile = windowStrategy.getWindowBedForOutput(outputFile);

                action.addInput(bam, "Input BAM");
                action.addInput(getFinalFilename(intervalsFile, settings), "Windows File");
                rawDataFiles.add(intervalsFile);

                List<Interval> intervalList = SequenceUtil.bedToIntervalList(intervalsFile);

                job.getLogger().info("generating raw data for: " + outputFile.getName());
                File rawDataFile = new File(outputDir, getBaseNameForFile(outputFile) + ".coverage.txt");
                generateRawDataForOutput(bam, intervalList, rawDataFile);
                action.addOutput(getFinalFilename(rawDataFile, settings), "Coverage Data", settings.deleteRawData());
                rawDataFiles.add(rawDataFile);

                job.getLogger().info("generating coverage-normalized data for: " + outputFile.getName());
                File normalizedDataFile = new File(outputDir, getBaseNameForFile(outputFile) + ".normalizedCoverage.txt");
                generateNormalizedDataForOutput(rawDataFile, normalizedDataFile);
                action.addOutput(getFinalFilename(normalizedDataFile, settings), "Coverage Data - Normalized To Avg", settings.deleteRawData());
                rawDataFiles.add(normalizedDataFile);

                normalizedDataMap.put(outputFile, normalizedDataFile);
                rawDataMap.put(outputFile, rawDataFile);
            }

            //step 4: calculate values relative to another sample, if needed
            Integer referenceSampleId = windowStrategy.getReferenceSampleForWindows();
            SequenceOutputFile referenceSample = referenceSampleId == null ? null : getSequenceOutputByRowId(referenceSampleId, inputFiles);
            Map<SequenceOutputFile, File> normalizedToSampleMap = new HashMap<>();
            if (referenceSample != null)
            {
                //read intervals into memory
                job.getLogger().info("using reference sample: " + referenceSample.getName());
                Map<String, MetricLine> referenceData = readDataToMemory(rawDataMap.get(referenceSample));

                //get read count for reference
                long refReadCount = getReadCount(referenceSample);
                job.getLogger().info("total reads in reference sample: " + refReadCount);

                //iterate original data and augment
                for (SequenceOutputFile outputFile : inputFiles)
                {
                    job.getLogger().info("generating reference-normalized data for: " + outputFile.getName());
                    File normalizedToReference = new File(outputDir, getBaseNameForFile(outputFile) + ".normalizedToSample.txt");
                    File rawData = rawDataMap.get(outputFile);
                    long sampleReadCount = getReadCount(outputFile);
                    job.getLogger().info("total reads in BAM: " + sampleReadCount);
                    generateNormalizedDataForSample(job, rawData, normalizedToReference, referenceData, refReadCount, sampleReadCount);
                    actionMap.get(outputFile).addOutput(getFinalFilename(normalizedToReference, settings), "Coverage Data - Normalized To Reference Sample", settings.deleteRawData());
                    rawDataFiles.add(normalizedToReference);
                    normalizedToSampleMap.put(outputFile, normalizedToReference);
                }
            }

            //step 5: generate graph/html
            boolean vertical = params.containsKey("orientation") && "vertical".equals(params.getString("orientation"));
            for (SequenceOutputFile outputFile : inputFiles)
            {
                job.getLogger().info("generating graphs for: " + outputFile.getName());

                File html = new File(outputDir, getBaseNameForFile(outputFile) + ".summary.html");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(html)))
                {
                    writer.write("<html><body><h2>" + outputFile.getName() + ":</h2>");
                    writer.write("This report contains multiple graphs showing coverage within your sample.  You can either scroll through the page or use the links below to jump to a given section.<p>");
                    writer.write("<table>");
                    writer.write("<tr><td><a href='#rawValues'>Section 1: Raw Data</a></td></tr>");
                    writer.write("<tr><td><a href='#normalizedToChromosome'>Section 2: Normalized To The Avg. Per Chromosome</a></td></tr>");

                    if (normalizedToSampleMap.containsKey(outputFile))
                    {
                        writer.write("<tr><td><a href='#normalizedToSample'>Section 3: Data Normalized to Another Sample</a></td></tr>");
                    }

                    String urlBase = AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/_webdav" + job.getContainer().getPath() + "/@files/sequenceOutputPipeline/" + outputDir.getName() + "/";

                    if (!settings.deleteRawData())
                    {
                        writer.write("<tr><td><a href='" + urlBase + windowStrategy.getWindowBedForOutput(outputFile).getName() + "'>Download Window Borders</a></td></tr>");
                    }
                    writer.write("</table><p><hr>");

                    //section 1: raw data
                    writer.write("<h3 id='rawValues'>Section 1: Raw Data</h3><p>");
                    if (!settings.deleteRawData())
                    {
                        writer.write("<a href='" + urlBase + rawDataMap.get(outputFile).getName() + "'>Download Data</a><p>");
                    }

                    for (MetricDescriptor m : getMetricDescriptors().values())
                    {
                        //run R, generate graph
                        File graph = new File(outputDir, getBaseNameForFile(outputFile) + "_" + m.getColumnHeader() + ".png");
                        runScript(job, graph, outputDir, rawDataMap.get(outputFile), m.getColumnHeader(), m.getLabel(), "Count", vertical);

                        String encoded = Base64.encodeBase64String(FileUtils.readFileToByteArray(graph));
                        writer.write("<h3 id=\"" + m.getColumnHeader() + "\">" + m.getLabel() + "<h3>");
                        writer.write("<img src=\"data:image/png;base64," + encoded + "\"/>");
                        writer.write("<br>");
                        graph.delete();
                    }
                    writer.write("<hr>");

                    //section 2: normalized
                    writer.write("<h3 id='normalizedToChromosome'>Section 2: Normalized To The Avg. Per Chromosome</h3><p>");
                    if (!settings.deleteRawData())
                    {
                        writer.write("<a href='" + urlBase + normalizedDataMap.get(outputFile).getName() + "'>Download Data</a><p>");
                    }

                    for (MetricDescriptor m : getMetricDescriptors().values())
                    {
                        String header = m.getColumnHeader() + "NormalizedToAvg";
                        File graph = new File(outputDir, getBaseNameForFile(outputFile) + "_" + header + ".png");
                        runScript(job, graph, outputDir, normalizedDataMap.get(outputFile), header, m.getLabel(), "Value", vertical);

                        String encoded = Base64.encodeBase64String(FileUtils.readFileToByteArray(graph));
                        writer.write("<h3 id=\"" + header + "\">" + m.getLabel() + ", Normalized To Average Over Chromosome</h3>");
                        writer.write("<img src=\"data:image/png;base64," + encoded + "\"/>");
                        graph.delete();

                        header = m.getColumnHeader() + "NormalizedToAvgWithCoverage";
                        graph = new File(outputDir, getBaseNameForFile(outputFile) + "_" + header + ".png");
                        runScript(job, graph, outputDir, normalizedDataMap.get(outputFile), header, m.getLabel(), "Value", vertical);

                        encoded = Base64.encodeBase64String(FileUtils.readFileToByteArray(graph));
                        writer.write("<h3 id=\"" + header + "\">" + m.getLabel() + ", Normalized To Average Over Chromosome (Only Including Windows With Coverage)</h3>");
                        writer.write("<img src=\"data:image/png;base64," + encoded + "\"/>");
                        graph.delete();
                    }

                    writer.write("<hr>");

                    //section 3: normlaized by sample
                    if (referenceSample != null)
                    {
                        writer.write("<h3 id='normalizedToSample'>Normalized to: " + referenceSample.getName() + "</h3><p>");
                        if (!settings.deleteRawData())
                        {
                            writer.write("<a href='" + urlBase + normalizedToSampleMap.get(outputFile).getName() + "'>Download Data</a><p>");
                        }

                        for (MetricDescriptor m : getMetricDescriptors().values())
                        {
                            //run R, generate graph
                            File graph = new File(outputDir, getBaseNameForFile(outputFile) + "_" + m.getColumnHeader() + ".png");
                            runScript(job, graph, outputDir, normalizedToSampleMap.get(outputFile), m.getColumnHeader(), m.getLabel(), "Value", vertical);

                            String encoded = Base64.encodeBase64String(FileUtils.readFileToByteArray(graph));
                            writer.write("<h3 id=\"" + m.getColumnHeader() + "\">" + m.getLabel() + ", Normalized To " + referenceSample.getName() + "<h3>");
                            writer.write("<img src=\"data:image/png;base64," + encoded + "\"/>");
                            graph.delete();
                        }

                        writer.write("<hr>");
                    }

                    writer.write("</body</html>");
                }

                SequenceOutputFile htmlOut = new SequenceOutputFile();
                htmlOut.setContainer(job.getContainerId());
                htmlOut.setCreated(new Date());
                htmlOut.setCreatedby(job.getUser().getUserId());
                htmlOut.setModified(new Date());
                htmlOut.setModifiedby(job.getUser().getUserId());

                ExpData htmlData = ExperimentService.get().createData(job.getContainer(), new DataType("Coverage Report"));
                htmlData.setDataFileURI(html.toURI());
                htmlData.setName(html.getName());
                htmlData.save(job.getUser());

                htmlOut.setName(html.getName());
                htmlOut.setDataId(htmlData.getRowId());
                htmlOut.setDescription("Summary report of coverage for the BAM: " + outputFile.getFile().getName());
                htmlOut.setCategory("Coverage");
                htmlOut.setLibrary_id(outputFile.getLibrary_id());
                htmlOut.setReadset(outputFile.getReadset());
                outputsToCreate.add(htmlOut);

                if (settings.deleteRawData())
                {
                    for (File f : rawDataFiles)
                    {
                        f.delete();
                    }
                }
                else
                {
                    for (File f : rawDataFiles)
                    {
                        Compress.compressGzip(f);
                        f.delete();
                    }
                }

                actionMap.get(outputFile).addOutput(html, "Coverage Data Summary", false);
            }

            for (RecordedAction action : actionMap.values())
            {
                action.setEndTime(new Date());
                actions.add(action);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        //throw new PipelineJobException("complete!");
    }

    private File getFinalFilename(File f, CoverageSettings settings)
    {
        return settings.deleteRawData() ? f : new File(f.getPath() + ".gz");
    }

    private long getReadCount(SequenceOutputFile outputFile)
    {
        long ret = 0;
        File bam = outputFile.getExpData().getFile();

        List<SamRecordFilter> filters = Arrays.asList(
                new AlignedFilter(true),
                new NotPrimaryAlignmentFilter()
        );

        try (SAMFileReader reader = new SAMFileReader(bam))
        {
            try (SAMRecordIterator it = reader.iterator())
            {
                while (it.hasNext())
                {
                    SAMRecord r = it.next();
                    for (SamRecordFilter f : filters)
                    {
                        if (!f.filterOut(r))
                        {
                            if (!r.getProperPairFlag() || !r.getSecondOfPairFlag())
                            {
                                ret++;
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    private SequenceOutputFile getSequenceOutputByRowId(int rowId, List<SequenceOutputFile> inputFiles)
    {
        for (SequenceOutputFile o : inputFiles)
        {
            if (o.getRowid() == rowId)
            {
                return o;
            }
        }

        return null;
    }

    private Map<String, MetricLine> readDataToMemory(File output) throws IOException
    {
        Map<String, MetricLine> ret = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(output)))
        {
            String line;
            while (null != (line = reader.readLine()))
            {
                //skip header
                if (!line.startsWith("SequenceName"))
                {
                    String[] split = StringUtils.split(line, '\t');
                    MetricLine m = new MetricLine(split);
                    ret.put(m.getKey(), m);
                }
            }
        }

        return ret;
    }

    private void generateNormalizedDataForSample(PipelineJob job, File rawData, File output, Map<String, MetricLine> referenceData, long refReadCount, long sampleReadCount) throws IOException
    {
        try (CSVReader reader = new CSVReader(new FileReader(rawData), '\t');CSVWriter outputWriter = new CSVWriter(new FileWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            String[] line;
            while (null != (line = reader.readNext()))
            {
                if (line[0].startsWith("SequenceName"))
                {
                    //just copy header
                    outputWriter.writeNext(line);
                }
                else
                {
                    MetricLine m = new MetricLine(line);
                    MetricLine ref = referenceData.get(m.getKey());
                    if (ref == null)
                    {
                        job.getLogger().error("Unable to find matching interval: " + m.getKey());
                    }
                    else
                    {
                        List<String> newRow = new ArrayList<>();
                        for (int idx = 0; idx < 3; idx++)
                        {
                            newRow.add(line[idx]);
                        }

                        for (int idx = 3; idx < line.length; idx++)
                        {
                            Double val = ConvertHelper.convert(line[idx], Double.class);
                            Double refVal = ref._values.get(idx);

                            if (refVal == null || refVal == 0)
                            {
                                newRow.add("NA");
                            }
                            else
                            {
                                newRow.add(String.valueOf((val / sampleReadCount) / (refVal / refReadCount)));
                            }
                        }

                        outputWriter.writeNext(newRow.toArray(new String[newRow.size()]));
                    }
                }
            }
        }
    }

    private class MetricLine
    {
        private String _sequenceName;
        private Integer _start;
        private Integer _stop;
        
        private Map<Integer, Double> _values = new HashMap<>();

        public MetricLine(String[] line)
        {
            _sequenceName = extractValue(line, 0, String.class);
            _start =  extractValue(line, 1, Integer.class);
            _stop = extractValue(line, 2, Integer.class);

            for (int idx = 3; idx < line.length; idx++)
            {
                _values.put(idx, extractValue(line, idx, Double.class));
            }            
        }

        private <T> T extractValue(String[] line, int rowIdx, Class<T> clazz)
        {
            if (line.length <= rowIdx)
            {
                return null;
            }

            return ConvertHelper.convert(line[rowIdx], clazz);            
        }

        public String getKey()
        {
            return _sequenceName + "||" + _start + "||" + _stop;
        }
    }

    private class CoverageTracker
    {
        Map<Integer, Double> columnSum = new TreeMap<>();
        Map<Integer, Double> columnSumWithoutZeros = new HashMap<>();
        int totalWindows = 0;
        int totalWindowsWithCoverage = 0;

        public CoverageTracker()
        {

        }
    }

    private void generateNormalizedDataForOutput(File originalRawData, File outputFile) throws IOException
    {
        //first calculate avg by column:
        Map<Integer, String> columnNameMap = new LinkedHashMap<>();
        Map<String, CoverageTracker> coverageTrackerMap = new HashMap<>();

        int lineNumber = 0;
        try (CSVReader reader = new CSVReader(new FileReader(originalRawData), '\t'))
        {
            String[] line;
            while ((line = reader.readNext()) != null)
            {
                lineNumber++;
                if (lineNumber == 1)
                {
                    int colIdx = 0;
                    for (String header : line)
                    {
                        columnNameMap.put(colIdx, header);
                        colIdx++;
                    }
                }
                else
                {
                    String refName = line[0];
                    if (!coverageTrackerMap.containsKey(refName))
                    {
                        coverageTrackerMap.put(refName, new CoverageTracker());
                    }

                    CoverageTracker tracker = coverageTrackerMap.get(refName);

                    Double distinctReads = Double.parseDouble(line[3]);
                    tracker.totalWindows++;
                    if (distinctReads > 0)
                    {
                        tracker.totalWindowsWithCoverage++;
                    }

                    for (int idx = 3; idx < line.length; idx++)
                    {
                        Double val = Double.parseDouble(line[idx]);
                        if (!tracker.columnSum.containsKey(idx))
                        {
                            tracker.columnSum.put(idx, 0.0);
                        }

                        tracker.columnSum.put(idx, tracker.columnSum.get(idx) + val);

                        if (distinctReads > 0)
                        {
                            if (!tracker.columnSumWithoutZeros.containsKey(idx))
                            {
                                tracker.columnSumWithoutZeros.put(idx, 0.0);
                            }

                            tracker.columnSumWithoutZeros.put(idx, tracker.columnSumWithoutZeros.get(idx) + val);
                        }
                    }
                }
            }
        }

        try (CSVReader reader = new CSVReader(new FileReader(originalRawData), '\t'); CSVWriter outputWriter = new CSVWriter(new FileWriter(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            String[] line;
            lineNumber = 0;
            while ((line = reader.readNext()) != null)
            {
                lineNumber++;
                if (lineNumber == 1)
                {
                    List<String> header = new ArrayList<>();
                    for (int idx = 0; idx < 3; idx++)
                    {
                        header.add(line[idx]);
                    }

                    //normalized
                    for (int idx = 3; idx < line.length; idx++)
                    {
                        header.add(line[idx] + "NormalizedToAvg");
                    }

                    //normalized without zeros
                    for (int idx = 3; idx < line.length; idx++)
                    {
                        header.add(line[idx] + "NormalizedToAvgWithCoverage");
                    }

                    outputWriter.writeNext(header.toArray(new String[header.size()]));
                }
                else
                {
                    String refName = line[0];
                    CoverageTracker tracker = coverageTrackerMap.get(refName);

                    List<String> data = new ArrayList<>();
                    for (int idx = 0; idx < 3; idx++)
                    {
                        data.add(line[idx]);
                    }

                    //normalized
                    for (int idx = 3; idx < line.length; idx++)
                    {
                        Double val = Double.parseDouble(line[idx]);
                        Double avg = tracker.columnSum.get(idx) / (double)tracker.totalWindows;

                        data.add(avg > 0.0 ? String.valueOf(val / avg) : "");
                    }

                    //normalized without zeros
                    for (int idx = 3; idx < line.length; idx++)
                    {
                        Double val = Double.parseDouble(line[idx]);
                        Double avg = tracker.totalWindowsWithCoverage == 0 ? 0.0 : tracker.columnSumWithoutZeros.get(idx) / (double)tracker.totalWindowsWithCoverage;

                        data.add(avg > 0 ? String.valueOf(val / avg) : "");
                    }

                    outputWriter.writeNext(data.toArray(new String[data.size()]));
                }
            }
        }
    }

    private void generateRawDataForOutput(File bam, List<Interval> intervalList, File outputFile) throws PipelineJobException
    {
        //TODO: SamReaderFactory fact = SamReaderFactory.makeDefault();
        try (SAMFileReader reader = new SAMFileReader(bam); CSVWriter outputWriter = new CSVWriter(new FileWriter(outputFile), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            outputWriter.writeNext(new String[]{
                    "SequenceName",
                    "Start",
                    "Stop",
                    "DistinctReads",
                    "PositionsOfNonZeroDepth",
                    "PositionsOfZeroDepth",
                    "AvgReadsPerPosition",
                    "AvgReadsPerCoveredPosition"
            });

            for (Interval i : intervalList)
            {
                IntervalList intervals = new IntervalList(reader.getFileHeader());
                intervals.add(i);
                try (SamLocusIterator sli = new SamLocusIterator(reader, intervals))
                {
                    sli.setEmitUncoveredLoci(false);
                    sli.setSamFilters(Arrays.asList(
                            new AlignedFilter(true),
                            new NotPrimaryAlignmentFilter()
                    ));

                    Iterator<SamLocusIterator.LocusInfo> iterator = sli.iterator();
                    Set<String> distinctReadsByInterval = new HashSet<>();
                    int[] totalReadsByPosition = new int[i.length()];
                    int idx = 0;
                    while (iterator.hasNext())
                    {
                        SamLocusIterator.LocusInfo l = iterator.next();
                        Set<String> distinctReadsByPosition = new HashSet<>();
                        for (SamLocusIterator.RecordAndOffset ro : l.getRecordAndPositions())
                        {
                            distinctReadsByPosition.add(ro.getRecord().getReadName());
                        }

                        totalReadsByPosition[idx] = distinctReadsByPosition.size();
                        distinctReadsByInterval.addAll(distinctReadsByPosition);
                        idx++;
                    }

                    int sumReadsPerPosition = 0;
                    int sumReadsPerPositionWithoutZeros = 0;
                    int nonZeroPositions = 0;
                    for (int val : totalReadsByPosition)
                    {
                        sumReadsPerPosition += val;
                        if (val != 0)
                        {
                            sumReadsPerPositionWithoutZeros += val;
                            nonZeroPositions++;
                        }
                    }

                    double avgReadsPerPosition = (double)sumReadsPerPosition / (double)i.length();
                    double avgReadsPerPositionWithoutZeros = nonZeroPositions == 0 ? 0.0 : (double)sumReadsPerPositionWithoutZeros / (double)nonZeroPositions;

                    outputWriter.writeNext(new String[]{
                            i.getSequence(),
                            String.valueOf(i.getStart()),
                            String.valueOf(i.getEnd()),
                            String.valueOf(distinctReadsByInterval.size()),
                            String.valueOf(nonZeroPositions),
                            String.valueOf(i.length() - nonZeroPositions),
                            String.valueOf(avgReadsPerPosition),
                            String.valueOf(avgReadsPerPositionWithoutZeros)
                    });
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private Map<String, MetricDescriptor> getMetricDescriptors()
    {
        Map<String, MetricDescriptor> ret = new LinkedHashMap<>();

        ret.put("totalReads", new MetricDescriptor("Total Reads / Window", "DistinctReads"));
        ret.put("avgDepth", new MetricDescriptor("Avg Depth / Position", "AvgReadsPerPosition"));
        ret.put("avgDepthIgnoreZero", new MetricDescriptor("Avg Depth / Position (Ignore Positions w/ Zero Coverage)", "AvgReadsPerCoveredPosition"));
        ret.put("positionsOfZeroDepth", new MetricDescriptor("Positions of Zero Depth", "PositionsOfZeroDepth"));
        ret.put("positionsOfNonZeroDepth", new MetricDescriptor("Positions of Non-Zero Depth", "PositionsOfNonZeroDepth"));

        return ret;
    }

    private class CoverageSettings
    {
        private JSONObject _json;

        public CoverageSettings(JSONObject params)
        {
            if (params == null || params.isEmpty())
            {
                throw new ConfigurationException("No parameters provided");
            }

            _json = params;
        }

        public WindowStrategy getWindowStrategy()
        {
            return new WindowStrategy(this);
        }

        public boolean deleteRawData()
        {
            return _json.get("deleteRawData") == null ? true : _json.getBoolean("deleteRawData");
        }

        public List<String> getMetricNames()
        {
            List<String> metricNames;
            if (_json.get("metric") == null)
            {
                throw new ConfigurationException("No metic names provided");
            }
            else if (_json.get("metric") instanceof String)
            {
                metricNames = Arrays.asList(_json.getString("metric"));
            }
            else
            {
                metricNames = new ArrayList<>();
                for (Object o : _json.getJSONArray("metric").toArray())
                {
                    metricNames.add(o.toString());
                }
            }

            return metricNames;
        }

        public JSONObject getJson()
        {
            return _json;
        }
    }

    public class MetricDescriptor
    {
        private String _label;
        private String _columnHeader;

        public MetricDescriptor()
        {

        }

        public MetricDescriptor(String label, String columnHeader)
        {
            _label = label;
            _columnHeader = columnHeader;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getColumnHeader()
        {
            return _columnHeader;
        }

        public void setColumnHeader(String columnHeader)
        {
            _columnHeader = columnHeader;
        }
    }

    private String getBaseNameForFile(SequenceOutputFile o)
    {
        return o.getRowid() + "_" + FileUtil.getBaseName(o.getFile());
    }

    private class WindowStrategy
    {
        //uses the RowId of the SequenceOutputFile
        private Map<Integer, File> _fileMap = new HashMap<>();
        private CoverageSettings _settings;

        public WindowStrategy(CoverageSettings settings)
        {
            _settings = settings;
        }

        public void generateWindows(List<SequenceOutputFile> outputFiles, File outputDir) throws IOException, PipelineJobException
        {
            if (doCalculateWindowsPerSample())
            {
                for (SequenceOutputFile outputFile : outputFiles)
                {
                    calculateIntervalsForFile(outputFile, outputDir);
                }
            }
            else
            {
                Integer rowId = getReferenceSampleForWindows();
                calculateIntervalsForFile(getSequenceOutputByRowId(rowId, outputFiles), outputDir);
            }
        }

        private void calculateIntervalsForFile(SequenceOutputFile o, File outputDir) throws IOException, PipelineJobException
        {
            File output = new File(outputDir, getBaseNameForFile(o) + ".windows.bed");
            try (CSVWriter writer = new CSVWriter(new FileWriter(output), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                if ("fixedWidth".equals(_settings.getJson().getString("windowStrategy")))
                {
                    int basesPerWindow = _settings.getJson().getInt("basesPerWindow");

                    SamReaderFactory fact = SamReaderFactory.makeDefault();
                    try (SamReader reader = fact.open(o.getFile()))
                    {
                        SAMSequenceDictionary dict = reader.getFileHeader().getSequenceDictionary();
                        for (SAMSequenceRecord sr : dict.getSequences())
                        {
                            int windowStart = 0;  //zero-based
                            while (windowStart < sr.getSequenceLength())
                            {
                                int windowEnd = Math.min(sr.getSequenceLength(), windowStart + basesPerWindow);  //1-based for BED
                                writer.writeNext(new String[]{sr.getSequenceName(), String.valueOf(windowStart), String.valueOf(windowEnd)});
                                windowStart = windowEnd;
                            }
                        }
                    }
                }
                else if ("fixedReadNumber".equals(_settings.getJson().getString("windowStrategy")))
                {
                    int readNumberPerWindow = _settings.getJson().getInt("readNumberPerWindow");
                    //zero-based
                    int windowStart = 0;
                    int lastReadStart = 0;
                    String refName = null;

                    // We make the assumption this BAM is sorted.
                    // TODO: this is probably not a safe assumption
                    //SamReaderFactory fact = SamReaderFactory.makeDefault();
                    try (SAMFileReader reader = new SAMFileReader(o.getFile()))
                    {
                        SAMFileHeader header = reader.getFileHeader();
                        Set<String> encounteredReadNames = new HashSet<>(readNumberPerWindow);
                        try (PeekableIterator<SAMRecord> i = getIterator(reader.iterator()))
                        {
                            while (i.hasNext())
                            {
                                SAMRecord r = i.next();

                                //if we switch reference, restart
                                if (refName != null && !refName.equals(r.getReferenceName()))
                                {
                                    writer.writeNext(new String[]{refName, String.valueOf(windowStart), String.valueOf(header.getSequence(refName).getSequenceLength())});  //end is 1-based
                                    windowStart = 0;  //always use sequence start
                                    encounteredReadNames.clear();
                                }

                                encounteredReadNames.add(r.getReadName());
                                if (encounteredReadNames.size() % readNumberPerWindow == 0)
                                {
                                    if (windowStart != r.getAlignmentStart())
                                    {
                                        writer.writeNext(new String[]{r.getReferenceName(), String.valueOf(windowStart), String.valueOf(r.getAlignmentStart())});  //end is 1-based
                                    }
                                    else
                                    {
                                        int x = 1 + 1;
                                        x++;
                                    }

                                    windowStart = r.getAlignmentStart();  //start of the next window, 0-based
                                    encounteredReadNames.clear();
                                }

                                refName = r.getReferenceName();
                                lastReadStart = r.getAlignmentStart();
                            }
                        }

                        if (!encounteredReadNames.isEmpty() && refName != null)
                        {
                            writer.writeNext(new String[]{refName, String.valueOf(windowStart), String.valueOf(lastReadStart)});  //end is 1-based
                            encounteredReadNames.clear();
                        }
                    }
                }
                else
                {
                    throw new PipelineJobException("Unknown window strategy: " + _settings.getJson().getString("windowStrategy"));
                }
            }

            _fileMap.put(o.getRowid(), output);
        }

        private CoverageSettings getSettings()
        {
            if (_settings == null)
                throw new IllegalArgumentException("CoverageSetting has not been set");

            return _settings;
        }

        private boolean doCalculateWindowsPerSample()
        {
            return !"relative".equals(getSettings().getJson().getString("sampleHandling"));
        }

        private Integer getReferenceSampleForWindows()
        {
            if (!getSettings().getJson().has("referenceSample"))
            {
                return null;
            }

            Integer referenceSample = getSettings().getJson().getInt("referenceSample");

            return referenceSample;
        }

        public File getWindowBedForOutput(SequenceOutputFile outputFile)
        {
            if (doCalculateWindowsPerSample())
            {
                return _fileMap.get(outputFile.getRowid());
            }
            else
            {
                return _fileMap.get(getReferenceSampleForWindows());
            }
        }
    }

    public PeekableIterator<SAMRecord> getIterator(SAMRecordIterator tempIterator) {
        return new PeekableIterator<>(new FilteringIterator(tempIterator, new AggregateFilter(Arrays.asList(
                new AlignedFilter(true),
                new SecondaryOrSupplementaryFilter(),
                new DuplicateReadFilter()
        ))));
    }

    private void runScript(PipelineJob job, File outputFile, File workDir, File rawData, String colHeader, String title, String yLabel, boolean vertical) throws PipelineJobException
    {
        job.getLogger().info("Preparing to run R script");

        String exePath = new File(inferRPath(), "Rscript").getPath();

        List<String> args = new ArrayList<>();
        args.add(exePath);
        args.add("--vanilla");
        args.add(getScriptPath());
        args.add("-i");
        args.add(rawData.getPath());
        args.add("-o");
        args.add(outputFile.getPath());
        args.add("-c");
        args.add(colHeader);
        args.add("-t");
        args.add(title);
        args.add("--yLabel");
        args.add(yLabel);
        if (vertical)
        {
            args.add("-v");
            args.add("1");
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        job.runSubProcess(pb, workDir);
    }

    private String inferRPath()
    {
        //preferentially use R config setup in scripting props.  only works if running locally.
        for (ExternalScriptEngineDefinition def : LabkeyScriptEngineManager.getEngineDefinitions())
        {
            if (RScriptEngineFactory.isRScriptEngine(def.getExtensions()))
            {
                return new File(def.getExePath()).getParent();
            }
        }

        //then RHOME
        Map<String, String> env = System.getenv();
        if (env.containsKey("RHOME"))
        {
            return env.get("RHOME");
        }

        return null;
    }

    private String getScriptPath() throws PipelineJobException
    {
        Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);
        Resource script = module.getModuleResource("/external/coverageGraph.r");
        if (!script.exists())
            throw new PipelineJobException("Unable to find file: " + script.getPath());

        File f = ((FileResource) script).getFile();
        if (!f.exists())
            throw new PipelineJobException("Unable to find file: " + f.getPath());

        return f.getPath();
    }
}
