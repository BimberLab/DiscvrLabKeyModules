package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.pipeline.CacheGenomeTrigger;
import org.labkey.sequenceanalysis.pipeline.ReferenceGenomeImpl;
import org.labkey.sequenceanalysis.run.util.FastaIndexer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by bimber on 9/15/2014.
 */
public class SequenceAnalysisMaintenanceTask implements MaintenanceTask
{
    public SequenceAnalysisMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "Delete SequenceAnalysis Artifacts";
    }

    @Override
    public String getName()
    {
        return "DeleteSequenceAnalysisArtifacts";
    }

    @Override
    public void run(Logger log)
    {
        //delete sequence text files and library artifacts not associated with a DB record
        try
        {
            possiblySubmitRemoteTask(log);

            processContainer(ContainerManager.getRoot(), log);
            verifySequenceDataPresent(log);
        }
        catch (Exception e)
        {
            log.error(e.getMessage(), e);
        }
    }

    private void possiblySubmitRemoteTask(Logger log)
    {
        if (SequencePipelineService.get().isRemoteGenomeCacheUsed())
        {
            JobRunner jr = JobRunner.getDefault();
            jr.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        Map<Integer, File> genomeMap = new HashMap<>();
                        new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("rowid", "fasta_file"), new SimpleFilter(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK), null).forEachResults(rs -> {
                            int dataId = rs.getInt(FieldKey.fromString("fasta_file"));
                            if (dataId > -1)
                            {
                                ExpData d = ExperimentService.get().getExpData(dataId);
                                if (d != null && d.getFile() != null)
                                {
                                    genomeMap.put(rs.getInt(FieldKey.fromString("rowid")), d.getFile());
                                }
                            }
                        });

                        if (!genomeMap.isEmpty())
                        {
                            final User adminUser = LDKService.get().getBackgroundAdminUser();
                            if (adminUser == null)
                            {
                                log.error("LDK module BackgroundAdminUser property not set.  If this is set, JBrowseMaintenanceTask could automatically submit repair jobs.");
                                return;
                            }

                            CacheGenomeTrigger.cacheGenomes(ContainerManager.getSharedContainer(), adminUser, genomeMap, log, true);
                        }
                    }
                    catch (Exception e)
                    {
                        log.error(e);
                    }
                }
            });

            jr.waitForCompletion();
        }
        else
        {
            log.debug("Genome caching not used, skipping");
        }
    }

    private void verifySequenceDataPresent(Logger log)
    {
        log.info("verifying sequence data files present");
        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);
        TableSelector ts = new TableSelector(ti, null, new Sort("container"));

        log.info("Inspecting ReadData");
        List<ReadDataImpl> readDatas = ts.getArrayList(ReadDataImpl.class);
        int i = 0;
        for (ReadDataImpl rd : readDatas)
        {
            i++;
            if (i % 1000 == 0)
            {
                log.info("readdata " + i + " of " + readDatas.size() + ". Current container: " + ContainerManager.getForId(rd.getContainer()).getPath());
            }

            if (rd.getFileId1() != null)
            {
                ExpData d = ExperimentService.get().getExpData(rd.getFileId1());
                Container c = ContainerManager.getForId(rd.getContainer());
                if (!rd.isArchived())
                {
                    if (d == null || d.getFile() == null)
                    {
                        log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId1() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                    else if (!d.getFile().exists())
                    {
                        log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId1() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                }
                else
                {
                    if (d != null && d.getFile() != null && d.getFile().exists())
                    {
                        log.error("ReadData marked as archived, but file exists: " + rd.getRowid() + ", " + rd.getFileId1() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                }
            }

            if (rd.getFileId2() != null)
            {
                ExpData d = ExperimentService.get().getExpData(rd.getFileId2());
                Container c = ContainerManager.getForId(rd.getContainer());
                if (!rd.isArchived())
                {
                    if (d == null || d.getFile() == null)
                    {
                        log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId2() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                    else if (!d.getFile().exists())
                    {
                        log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId2() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                }
                else
                {
                    if (d != null && d.getFile() != null && d.getFile().exists())
                    {
                        log.error("ReadData marked as archived, but file exists: " + rd.getRowid() + ", " + rd.getFileId1() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                }
            }
        }

        //also check analyses
        log.info("Inspecting Analyses");
        TableInfo analysesTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
        TableSelector tsAnalyses = new TableSelector(analysesTable, null, new Sort("container"));
        List<AnalysisModelImpl> analyses = tsAnalyses.getArrayList(AnalysisModelImpl.class);
        i = 0;
        for (AnalysisModelImpl m : analyses)
        {
            i++;
            if (i % 1000 == 0)
            {
                log.info("analysis " + i + " of " + analyses.size() + ". Current container: " + ContainerManager.getForId(m.getContainer()).getPath());
            }

            if (m.getAlignmentFile() != null)
            {
                ExpData d = m.getAlignmentData();
                Container c = ContainerManager.getForId(m.getContainer());
                if (d == null || d.getFile() == null)
                {
                    log.error("Unable to find file associated with analysis: " + m.getAnalysisId() + ", " + m.getAlignmentFile() + " for container: " + (c == null ? m.getContainer() : c.getPath()));
                }
                else if (!d.getFile().exists())
                {
                    log.error("Unable to find file associated with analysis: " + m.getAnalysisId() + ", " + m.getAlignmentFile() + ", " + d.getFile().getPath() + " for container: " + (c == null ? m.getContainer() : c.getPath()));
                }
            }
        }
    }

    private void processContainer(Container c, Logger log) throws IOException, PipelineJobException
    {
        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root != null && !root.isCloudRoot())
        {
            if (!c.isWorkbook())
                log.info("processing container: " + c.getPath());

            //first sequences
            File sequenceDir = new File(root.getRootPath(), ".sequences");
            TableInfo tableRefNtSequences = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
            TableSelector ntTs = new TableSelector(tableRefNtSequences, new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
            final Set<String> expectedSequences = new HashSet<>(10000, 1000);
            ntTs.forEach(RefNtSequenceModel.class, m -> {
                if (m.getSequenceFile() == null || m.getSequenceFile() == 0)
                {
                    log.error("sequence record lacks a sequence file Id: " + m.getRowid());
                    return;
                }

                ExpData d = ExperimentService.get().getExpData(m.getSequenceFile());
                if (d == null || d.getFile() == null)
                {
                    log.error("file was null for sequence: " + m.getRowid());
                    return;
                }

                if (!d.getFile().exists())
                {
                    log.error("expected sequence file does not exist for sequence: " + m.getRowid() + " " + m.getName() + ", expected: " + d.getFile().getPath());
                    return;
                }

                if (d.getFile().getAbsolutePath().toLowerCase().startsWith(sequenceDir.getAbsolutePath().toLowerCase()))
                {
                    expectedSequences.add(d.getFile().getName());
                }
            });

            if (sequenceDir.exists())
            {
                for (File child : sequenceDir.listFiles())
                {
                    if (!expectedSequences.contains(child.getName()))
                    {
                        deleteFile(child, log);
                    }
                }
            }

            //then libraries
            File libraryDir = SequenceAnalysisManager.get().getReferenceLibraryDir(c);
            if (libraryDir != null && libraryDir.exists())
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
                TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                Set<String> expectedLibraries = new HashSet<>();
                for (Integer rowId : ts.getArrayList(Integer.class))
                {
                    expectedLibraries.add(rowId.toString());
                }

                for (File child : libraryDir.listFiles())
                {
                    if ("log".equals(FileUtil.getExtension(child)) || "xml".equals(FileUtil.getExtension(child)))
                    {
                        continue;  //always ignore log files
                    }

                    if (!expectedLibraries.contains(child.getName()))
                    {
                        deleteFile(child, log);
                    }
                    else
                    {
                        //inspect within library
                        List<String> expectedChildren = new ArrayList<>();
                        int libraryId = Integer.parseInt(child.getName());
                        Integer fastaId = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("fasta_file")).getObject(libraryId, Integer.class);
                        if (fastaId == null)
                        {
                            log.error("Unable to find FASTA ExpData in DB matching jbrowse directory: " + child.getPath());
                            continue;
                        }

                        ExpData fastaData = ExperimentService.get().getExpData(fastaId);
                        File fasta = fastaData.getFile();
                        if (!fasta.exists())
                        {
                            log.error("expected fasta file does not exist: " + fasta.getPath());
                        }

                        // Use this to retroactively convert existing genomes:
                        File gz = new File(fasta.getPath() + ".gz");
                        if (!gz.exists())
                        {
                            ReferenceGenomeImpl genome = new ReferenceGenomeImpl(fasta, fastaData, libraryId, null);
                            log.error("GZipped genome missing for: " + genome.getGenomeId());

                            if (SystemUtils.IS_OS_WINDOWS)
                            {
                                log.warn("Cannot create bgzipped file on windows machine");
                            }
                            else
                            {
                                genome.createGzippedFile(log);
                            }
                        }

                        File gzi = new File(fasta.getPath() + ".gz.gzi");
                        if (!gzi.exists())
                        {
                            if (SystemUtils.IS_OS_WINDOWS)
                            {
                                log.warn("Cannot index gzipped FASTA on windows: " + fasta.getPath());
                            }
                            else
                            {
                                new FastaIndexer(log).execute(gz);
                            }
                        }

                        expectedChildren.add(fasta.getName() + ".gz");
                        expectedChildren.add(fasta.getName() + ".gz.gzi");
                        expectedChildren.add(fasta.getName() + ".gz.fai");

                        expectedChildren.add(fasta.getName());
                        expectedChildren.add(fasta.getName() + ".fai");
                        expectedChildren.add(FileUtil.getBaseName(fasta.getName()) + ".idKey.txt");
                        expectedChildren.add(FileUtil.getBaseName(fasta.getName()) + ".dict");
                        expectedChildren.add("libraryMembers.xml");  //temp file creating during pipeline job
                        expectedChildren.add("alignerIndexes");
                        expectedChildren.add("tracks");
                        expectedChildren.add("chainFiles");
                        expectedChildren.add(".lastUpdate");

                        for (String fileName : child.list())
                        {
                            if (!expectedChildren.contains(fileName))
                            {
                                if ("log".equals(FileUtil.getExtension(fileName)) || "xml".equals(FileUtil.getExtension(fileName)))
                                {
                                    continue;
                                }

                                deleteFile(new File(child, fileName), log);
                            }
                        }

                        //check/verify tracks
                        File trackDir = new File(child, "tracks");
                        if (trackDir.exists())
                        {
                            Set<String> expectedTracks = new HashSet<>();
                            TableInfo tracksTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS);
                            TableSelector tracksTs = new TableSelector(tracksTable, Collections.singleton("fileid"), new SimpleFilter(FieldKey.fromString("library_id"), libraryId), null);
                            for (Integer dataId : tracksTs.getArrayList(Integer.class))
                            {
                                ExpData trackData = ExperimentService.get().getExpData(dataId);
                                if (trackData != null && trackData.getFile() != null)
                                {
                                    expectedTracks.add(trackData.getFile().getName());
                                    if (!trackData.getFile().exists())
                                    {
                                        log.error("expected track file does not exist: " + trackData.getFile().getPath());
                                    }

                                    expectedTracks.addAll(getAssociatedFiles(trackData.getFile(), true));
                                }
                                else
                                {
                                    log.warn("unable to find ExpData for track with dataId: " + dataId);
                                }
                            }

                            for (File f : trackDir.listFiles())
                            {
                                if (!expectedTracks.contains(f.getName()))
                                {
                                    deleteFile(f, log);
                                }
                            }
                        }

                        //check/verify chainFiles
                        File chainDir = new File(child, "chainFiles");
                        if (chainDir.exists())
                        {
                            Set<String> expectedChains = new HashSet<>();
                            TableInfo chainTable = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_CHAIN_FILES);
                            TableSelector chainTs = new TableSelector(chainTable, Collections.singleton("chainFile"), new SimpleFilter(FieldKey.fromString("genomeId1"), libraryId), null);
                            for (Integer dataId : chainTs.getArrayList(Integer.class))
                            {
                                ExpData chainData = ExperimentService.get().getExpData(dataId);
                                if (chainData != null && chainData.getFile() != null)
                                {
                                    expectedChains.add(chainData.getFile().getName());
                                    if (!chainData.getFile().exists())
                                    {
                                        log.error("expected chain file does not exist: " + chainData.getFile().getPath());
                                    }
                                }
                            }

                            for (File f : chainDir.listFiles())
                            {
                                if (!expectedChains.contains(f.getName()))
                                {
                                    deleteFile(f, log);
                                }
                            }
                        }
                    }
                }
            }

            //finally outputfiles
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
            TableSelector ts = new TableSelector(ti, Collections.singleton("dataid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
            Set<String> expectedFileNames = new HashSet<>();
            for (Integer dataId : ts.getArrayList(Integer.class))
            {
                ExpData d = ExperimentService.get().getExpData(dataId);
                if (d != null)
                {
                    if (d.getFile() == null)
                    {
                        log.error("File was null for ExpData: " + d.getRowId());
                        continue;
                    }

                    expectedFileNames.add(d.getFile().getName());
                    expectedFileNames.addAll(getAssociatedFiles(d.getFile(), true));

                    if (!d.getFile().exists())
                    {
                        log.error("expected output file does not exist: " + d.getFile().getPath());
                        continue;
                    }

                    //also verify indexes
                    if (_vcfFileType.isType(d.getFile()) && d.getFile().getPath().endsWith(".gz"))
                    {
                        File idx = new File(d.getFile().getPath() + ".tbi");
                        if (!idx.exists())
                        {
                            log.warn("unable to find index for file: " + d.getFile().getPath() + ", creating");
                            SequenceAnalysisService.get().ensureVcfIndex(d.getFile(), log);
                        }
                    }
                }
            }

            File sequenceOutputsDir = new File(root.getRootPath(), "sequenceOutputs");
            if (sequenceOutputsDir.exists())
            {
                for (File child : sequenceOutputsDir.listFiles())
                {
                    if (!expectedFileNames.contains(child.getName()))
                    {
                        deleteFile(child, log);
                    }
                }
            }
        }

        for (Container child : c.getChildren())
        {
            processContainer(child, log);
        }
    }

    private void deleteFile(File f, Logger log) throws IOException
    {
        log.info("deleting sequence file: " + f.getPath());
        if (f.isDirectory())
        {
            FileUtils.deleteDirectory(f);
        }
        else
        {
            f.delete();
        }
    }

    private static FileType _bamFileType = new FileType("bam");
    private static FileType _cramFileType = new FileType("cram");
    private static FileType _vcfFileType = new FileType("vcf", FileType.gzSupportLevel.SUPPORT_GZ);
    private static FileType _bedFileType = new FileType("bed", FileType.gzSupportLevel.SUPPORT_GZ);
    private static FileType _fastaFileType = new FileType(Arrays.asList("fasta", "fa"), "fasta", FileType.gzSupportLevel.SUPPORT_GZ);
    private static FileType _gxfFileType = new FileType(Arrays.asList("gtf", "gff", "gff3"), "gff", FileType.gzSupportLevel.SUPPORT_GZ);

    /**
     * This is intended to return any files associated with an input, which is primarily designed to pick up index files
     */
    public static List<String> getAssociatedFiles(File f, boolean includeGz)
    {
        List<String> ret = new ArrayList<>();

        //TODO: this is sort of a hack.  certain file types can get gzipped or indexed, so add those variants:
        if (_bamFileType.isType(f))
        {
            ret.add(f.getName() + ".bai");
        }
        else if (_cramFileType.isType(f))
        {
            ret.add(f.getName() + ".crai");
        }
        else if (_vcfFileType.isType(f))
        {
            ret.add(f.getName() + ".tbi");
            ret.add(f.getName() + ".idx");
            ret.add(f.getName() + ".bgz");

            if (includeGz)
            {
                ret.add(f.getName() + ".gz");
                ret.add(f.getName() + ".gz.tbi");
                ret.add(f.getName() + ".gz.idx");
            }
        }
        else if (_bedFileType.isType(f) || _gxfFileType.isType(f))
        {
            ret.add(f.getName() + ".idx");
            ret.add(f.getName() + ".tbi");

            if (includeGz)
            {
                ret.add(f.getName() + ".gz");
                ret.add(f.getName() + ".gz.tbi");
                ret.add(f.getName() + ".gz.idx");
            }
        }
        else if (_fastaFileType.isType(f))
        {
            ret.add(f.getName() + ".fai");
            ret.add(f.getName() + ".gz");
            ret.add(f.getName() + ".gz.gzi");
            ret.add(f.getName() + ".gz.fai");
        }

        // NOTE: this allows modules to register handlers for extra ancillary files, such as seurat metadata
        SequenceAnalysisServiceImpl.get().getAccessoryFileProviders().forEach(fn -> {
            ret.addAll(fn.apply(f).stream().map(File::getName).collect(Collectors.toList()));
        });

        return ret;
    }
}