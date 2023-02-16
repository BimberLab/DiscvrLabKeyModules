/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.pipeline;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.variant.utils.SAMSequenceDictionaryExtractor;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.sequenceanalysis.GenomeTrigger;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenomeManager;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.Job;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.SequenceAnalysisServiceImpl;
import org.labkey.sequenceanalysis.run.util.GxfSorter;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bbimber
 * Date: 8/6/12
 * Time: 12:57 PM
 */
public class ImportGenomeTrackTask extends PipelineJob.Task<ImportGenomeTrackTask.Factory>
{
    private static final String ACTION_NAME = "Import Genome Tracks";

    protected ImportGenomeTrackTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(ImportGenomeTrackTask.class);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return List.of(ACTION_NAME);
        }

        @Override
        public PipelineJob.Task<?> createTask(PipelineJob job)
        {
            return new ImportGenomeTrackTask(this, job);
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        getJob().getLogger().info("Importing tracks from file(s): ");
        RecordedAction action = new RecordedAction(ACTION_NAME);

        try
        {
            final int libraryId = getPipelineJob().getLibraryId();
            final Container genomeContainer = ReferenceGenomeImpl.getFolderForGenome(libraryId);
            if (!genomeContainer.hasPermission(getJob().getUser(), InsertPermission.class))
            {
                throw new UnauthorizedException("User cannot does not have update permission in folder: " + genomeContainer.getPath());
            }

            final int trackId = addTrackForLibrary(getPipelineJob().getTrack(), getPipelineJob().getTrackName(), getPipelineJob().getTrackDescription(), action);
            ReferenceGenomeManager.get().markGenomeModified(SequenceAnalysisService.get().getReferenceGenome(getPipelineJob().getLibraryId(), getJob().getUser()), getJob().getLogger());

            Set<GenomeTrigger> triggers = SequenceAnalysisServiceImpl.get().getGenomeTriggers();
            if (!triggers.isEmpty())
            {
                JobRunner jr = JobRunner.getDefault();
                for (final GenomeTrigger t : triggers)
                {
                    if (t.isAvailable(genomeContainer))
                    {
                        getJob().getLogger().info("running genome trigger: " + t.getName());
                        jr.execute(new Job()
                        {
                            @Override
                            public void run()
                            {
                                t.onTrackAdd(genomeContainer, getJob().getUser(), getJob().getLogger(), libraryId, trackId);
                            }
                        });
                    }
                }

                jr.waitForCompletion();
            }

        }
        catch (Exception e)
        {
            throw new PipelineJobException(e);
        }

        return new RecordedActionSet(action);
    }

    private int addTrackForLibrary(File file, String trackName, String trackDescription, RecordedAction action) throws Exception
    {
        ReferenceGenome genome = SequenceAnalysisService.get().getReferenceGenome(getPipelineJob().getLibraryId(), getJob().getUser());
        if (genome == null)
        {
            throw new PipelineJobException("Unable to find genome: " + getPipelineJob().getLibraryId());
        }

        File fasta = genome.getSourceFastaFile();
        if (!fasta.exists())
        {
            throw new PipelineJobException("Unable to find FASTA: " + fasta.getPath());
        }

        File targetDir = fasta.getParentFile();
        if (!targetDir.exists())
        {
            throw new IllegalArgumentException("Unable to find expected FASTA location: " + targetDir.getPath());
        }

        File tracksDir = new File(targetDir, "tracks");
        if (!tracksDir.exists())
        {
            tracksDir.mkdirs();
        }

        action.addInput(file, "Original Track");

        //determine if we need to translate this file
        //also validate against known tracks
        List<Integer> refNtIds = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS), PageFlowUtil.set("ref_nt_id"), new SimpleFilter(FieldKey.fromString("library_id"), getPipelineJob().getLibraryId()), null).getArrayList(Integer.class);
        List<RefNtSequenceModel> refNtModels = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), new SimpleFilter(FieldKey.fromString("rowid"), refNtIds, CompareType.IN), null).getArrayList(RefNtSequenceModel.class);
        Map<String, Pair<String, Integer>> nameTranslationMap = getNameTranslationMap(refNtModels);

        File outputFile = getOutputFile(file, tracksDir);
        action.addOutput(file, "Transformed Track", false);

        getJob().getLogger().info("input track: " + file.getPath());
        getJob().getLogger().info("writing output to: "+ outputFile.getPath());

        Map<String, String> knownChrs = new CaseInsensitiveHashMap<>();
        for (RefNtSequenceModel m : refNtModels)
        {
            knownChrs.put(m.getName(), m.getName());
        }

        SAMSequenceDictionary dict = null;
        if (!genome.getSequenceDictionary().exists())
        {
            SequencePipelineService.get().ensureSequenceDictionaryExists(genome.getWorkingFastaFile(), getJob().getLogger(), false);
        }

        dict = SAMSequenceDictionaryExtractor.extractDictionary(genome.getSequenceDictionary().toPath());

        if (SequenceUtil.FILETYPE.bed.getFileType().isType(file))
        {
            validateAndTransformTsv(file, outputFile, nameTranslationMap, knownChrs, 0, 1, 2, -1, dict, action);
            if (SystemUtils.IS_OS_WINDOWS)
            {
                getJob().getLogger().warn("unable to sort BED file on windows, skipping");
            }
            else
            {
                SequencePipelineService.get().sortROD(outputFile, getJob().getLogger(), 2);
            }
        }
        else if (SequenceUtil.FILETYPE.gff.getFileType().isType(file) || SequenceUtil.FILETYPE.gtf.getFileType().isType(file))
        {
            long count1 = SequenceUtil.getLineCount(file);
            getJob().getLogger().info("total lines in input: " + count1);

            validateAndTransformTsv(file, outputFile, nameTranslationMap, knownChrs, 0, 3, 4, 0, dict, action);
            sortGxf(outputFile);

            long count2 = SequenceUtil.getLineCount(outputFile);
            getJob().getLogger().info("total lines in sorted output: " + count2);
            getJob().getLogger().info("difference: " + (count1 - count2));
        }
        else if (SequenceUtil.FILETYPE.vcf.getFileType().isType(file))
        {
            validateAndTransformVcf(file, outputFile, nameTranslationMap, knownChrs, dict, action);

            if (getPipelineJob().doChrTranslation())
            {
                File sorted = new File(getPipelineJob().getOutDir(), "tmpSorted.vcf" + (outputFile.getName().toLowerCase().endsWith(".gz") ? ".gz" : ""));
                SequencePipelineService.get().sortVcf(outputFile, sorted, genome.getSequenceDictionary(), getJob().getLogger());
                if (sorted.exists())
                {
                    for (String suffix : Arrays.asList(".tbi", "idx"))
                    {
                        File origIdx = new File(outputFile.getPath() + suffix);
                        File newIdx = new File(sorted.getPath() + suffix);
                        if (origIdx.exists())
                        {
                            origIdx.delete();
                        }

                        if (newIdx.exists())
                        {
                            FileUtils.moveFile(newIdx, origIdx);
                        }
                    }

                    outputFile.delete();
                    FileUtils.moveFile(sorted, outputFile);
                }
            }
            else
            {
                getJob().getLogger().info("skipping VCF sort");
                SequenceAnalysisService.get().ensureVcfIndex(file, getJob().getLogger());
            }
        }
        else if (SequenceUtil.FILETYPE.gbk.getFileType().isType(file))
        {
            getJob().getLogger().debug("no processing needed: " + file.getName());
            FileUtils.moveFile(file, outputFile);
        }
        else if (SequenceUtil.FILETYPE.bw.getFileType().isType(file))
        {
            getJob().getLogger().debug("no processing needed: " + file.getName());
            FileUtils.moveFile(file, outputFile);
        }
        else
        {
            throw new PipelineJobException("Unsupported filetype: " + file.getName());
        }

        Container genomeContainer = ReferenceGenomeImpl.getFolderForGenome(getPipelineJob().getLibraryId());

        ExpData trackData = ExperimentService.get().createData(genomeContainer, new DataType("Sequence Track"));
        trackData.setName(outputFile.getName());
        trackData.setDataFileURI(outputFile.toURI());
        trackData.save(getJob().getUser());
        //create row
        CaseInsensitiveHashMap<Object> map = new CaseInsensitiveHashMap<>();
        map.put("name", trackName);
        map.put("description", trackDescription);
        map.put("library_id", getPipelineJob().getLibraryId());
        map.put("container", genomeContainer);
        map.put("created", new Date());
        map.put("createdby", getJob().getUser().getUserId());
        map.put("modified", new Date());
        map.put("modifiedby", getJob().getUser().getUserId());
        map.put("fileid", trackData.getRowId());
        Integer jobId = PipelineService.get().getJobId(getJob().getUser(), genomeContainer, getJob().getJobGUID());
        map.put("jobId", jobId);

        TableInfo trackTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS);
        map = Table.insert(getJob().getUser(), trackTable, map);
        if (map.get("rowid") == null)
        {
            throw new PipelineJobException("Unable to find rowid for new track");
        }

        return (Integer)map.get("rowid");
    }

    private void sortGxf(File gxf) throws PipelineJobException
    {
        getJob().getLogger().info("sorting file: " + gxf.getPath());
        if (SystemUtils.IS_OS_WINDOWS)
        {
            getJob().getLogger().warn("unable to sort files on windows, skipping");
            return;
        }

        new GxfSorter(getJob().getLogger()).sortGxf(gxf, null);
    }
    private File getOutputFile(File file, File tracksDir)
    {
        return AssayFileWriter.findUniqueFileName(file.getName().replaceAll(" ", "_"), tracksDir);
    }

    private Map<String, Pair<String, Integer>> getNameTranslationMap(List<RefNtSequenceModel> refNtSequenceModels) throws PipelineJobException
    {
        if (getPipelineJob().doChrTranslation())
            getJob().getLogger().info("will translate between common forms of chromosome names, like chr01, chr1 and 1");
        else
            return Collections.emptyMap();

        getJob().getLogger().info("building list of chromosome name translations");
        Map<String, Pair<String, Integer>> ret = new CaseInsensitiveHashMap<>();

        TableInfo rlm = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS);
        for (RefNtSequenceModel m : refNtSequenceModels)
        {
            if (m.getOffsetsFile() != null && m.getOffsetsFile().exists())
            {
                try (CSVReader reader = new CSVReader(Readers.getReader(m.getOffsetsFile()), '\t'))
                {
                    String[] line;
                    while ((line = reader.readNext()) != null)
                    {
                        //rowId, name, offset, genbank, refSeqId
                        Integer offset = Integer.parseInt(line[2]);
                        ret.put(line[1], Pair.of(m.getName(), offset));
                        
                        if (line.length > 3 && StringUtils.trimToNull(line[3]) != null)
                        {
                            ret.put(line[3], Pair.of(m.getName(), offset));
                        }

                        if (line.length > 4 && StringUtils.trimToNull(line[4]) != null)
                        {
                            ret.put(line[4], Pair.of(m.getName(), offset));
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                if (m.getGenbank() != null)
                    ret.put(m.getGenbank(), Pair.of(m.getName(), 0));

                if (m.getRefSeqId() != null)
                    ret.put(m.getRefSeqId(), Pair.of(m.getName(), 0));

                if (getPipelineJob().doChrTranslation() && m.getName().toLowerCase().startsWith("chr"))
                {
                    try
                    {
                        String strPart = m.getName().toLowerCase().replaceAll("^chr", "");
                        Integer number = Integer.parseInt(strPart);
                        ret.put(number.toString(), Pair.of(m.getName(), 0));

                        if (strPart.length() != number.toString().length())
                        {
                            for (String t : Arrays.asList(number.toString(), StringUtils.leftPad(number.toString(), 2, "0")))
                            {
                                String i = "chr" + t;
                                if (!i.equalsIgnoreCase(m.getName()))
                                {
                                    ret.put(i, Pair.of(m.getName(), 0));
                                }
                            }
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        //ignore
                    }
                }

                if (getPipelineJob().doChrTranslation())
                {
                    try
                    {
                        Integer number = Integer.parseInt(m.getName());
                        for (String t : Arrays.asList(number.toString(), StringUtils.leftPad(number.toString(), 2, "0")))
                        {
                            String i = "chr" + t;
                            ret.put(i, Pair.of(m.getName(), 0));
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        //ignore
                    }

                    if ("X".equalsIgnoreCase(m.getName()) || "Y".equalsIgnoreCase(m.getName()))
                    {
                        ret.put("chr" + m.getName(), Pair.of(m.getName(), 0));
                    }

                    if ("chrX".equalsIgnoreCase(m.getName()) || "chrY".equalsIgnoreCase(m.getName()))
                    {
                        String strPart = m.getName().toLowerCase().replaceAll("^chr", "");
                        ret.put(strPart, Pair.of(m.getName(), 0));
                    }

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromString("library_id"), getPipelineJob().getLibraryId());
                    filter.addCondition(FieldKey.fromString("ref_nt_id"), m.getRowid());
                    TableSelector ts = new TableSelector(rlm, PageFlowUtil.set("alias"), filter, null);
                    if (ts.exists())
                    {
                        String alias = ts.getObject(String.class);
                        if (alias != null)
                        {
                            ret.put(alias, Pair.of(m.getName(), 0));
                        }
                    }
                }
            }
        }

        getJob().getLogger().info("a total of " + ret.size() + " possible name or offset translations were found");

        return ret;
    }

    private Pair<String, Integer> getOffset(String name, Map<String, Pair<String, Integer>> nameTranslationMap)
    {
        if (nameTranslationMap.containsKey(name))
        {
            return nameTranslationMap.get(name);
        }

        return null;
    }

    private void validateAndTransformTsv(File file, File out, Map<String, Pair<String, Integer>> nameTranslationMap, Map<String, String> knownChrs, int colChr, int colStart, int colEnd, int startOffset, @Nullable SAMSequenceDictionary dict, RecordedAction action) throws PipelineJobException
    {
        Map<String, Integer> warnedChrs = new CaseInsensitiveHashMap<>();
        getJob().getLogger().info("validating file: " + file.getPath());

        File outLog = new File(getPipelineJob().getOutDir(), "translations.txt");
        if (getPipelineJob().doChrTranslation())
        {
            action.addOutput(outLog, "Transformed Lines", false);
            getJob().getLogger().info("writing log of translation to : " + outLog.getPath());
        }

        try (CSVReader reader = new CSVReader(IOUtil.openFileForBufferedReading(file), '\t', CSVWriter.NO_QUOTE_CHARACTER); CSVWriter writer = new CSVWriter(IOUtil.openFileForBufferedUtf8Writing(out), '\t', CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER);CSVWriter logWriter = new CSVWriter(PrintWriters.getPrintWriter(outLog), '\t', CSVWriter.NO_QUOTE_CHARACTER))
        {
            if (getPipelineJob().doChrTranslation())
            {
                logWriter.writeNext(new String[]{"LineNo", "OrigContig", "OrigStart", "OrigEnd", "NewContig", "Offset"});
            }

            String[] line;
            int lineNo = 0;
            int commentNo = 0;
            int totalShifted = 0;
            int totalRenamed = 0;

            while ((line = reader.readNext()) != null)
            {
                lineNo++;
                if (line[0].startsWith("#"))
                {
                    commentNo++;
                    writer.writeNext(line);
                    continue;
                }

                if (lineNo % 100000 == 0)
                {
                    getJob().getLogger().info("processed " + lineNo + " records");
                }

                if (getPipelineJob().doChrTranslation())
                {
                    Pair<String, Integer> translation = getOffset(line[colChr], nameTranslationMap);
                    if (translation != null)
                    {
                        if (translation.second > 0)
                            totalShifted++;

                        if (!line[colChr].equalsIgnoreCase(translation.first))
                            totalRenamed++;

                        logWriter.writeNext(new String[]{String.valueOf(lineNo), line[colChr], line[colStart], line[colEnd], translation.first, translation.second.toString()});
                        line[colChr] = translation.first;
                        line[colStart] = String.valueOf(Integer.parseInt(line[colStart]) + translation.second + startOffset);
                        line[colEnd] = String.valueOf(Integer.parseInt(line[colEnd]) + translation.second);
                    }
                }

                if (!knownChrs.containsKey(line[colChr]))
                {
                    if (!warnedChrs.containsKey(line[colChr]))
                    {
                        getPipelineJob().getLogger().warn("Unknown chromosome on line: " + lineNo + ", " + line[colChr]);
                        warnedChrs.put(line[colChr], 0);
                    }

                    warnedChrs.put(line[colChr], 1 + warnedChrs.get(line[colChr]));
                    continue;
                }
                else
                {
                    //ensure correct case
                    line[colChr] = knownChrs.get(line[colChr]);
                }

                if (dict != null)
                {
                    SAMSequenceRecord r = dict.getSequence(line[colChr]);
                    if (r == null)
                    {
                        throw new PipelineJobException("Unable to find sequence in dictionary: " + line[colChr]);
                    }

                    if (Integer.parseInt(line[colEnd]) > r.getSequenceLength())
                    {
                        getJob().getLogger().error("feature extended beyond contig end at line: " + lineNo + ", " + line[colChr] + ": " + line[colStart] + "-" + line[colEnd]);
                    }
                }

                writer.writeNext(line);
            }

            getJob().getLogger().info("total lines inspected from input: " + lineNo);
            getJob().getLogger().info("total comment lines inspected: " + commentNo);
            getJob().getLogger().info("total features renamed: " + totalRenamed);
            getJob().getLogger().info("total features with coordinates transformed: " + totalShifted);
            if (!warnedChrs.isEmpty())
            {
                getJob().getLogger().info("features skipped: ");
                for (String chr : warnedChrs.keySet())
                {
                    getJob().getLogger().info(chr + ": " + warnedChrs.get(chr));
                }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }

        if (!getPipelineJob().doChrTranslation())
        {
            outLog.delete();
        }

        long count = SequenceUtil.getLineCount(out);
        getJob().getLogger().info("total lines in output: " + count);
    }

    private void validateAndTransformVcf(File file, File out, Map<String, Pair<String, Integer>> nameTranslationMap, Map<String, String> knownChrs, SAMSequenceDictionary dict, RecordedAction action) throws PipelineJobException
    {
        getJob().getLogger().info("validating file: " + file.getPath());

        if (dict == null)
        {
            throw new PipelineJobException("Cannot import VCF tracks without a sequence dictionary");
        }

        try
        {
            SequenceAnalysisService.get().ensureVcfIndex(file, getPipelineJob().getLogger());

            Map<String, Integer> warnedChrs = new CaseInsensitiveHashMap<>();
            File outLog = new File(getPipelineJob().getOutDir(), "translations.txt");
            if (getPipelineJob().doChrTranslation())
            {
                action.addOutput(outLog, "Transformed Lines", false);
                getJob().getLogger().info("writing log of translation to : " + outLog.getPath());
            }

            VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
            builder.setOutputFile(out);
            builder.setOption(Options.USE_ASYNC_IO);
            builder.setReferenceDictionary(dict);

            try (VCFFileReader reader = new VCFFileReader(file); VariantContextWriter writer = builder.build(); CSVWriter logWriter = new CSVWriter(PrintWriters.getPrintWriter(outLog), '\t', CSVWriter.NO_QUOTE_CHARACTER))
            {
                VCFHeader header = reader.getFileHeader();
                header.setSequenceDictionary(dict);
                writer.writeHeader(header);

                if (getPipelineJob().doChrTranslation())
                {
                    logWriter.writeNext(new String[]{"LineNo", "OrigContig", "OrigStart", "OrigEnd", "NewContig", "Offset"});
                }

                int lineNo = 0;
                int totalShifted = 0;
                int totalRenamed = 0;

                try (CloseableIterator<VariantContext> it = reader.iterator())
                {
                    while (it.hasNext())
                    {
                        lineNo++;
                        if (lineNo % 100000 == 0)
                        {
                            getJob().getLogger().info("processed " + lineNo + " records");
                        }

                        VariantContext vc = it.next();

                        VariantContextBuilder vcb = new VariantContextBuilder(vc);

                        if (getPipelineJob().doChrTranslation())
                        {
                            Pair<String, Integer> translation = getOffset(vc.getContig(), nameTranslationMap);
                            if (translation != null)
                            {
                                if (translation.second > 0)
                                    totalShifted++;

                                if (!vc.getContig().equalsIgnoreCase(translation.first))
                                    totalRenamed++;

                                logWriter.writeNext(new String[]{String.valueOf(lineNo), vc.getContig(), String.valueOf(vc.getStart()), String.valueOf(vc.getEnd()), translation.first, translation.second.toString()});

                                vcb.chr(translation.first);
                                vcb.start(vc.getStart() + translation.second);
                                vcb.stop(vc.getEnd() + translation.second);
                            }
                        }

                        if (!knownChrs.containsKey(vc.getContig()))
                        {
                            if (!warnedChrs.containsKey(vc.getContig()))
                            {
                                getPipelineJob().getLogger().warn("Unknown chromosome on line: " + lineNo + ", " + vc.getContig());
                                warnedChrs.put(vc.getContig(), 0);
                            }

                            warnedChrs.put(vc.getContig(), 1 + warnedChrs.get(vc.getContig()));
                            continue;
                        }
                        else
                        {
                            //ensure correct case
                            vcb.chr(knownChrs.get(vc.getContig()));
                        }
                        vc = vcb.make();

                        SAMSequenceRecord r = dict.getSequence(vc.getContig());
                        if (vc.getEnd() > r.getSequenceLength())
                        {
                            getJob().getLogger().error("feature extended beyond contig end at line: " + lineNo + ", " + vc.getContig() + ": " + vc.getStart() + "-" + vc.getEnd());
                        }

                        writer.add(vc);
                    }
                }

                getJob().getLogger().info("total lines inspected: " + lineNo);
                getJob().getLogger().info("total features renamed: " + totalRenamed);
                getJob().getLogger().info("total features with coordinates transformed: " + totalShifted);
                if (!warnedChrs.isEmpty())
                {
                    getJob().getLogger().info("features skipped: ");
                    for (String chr : warnedChrs.keySet())
                    {
                        getJob().getLogger().info(chr + ": " + warnedChrs.get(chr));
                    }
                }
            }

            if (!getPipelineJob().doChrTranslation())
            {
                outLog.delete();
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private ImportGenomeTrackPipelineJob getPipelineJob()
    {
        return (ImportGenomeTrackPipelineJob)getJob();
    }
}
