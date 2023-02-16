package org.labkey.sequenceanalysis.pipeline;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.CancelledException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;
import org.labkey.sequenceanalysis.util.SequenceUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrphanFilePipelineJob extends PipelineJob
{
    // Default constructor for serialization
    protected OrphanFilePipelineJob()
    {
    }

    public OrphanFilePipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot)
    {
        super(OrphanFilePipelineProvider.NAME, new ViewBackgroundInfo(c, user, url), pipeRoot);

        File subdir = new File(pipeRoot.getRootPath(), "orphanSequenceFiles");
        if (!subdir.exists())
        {
            subdir.mkdirs();
        }

        setLogFile(new File(subdir, FileUtil.makeFileNameWithTimestamp("orphanFilesPipeline", "log")));
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public String getDescription()
    {
        return "Find Orphan Sequence Files";
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(OrphanFilePipelineJob.class));
    }

    public static class Task extends PipelineJob.Task<Task.Factory>
    {
        protected Task(Factory factory, PipelineJob job)
        {
            super(factory, job);
        }

        public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
        {
            public Factory()
            {
                super(Task.class);
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
                return List.of("Find Orphan Sequence Files");
            }

            @Override
            public PipelineJob.Task createTask(PipelineJob job)
            {
                return new Task(this, job);
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
            getJob().getLogger().info("## The following sections list any files or pipeline jobs that appear to be orphans, not connected to any imported readsets or sequence outputs:");

            Set<File> orphanFiles = new HashSet<>();
            Set<File> orphanIndexes = new HashSet<>();
            Set<File> probableDeletes = new HashSet<>();
            Set<PipelineStatusFile> orphanJobs = new HashSet<>();
            List<String> messages = new ArrayList<>();
            Set<File> knownJobPaths = Collections.unmodifiableSet(getKnownSequenceJobPaths(getJob().getContainer(), getJob().getUser(), messages));

            //find known ExpDatas from this container, across all workbooks
            Set<Integer> knownExpDatas = new HashSet<>();
            Container parent = getJob().getContainer().isWorkbook() ? getJob().getContainer().getParent() : getJob().getContainer();
            UserSchema us = QueryService.get().getUserSchema(getJob().getUser(), parent, SequenceAnalysisSchema.SCHEMA_NAME);

            knownExpDatas.addAll(new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_READ_DATA, null), PageFlowUtil.set("fileid1"),null, null).getArrayList(Integer.class));
            knownExpDatas.addAll(new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_READ_DATA, null), PageFlowUtil.set("fileid2"),null, null).getArrayList(Integer.class));
            knownExpDatas.addAll(new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_ANALYSES, null), PageFlowUtil.set("alignmentfile"),null, null).getArrayList(Integer.class));
            knownExpDatas.addAll(new TableSelector(us.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES, null), PageFlowUtil.set("dataId"),null, null).getArrayList(Integer.class));
            knownExpDatas = Collections.unmodifiableSet(knownExpDatas);
            //messages.add("## total registered sequence ExpData: " + knownExpDatas.size());

            getOrphanFilesForContainer(getJob().getContainer(), getJob().getUser(), orphanFiles, orphanIndexes, orphanJobs, messages, probableDeletes, knownJobPaths, knownExpDatas);
            probableDeletes.addAll(orphanIndexes);

            if (!orphanFiles.isEmpty())
            {
                StringBuilder sb = new StringBuilder();
                orphanFiles.forEach(f -> sb.append("\n").append(f.getPath()));
                getJob().getLogger().info("## The following sequence files are not referenced by readsets, analyses or output files:" + sb);
            }

            if (!orphanIndexes.isEmpty())
            {
                StringBuilder sb = new StringBuilder();
                orphanIndexes.forEach(f -> sb.append("\n").append(f.getPath()));
                getJob().getLogger().info("## The following index files appear to be orphans:" + sb);
            }

            if (!orphanJobs.isEmpty())
            {
                getJob().getLogger().info("## The following sequence jobs are not referenced by readsets, analyses or output files.");
                getJob().getLogger().info("## The best action would be to view the pipeline job list, 'Sequence Jobs' view, and filter for jobs without sequence outputs.  Deleting any unwanted jobs through the UI should also delete files.");
                for (PipelineStatusFile sf : orphanJobs)
                {
                    File f = new File(sf.getFilePath()).getParentFile();
                    if (f.exists())
                    {
                        long size = FileUtils.sizeOfDirectory(f);
                        //ignore if less than 1mb
                        if (size > 1e6)
                        {
                            getJob().getLogger().info("\n## size: " + FileUtils.byteCountToDisplaySize(size));
                            getJob().getLogger().info("\n" + f.getPath());
                        }
                    }
                    else
                    {
                        messages.add("## Pipeline job folder does not exist: " + sf.getRowId());
                        messages.add(f.getPath());
                    }
                }
            }

            if (!messages.isEmpty())
            {
                getJob().getLogger().info("## The following messages were generated:");
                getJob().getLogger().info(StringUtils.join(messages, "\n"));
            }

            if (!probableDeletes.isEmpty())
            {
                StringBuilder sb = new StringBuilder();
                File output = new File(getJob().getLogFile().getParentFile(), "toRemove.sh");
                try (PrintWriter writer = PrintWriters.getPrintWriter(output))
                {
                    writer.println("#!/bin/bash");
                    writer.println("");
                    writer.println("set -e");
                    writer.println("set -x");
                    writer.println("");
                    probableDeletes.forEach(f -> writer.println("rm -Rf " + f.getPath()));
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }

                probableDeletes.forEach(f -> sb.append("\n").append(f.getPath()));
                getJob().getLogger().info("## The following files can almost certainly be deleted; however, please exercise caution. Note: the file toRemove.sh has been written and can be executed to remove these:" + sb);


            }

            return new RecordedActionSet();
        }

        private static final Set<String> pipelineDirs = PageFlowUtil.set(ReadsetImportJob.FOLDER_NAME, ReadsetImportJob.FOLDER_NAME + "Pipeline", AlignmentAnalysisJob.FOLDER_NAME, AlignmentAnalysisJob.FOLDER_NAME + "Pipeline", "sequenceOutputs", SequenceOutputHandlerJob.FOLDER_NAME + "Pipeline", "illuminaImport", "analyzeAlignment");
        private static final Set<String> skippedDirs = PageFlowUtil.set(".sequences", ".jbrowse");

        private Set<File> getKnownSequenceJobPaths(Container c, User u, Collection<String> messages)
        {
            c = c.isWorkbook() ? c.getParent() : c;

            //Note: these can cut across workbooks, so search using the parent container
            Set<Integer> knownPipelineJobs = new HashSet<>();
            UserSchema us = QueryService.get().getUserSchema(u, c, SequenceAnalysisSchema.SCHEMA_NAME);
            TableInfo rd = us.getTable(SequenceAnalysisSchema.TABLE_READ_DATA, null);
            knownPipelineJobs.addAll(new TableSelector(rd, new LinkedHashSet<>(QueryService.get().getColumns(rd, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

            TableInfo rs = us.getTable(SequenceAnalysisSchema.TABLE_READSETS, null);
            knownPipelineJobs.addAll(new TableSelector(rs, new LinkedHashSet<>(QueryService.get().getColumns(rs, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

            TableInfo a = us.getTable(SequenceAnalysisSchema.TABLE_ANALYSES, null);
            knownPipelineJobs.addAll(new TableSelector(a, new LinkedHashSet<>(QueryService.get().getColumns(a, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

            TableInfo of = us.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES, null);
            knownPipelineJobs.addAll(new TableSelector(of, new LinkedHashSet<>(QueryService.get().getColumns(of, PageFlowUtil.set(FieldKey.fromString("runId/jobId"))).values()), new SimpleFilter(FieldKey.fromString("runId/jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));

            knownPipelineJobs.addAll(new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES), PageFlowUtil.set("jobId"), new SimpleFilter(FieldKey.fromString("jobId"), null, CompareType.NONBLANK), null).getArrayList(Integer.class));
            knownPipelineJobs = Collections.unmodifiableSet(knownPipelineJobs);
            //messages.add("## total expected pipeline job folders: " + knownPipelineJobs.size());

            TableSelector jobTs = new TableSelector(PipelineService.get().getJobsTable(u, c), PageFlowUtil.set("FilePath"), new SimpleFilter(FieldKey.fromString("RowId"), knownPipelineJobs, CompareType.IN), null);

            Set<File> knownJobPaths = new HashSet<>();
            for (String filePath : jobTs.getArrayList(String.class))
            {
                File f = new File(filePath).getParentFile();
                if (!f.exists())
                {
                    messages.add("## unable to find expected pipeline job folder: " + f.getPath());
                }
                else
                {
                    knownJobPaths.add(f);
                }
            }
            //messages.add("## total job paths: " + knownJobPaths.size());

            return knownJobPaths;
        }

        private Map<URI, Set<Integer>> getDataMapForContainer(Container c)
        {
            SimpleFilter dataFilter = new SimpleFilter(FieldKey.fromString("container"), c.getId());
            TableInfo dataTable = ExperimentService.get().getTinfoData();
            TableSelector ts = new TableSelector(dataTable, PageFlowUtil.set("RowId", "DataFileUrl"), dataFilter, null);
            final Map<URI, Set<Integer>> dataMap = new HashMap<>();
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    if (rs.getString("DataFileUrl") == null)
                    {
                        return;
                    }

                    try
                    {
                        URI uri = new URI(rs.getString("DataFileUrl"));
                        if (!dataMap.containsKey(uri))
                        {
                            dataMap.put(uri, new HashSet<>());
                        }

                        dataMap.get(uri).add(rs.getInt("RowId"));
                    }
                    catch (URISyntaxException e)
                    {
                        getJob().getLogger().error(e.getMessage(), e);
                    }
                }
            });
            //messages.add("## total ExpData paths: " + dataMap.size());

            return dataMap;
        }

        public void getOrphanFilesForContainer(Container c, User u, Set<File> orphanFiles, Set<File> orphanIndexes, Set<PipelineStatusFile> orphanJobs, List<String> messages, Set<File> probableDeletes, Set<File> knownSequenceJobPaths, Set<Integer> knownExpDatas)
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(c);
            if (root == null)
            {
                return;
            }

            if (getJob().isCancelled())
            {
                throw new CancelledException();
            }

            messages.add("## processing container: " + c.getPath());

            Map<URI, Set<Integer>> dataMap = getDataMapForContainer(c);

            Container parent = c.isWorkbook() ? c.getParent() : c;
            TableInfo jobsTableParent = PipelineService.get().getJobsTable(u, parent);

            Set<File> unexpectedPipelineDirs = new HashSet<>();
            for (String dirName : pipelineDirs)
            {
                File dir = new File(root.getRootPath(), dirName);
                if (dir.exists())
                {
                    for (File subdir : dir.listFiles())
                    {
                        if (!subdir.isDirectory())
                        {
                            continue;
                        }

                        boolean isOrphanPipelineDir = isOrphanPipelineDir(jobsTableParent, subdir, c, knownExpDatas, knownSequenceJobPaths, orphanJobs, messages);
                        if (!isOrphanPipelineDir)
                        {
                            if (!knownSequenceJobPaths.contains(subdir))
                            {
                                messages.add("#pipeline path listed as orphan, and not present in known job paths: ");
                                messages.add(subdir.getPath());
                                probableDeletes.add(subdir);
                                unexpectedPipelineDirs.add(subdir);
                            }

                            getOrphanFilesForDirectory(knownExpDatas, dataMap, subdir, orphanFiles, orphanIndexes);
                        }
                    }
                }
            }

            // any files remaining in knownJobPaths indicates that we didnt find registered sequence data.  this could be a job
            // that either failed or for whatever reason is no longer important
            if (!unexpectedPipelineDirs.isEmpty())
            {
                messages.add("## The following directories match existing pipeline jobs, but do not contain registered data for this container:");
                for (File f : unexpectedPipelineDirs)
                {
                    long size = FileUtils.sizeOfDirectory(f);
                    //ignore if less than 1mb
                    if (size > 1e6)
                    {
                        messages.add("## size: " + FileUtils.byteCountToDisplaySize(size));
                        messages.add(f.getPath());
                    }
                }
            }

            //TODO: look for .deleted and /archive
            File deletedDir = new File(root.getRootPath().getParentFile(), ".deleted");
            if (deletedDir.exists())
            {
                messages.add("## .deleted dir found: " + deletedDir.getPath());
            }

            File assayData = new File(root.getRootPath(), "assaydata");
            if (assayData.exists())
            {
                File[] bigFiles = assayData.listFiles(new FileFilter()
                {
                    @Override
                    public boolean accept(File pathname)
                    {
                        //50mb
                        return (pathname.length() >= 5e7);
                    }
                });

                if (bigFiles != null && bigFiles.length > 0)
                {
                    messages.add("## large files in assaydata, might be unnecessary:");
                    for (File f : bigFiles)
                    {
                        messages.add(f.getPath());
                    }
                }

                File archive = new File(assayData, "archive");
                if (archive.exists())
                {
                    File[] files = archive.listFiles();
                    if (files != null && files.length > 0)
                    {
                        messages.add("## the following files are in assaydata/archive, and were probably automatically moved here after delete.  they might be unnecessary:");
                        for (File f : files)
                        {
                            messages.add(f.getPath());
                        }
                    }
                }
            }

            for (Container child : ContainerManager.getChildren(c))
            {
                if (child.isWorkbook())
                {
                    getOrphanFilesForContainer(child, u, orphanFiles, orphanIndexes, orphanJobs, messages, probableDeletes, knownSequenceJobPaths, knownExpDatas);
                }
            }
        }

        private boolean isOrphanPipelineDir(TableInfo jobsTable, File dir, Container c, Set<Integer> knownExpDataIds, Set<File> knownSequenceJobPaths, Set<PipelineStatusFile> orphanJobs, List<String> messages)
        {
            //find statusfile.  Note: this should consider all workbooks, not just current dir
            List<Integer> jobIds = new TableSelector(jobsTable, PageFlowUtil.set("RowId"), new SimpleFilter(FieldKey.fromString("FilePath"), dir.getPath() + System.getProperty("file.separator"), CompareType.STARTS_WITH), null).getArrayList(Integer.class);
            if (jobIds.isEmpty())
            {
                //NOTE: this is logged above
                //messages.add("## Unable to find matching job, might be orphan: ");
                //messages.add(dir.getPath());
                return false;
            }
            else if (jobIds.size() > 1)
            {
                messages.add("## More than one possible job found, this may simply indicate parent/child jobs: " + dir.getPath());
            }

            //this could be a directory from an analysis that doesnt register files, like picard metrics
            if (knownSequenceJobPaths.contains(dir))
            {
                return false;
            }

            // NOTE: if this file is within a known job path, it still could be an orphan.  first check whether the directory has registered files.
            // If so, remove that path from the set of known job paths
            List<? extends ExpData> dataUnderPath = ExperimentService.get().getExpDatasUnderPath(dir, c);
            Set<Integer> dataIdsUnderPath = new HashSet<>();
            for (ExpData d : dataUnderPath)
            {
                dataIdsUnderPath.add(d.getRowId());
            }

            if (!CollectionUtils.containsAny(dataIdsUnderPath, knownExpDataIds))
            {
                for (int jobId : jobIds)
                {
                    PipelineStatusFile sf = PipelineService.get().getStatusFile(jobId);
                    orphanJobs.add(sf);
                }

                return true;
            }

            return false;
        }

        private void getOrphanFilesForDirectory(Set<Integer> knownExpDatas, Map<URI, Set<Integer>> dataMap, File dir, Set<File> orphanSequenceFiles, Set<File> orphanIndexes)
        {
            //skipped for perf reasons.  extremely unlikely
            //if (!dir.exists() || Files.isSymbolicLink(dir.toPath()))
            //{
            //    return;
            //}

            //TODO: look for a /Shared folder with large items under it

            File[] arr = dir.listFiles();
            if (arr == null)
            {
                getJob().getLogger().error("unable to list files: " + dir.getPath());
                return;
            }

            for (File f : arr)
            {
                //skipped for perf reasons.  extremely unlikely
                //if (Files.isSymbolicLink(f.toPath()))
                //{
                //    continue;
                //}

                if (f.isDirectory())
                {
                    getOrphanFilesForDirectory(knownExpDatas, dataMap, f, orphanSequenceFiles, orphanIndexes);
                }
                else
                {
                    //iterate possible issues:

                    //orphan index
                    if (f.getPath().toLowerCase().endsWith(".bai") || f.getPath().toLowerCase().endsWith(".tbi") || f.getPath().toLowerCase().endsWith(".idx") || f.getPath().toLowerCase().endsWith(".crai"))
                    {
                        if (!new File(FileUtil.getBaseName(f.getPath())).exists())
                        {
                            orphanIndexes.add(f);
                            continue;
                        }
                    }

                    //orphan copy file:
                    if (f.getName().endsWith(".copy"))
                    {
                        orphanSequenceFiles.add(f);
                    }

                    //heapdump:
                    if (f.getName().endsWith(".hprof"))
                    {
                        orphanSequenceFiles.add(f);
                    }

                    //core dump:
                    if (f.getName().matches("core.[0-9]+"))
                    {
                        orphanSequenceFiles.add(f);
                    }

                    //sequence files not associated w/ DB records:
                    if (SequenceUtil.FILETYPE.fastq.getFileType().isType(f) || SequenceUtil.FILETYPE.bam.getFileType().isType(f) || SequenceUtil.FILETYPE.vcf.getFileType().isType(f) || SequenceUtil.FILETYPE.gvcf.getFileType().isType(f))
                    {
                        //find all ExpDatas referencing this file
                        Set<Integer> dataIdsForFile = dataMap.get(FileUtil.getAbsoluteCaseSensitiveFile(f).toURI());
                        if (dataIdsForFile == null || !CollectionUtils.containsAny(dataIdsForFile, knownExpDatas))
                        {
                            //a hack, but special-case undetermined/unaligned FASTQ files
                            if (SequenceUtil.FILETYPE.fastq.getFileType().isType(f))
                            {
                                if (f.getPath().contains("/Normalization/") && f.getName().startsWith("Undetermined_"))
                                    continue;
                                else if (f.getPath().contains("/Normalization/") && f.getName().contains("_unknowns"))
                                    continue;
                                else if (f.getPath().contains("/outs/") || f.getPath().contains("/Alignment/") && (f.getName().contains("unaligned") || f.getName().contains("unmapped") || f.getName().contains(".overlapping-")))
                                    continue;
                                else if (f.getName().contains(".overlapping-R"))
                                {
                                    //outputs from earlier TCR pipelines:
                                    continue;
                                }
                            }
                            else if (SequenceUtil.FILETYPE.bam.getFileType().isType(f))
                            {
                                //ignore 10x products:
                                if (f.getPath().contains("/outs/"))
                                {
                                    continue;
                                }
                            }

                            //this is too broad a net
                            //                        if (dataIdsForFile != null)
                            //                        {
                            //                            for (int rowId : dataIdsForFile)
                            //                            {
                            //                                ExpData d = ExperimentService.get().getExpData(rowId);
                            //                                Set<String> roles = ExperimentService.get().getDataInputRoles(d.getContainer(), ContainerFilter.EVERYTHING, ExpProtocol.ApplicationType.ExperimentRunOutput);
                            //                                if (roles != null && roles.contains(SequenceTaskHelper.NORMALIZED_FASTQ_OUTPUTNAME))
                            //                                {
                            //                                    continue OUTER;
                            //                                }
                            //                            }
                            //                        }

                            orphanSequenceFiles.add(f);
                        }
                    }
                }
            }
        }
    }
}
