package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by bimber on 9/15/2014.
 */
public class SequenceAnalysisMaintenanceTask implements MaintenanceTask
{
    private static final String SYSTEM_MAINTENANCE_DESCRIPTION = "System Maintenance";
    private static final String JOB_TABLE = "statusfiles";

    public SequenceAnalysisMaintenanceTask()
    {

    }

    @Override
    public String getDescription()
    {
        return "SequenceAnalysis File Maintenance";
    }

    @Override
    public String getName()
    {
        return "DeleteSequenceAnalysisArtifacts";
    }

    private int _jobId = -1;

    // NOTE: if there is a more direct way to locate the JobID this hack should be replaced
    private void checkJobCancelled(Logger log)
    {
        if (_jobId == -1)
        {
            // Make the assumption there is only one active maintenance job at a time:
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("description"), SYSTEM_MAINTENANCE_DESCRIPTION).
                    addCondition(FieldKey.fromString("container"), ContainerManager.getRoot().getId()).
                    addCondition(FieldKey.fromString("modified"), LocalDate.now().minusDays(2), CompareType.DATE_GTE);
            int rowId = new TableSelector(DbSchema.get("pipeline", DbSchemaType.Module).getTable(JOB_TABLE), PageFlowUtil.set("RowId", "Status"), filter, null).getMapCollection().stream().filter(map -> {
                String val = String.valueOf(map.get("status"));
                return val != null && (val.toLowerCase().startsWith(PipelineJob.TaskStatus.cancelling.name()) || val.toLowerCase().startsWith(PipelineJob.TaskStatus.running.name()));
            }).map(rs -> Integer.parseInt(String.valueOf(rs.get("rowid")))).max(Integer::compareTo).orElse(-1);

            if (rowId == -1)
            {
                log.warn("Unable to find rowId for job", new Exception("Unable to find rowId for job"));
                return;
            }

            _jobId = rowId;
        }

        PipelineStatusFile sf = PipelineService.get().getStatusFile(_jobId);
        if (PipelineJob.TaskStatus.cancelling.name().equalsIgnoreCase(sf.getStatus()))
        {
            throw new CancelledException();
        }
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

    private void possiblySubmitRemoteTask(Logger log) throws InterruptedException, ExecutionException
    {
        if (SequencePipelineService.get().isRemoteGenomeCacheUsed())
        {
            JobRunner jr = JobRunner.getDefault();
            Future<?> future = jr.execute(() -> {
                try
                {
                    Map<Integer, File> genomeMap = new IntHashMap<>();
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
            }, 0);

            // Wait for this job:
            future.get();
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
                log.info("readdata " + i + " of " + readDatas.size() + ". Current container: " + Objects.requireNonNull(ContainerManager.getForId(rd.getContainer())).getPath());
                checkJobCancelled(log);
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
                    else if (!checkFile(d.getFile()))
                    {
                        log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId1() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                }
                else
                {
                    if (d != null && d.getFile() != null && checkFile(d.getFile()))
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
                    else if (!checkFile(d.getFile()))
                    {
                        log.error("Unable to find file associated with ReadData: " + rd.getRowid() + ", " + rd.getFileId2() + ", " + d.getFile().getPath() + " for container: " + (c == null ? rd.getContainer() : c.getPath()));
                    }
                }
                else
                {
                    if (d != null && d.getFile() != null && checkFile(d.getFile()))
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
                checkJobCancelled(log);
            }

            if (m.getAlignmentFile() != null)
            {
                ExpData d = m.getAlignmentData();
                Container c = ContainerManager.getForId(m.getContainer());
                if (d == null || d.getFile() == null)
                {
                    log.error("Unable to find file associated with analysis: " + m.getAnalysisId() + ", " + m.getAlignmentFile() + " for container: " + (c == null ? m.getContainer() : c.getPath()));
                }
                else if (!checkFile(d.getFile()))
                {
                    log.error("Unable to find file associated with analysis: " + m.getAnalysisId() + ", " + m.getAlignmentFile() + ", " + d.getFile().getPath() + " for container: " + (c == null ? m.getContainer() : c.getPath()));
                }
            }

            inspectForCoreFiles(m.getRunId(), log);
        }
    }

    Set<File> _filesPresent = new HashSet<>();
    Set<File> _filesMissing = new HashSet<>();

    // The purpose of this is to cache filesystem interactions and speed inspection of files:
    private boolean checkFile(File f)
    {
        if (_filesMissing.contains(f))
        {
            return false;
        }
        else if (_filesPresent.contains(f))
        {
            return true;
        }

        if (f.exists())
        {
            _filesPresent.add(f);
            return true;
        }
        else
        {
            _filesMissing.add(f);
            return false;
        }
    }
    private void inspectForCoreFiles(Long runId, Logger log)
    {
        if (runId == null)
        {
            return;
        }

        ExpRun run = ExperimentService.get().getExpRun(runId);
        if (run == null)
        {
            log.info("Not ExpRun found for runId: " + runId);
            return;
        }
        else if (run.getJobId() == null)
        {
            log.info("ExpRun lacks jobId: " + runId);
            return;
        }

        PipelineStatusFile sf = PipelineService.get().getStatusFile(run.getJobId());
        if (sf == null)
        {
            log.error("Unknown statusFile: " + run.getJobId() + ", for run: " + runId);
            return;
        }
        else if (sf.getFilePath() == null)
        {
            log.error("StatusFile filepath is null: " + run.getJobId() + ", for run: " + runId);
            return;
        }

        File root = new File(sf.getFilePath()).getParentFile();
        if (!checkFile(root))
        {
            log.error("Run file root does not exist. runId: " + runId + " / jobId: " + sf.getRowId() + " / " + root.getPath());
            return;
        }

        try (Stream<Path> stream = Files.walk(root.toPath()))
        {
            List<Path> files = stream.filter(x -> x.getFileName().startsWith("core.")).toList();
            if (!files.isEmpty())
            {
                files.forEach(x -> log.error("Found core file: " + x.toFile().getPath()));
            }
        }
        catch (IOException e)
        {
            log.error("Error walking file root: " + run.getFilePathRootPath(), e);
        }
    }

    private void processContainer(Container c, Logger log) throws IOException, PipelineJobException
    {
        if (!c.isWorkbook())
        {
            log.info("processing container: " + c.getPath());
        }

        checkJobCancelled(log);

        PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
        if (root != null && !root.isCloudRoot())
        {
            //first sequences
            log.debug("Inspecting sequences");
            File sequenceDir = FileUtil.appendName(root.getRootPath(), ".sequences");
            TableInfo tableRefNtSequences = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
            TableSelector ntTs = new TableSelector(tableRefNtSequences, new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
            final Set<File> expectedSequences = new HashSet<>(10000, 1000);
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

                if (d.getFile().getAbsolutePath().toLowerCase().startsWith(sequenceDir.getAbsolutePath().toLowerCase()))
                {
                    // File existence will be verified below:
                    expectedSequences.add(d.getFile());
                }
                else if (!checkFile(d.getFile()))
                {
                    log.error("Missing sequence file {}", d.getFile().getPath());
                }
            });

            if (checkFile(sequenceDir))
            {
                inspectSequenceDir(sequenceDir, expectedSequences, log);
            }

            if (!expectedSequences.isEmpty())
            {
                for (File missing : expectedSequences)
                {
                    if (checkFile(missing))
                    {
                        log.error("File exists, but wasnt removed from expectedSequences for folder {}, file:  {}", sequenceDir.getPath(), missing.getPath());
                    }
                    else
                    {
                        log.error("expected sequence file does not exist: {}", missing.getPath());
                    }
                }
            }

            //then libraries
            log.debug("Inspecting genomes");
            File libraryDir = SequenceAnalysisManager.get().getReferenceLibraryDir(c);
            if (libraryDir != null && checkFile(libraryDir))
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
                TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("container"), c.getId()), null);
                Set<String> expectedLibraries = new HashSet<>();
                for (Integer rowId : ts.getArrayList(Integer.class))
                {
                    expectedLibraries.add(rowId.toString());
                }

                try (Stream<Path> stream = Files.list(libraryDir.toPath()))
                {
                    stream.forEach(x -> {
                        inspectLibraryDir(x.toFile(), expectedLibraries, log);
                    });
                }
            }

            //finally outputfiles
            log.debug("Inspecting outputs");
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

                    if (!checkFile(d.getFile()))
                    {
                        log.error("expected output file does not exist: " + d.getFile().getPath());
                        continue;
                    }

                    //also verify indexes
                    if (_vcfFileType.isType(d.getFile()) && d.getFile().getPath().endsWith(".gz"))
                    {
                        File idx = new File(d.getFile().getPath() + ".tbi");
                        if (!checkFile(idx))
                        {
                            log.warn("unable to find index for file: " + d.getFile().getPath() + ", creating");
                            SequenceAnalysisService.get().ensureVcfIndex(d.getFile(), log);
                        }
                    }
                }
            }

            File sequenceOutputsDir = FileUtil.appendName(root.getRootPath(), "sequenceOutputs");
            if (checkFile(sequenceOutputsDir))
            {
                try (Stream<Path> stream = Files.list(sequenceOutputsDir.toPath()))
                {
                    stream.forEach(path -> {
                        File f = path.toFile();
                        if (!expectedFileNames.contains(f.getName()))
                        {
                            deleteFile(f, log);
                        }
                    });
                }
            }

            log.debug("done");
        }

        for (Container child : c.getChildren())
        {
            processContainer(child, log);
        }
    }

    private void inspectLibraryDir(File child, Set<String> expectedLibraries, Logger log)
    {
        if ("log".equals(FileUtil.getExtension(child)) || "xml".equals(FileUtil.getExtension(child)))
        {
            return;  //always ignore log files
        }

        if (!expectedLibraries.contains(child.getName()))
        {
            deleteFile(child, log);
            return;
        }

        //inspect within library
        List<String> expectedChildren = new ArrayList<>();
        int libraryId = Integer.parseInt(child.getName());
        Integer fastaId = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("fasta_file")).getObject(libraryId, Integer.class);
        if (fastaId == null)
        {
            log.error("Unable to find FASTA ExpData in DB matching jbrowse directory: " + child.getPath());
            return;
        }

        ExpData fastaData = ExperimentService.get().getExpData(fastaId);
        if (fastaData == null)
        {
            log.error("Unable to find ExpData: {}", fastaId);
            return;
        }

        File fasta = fastaData.getFile();
        if (!checkFile(fasta))
        {
            log.error("expected fasta file does not exist: " + fasta.getPath());
        }

        try
        {
            // Use this to retroactively convert existing genomes:
            File gz = new File(fasta.getPath() + ".gz");
            if (!checkFile(gz))
            {
                ReferenceGenomeImpl genome = new ReferenceGenomeImpl(fasta, fastaData, libraryId, null);

                // NOTE: we can hit a race condition in automated testing where a genome is newly created during a test, and the maintenance task runs concurrent with that test.
                // This is a check to reduce the log level, which thereby prevents the test from erroring
                Date created = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES), PageFlowUtil.set("created"), new SimpleFilter(FieldKey.fromString("rowId"), libraryId), null).getObject(Date.class);
                long timeSinceCreated = new Date().getTime() - created.getTime();
                // 1000*60*20 = 20 minutes
                Level l = timeSinceCreated > 1200000 ? Level.ERROR : Level.WARN;

                log.log(l, "GZipped genome missing for: " + genome.getGenomeId());

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
            if (!checkFile(gzi))
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

            for (String fileName : Objects.requireNonNull(child.list()))
            {
                if (!expectedChildren.contains(fileName))
                {
                    if ("log".equals(FileUtil.getExtension(fileName)) || "xml".equals(FileUtil.getExtension(fileName)))
                    {
                        continue;
                    }

                    deleteFile(FileUtil.appendName(child, fileName), log);
                }
            }

            //check/verify tracks
            File trackDir = FileUtil.appendName(child, "tracks");
            if (checkFile(trackDir))
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
                        if (!checkFile(trackData.getFile()))
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

                try (Stream<Path> stream = Files.list(trackDir.toPath()))
                {
                    stream.forEach(path -> {
                        File f = path.toFile();
                        if (!expectedTracks.contains(f.getName()))
                        {
                            deleteFile(f, log);
                        }
                    });
                }
            }

            //check/verify chainFiles
            File chainDir = FileUtil.appendName(child, "chainFiles");
            if (checkFile(chainDir))
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
                        if (!checkFile(chainData.getFile()))
                        {
                            log.error("expected chain file does not exist: " + chainData.getFile().getPath());
                        }
                    }
                }

                try (Stream<Path> stream = Files.list(chainDir.toPath()))
                {
                    stream.forEach(path -> {
                        File f = path.toFile();
                        if (!expectedChains.contains(f.getName()))
                        {
                            deleteFile(f, log);
                        }
                    });
                }
            }
        }
        catch (PipelineJobException | IOException e)
        {
            log.error("Error processing library directory: " + child.getPath(), e);
        }
    }

    private void inspectSequenceDir(File sequenceDir, Set<File> expectedSequences, Logger log) throws IOException
    {
        try (Stream<Path> stream = Files.list(sequenceDir.toPath()))
        {
            stream.forEach(path -> {
                File child = path.toFile();
                if (child.isDirectory())
                {
                    try
                    {
                        try (DirectoryStream<Path> stream2 = Files.newDirectoryStream(child.toPath()))
                        {
                            if (!stream2.iterator().hasNext())
                            {
                                deleteFile(child, log);
                                return;
                            }
                        }

                        inspectSequenceDir(child, expectedSequences, log);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                else
                {
                    if (!expectedSequences.remove(child))
                    {
                        deleteFile(child, log);
                    }
                }
            });
        }
    }

    private void deleteFile(File f, Logger log)
    {
        try
        {
            if (f.isDirectory())
            {
                FileUtils.deleteDirectory(f);
            }
            else
            {
                Files.delete(f.toPath());
            }
        }
        catch (IOException e)
        {
            log.error("Failed to delete file: " + f.getPath(), e);
        }
    }

    private static final FileType _bamFileType = new FileType("bam");
    private static final FileType _cramFileType = new FileType("cram");
    private static final FileType _vcfFileType = new FileType("vcf", FileType.gzSupportLevel.SUPPORT_GZ);
    private static final FileType _bedFileType = new FileType("bed", FileType.gzSupportLevel.SUPPORT_GZ);
    private static final FileType _fastaFileType = new FileType(Arrays.asList("fasta", "fa"), "fasta", FileType.gzSupportLevel.SUPPORT_GZ);
    private static final FileType _gxfFileType = new FileType(Arrays.asList("gtf", "gff", "gff3"), "gff", FileType.gzSupportLevel.SUPPORT_GZ);

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
            ret.add(f.getName() + ".pbi");
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
        else if (new FileType("txt.gz").isType(f))
        {
            ret.add(f.getName() + ".tbi");
        }

        // NOTE: this allows modules to register handlers for extra ancillary files, such as seurat metadata
        SequenceAnalysisServiceImpl.get().getAccessoryFileProviders().forEach(fn -> {
            ret.addAll(fn.apply(f).stream().map(File::getName).toList());
        });

        return ret;
    }
}