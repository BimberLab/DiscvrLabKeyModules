package org.labkey.jbrowse.pipeline;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.DISCVRSeqRunner;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.PrintWriters;
import org.labkey.jbrowse.JBrowseManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class JBrowseLucenePipelineJob extends PipelineJob
{
    private List<String> _infoFields;
    private String _jbrowseTrackId;
    private File _vcf;
    private File _targetDir;
    private boolean _allowLenientLuceneProcessing = false;

    // Default constructor for serialization
    protected JBrowseLucenePipelineJob()
    {
    }

    public JBrowseLucenePipelineJob(Container c, User user, PipeRoot pipeRoot, String jbrowseTrackId, File vcf, File targetDir, List<String> infoFields, boolean allowLenientLuceneProcessing)
    {
        super(JBrowseLucenePipelineProvider.NAME, new ViewBackgroundInfo(c, user, null), pipeRoot);
        _jbrowseTrackId = jbrowseTrackId;
        _vcf = vcf;
        _targetDir = targetDir;
        _infoFields = infoFields;
        _allowLenientLuceneProcessing = allowLenientLuceneProcessing;

        setLogFile(AssayFileWriter.findUniqueFileName("jbrowse-lucene" + new GUID() + ".log", JBrowseManager.get().getBaseDir(c, true).toPath()));
    }

    public static class JBrowseLucenePipelineProvider extends PipelineProvider
    {
        public static final String NAME = "JBrowseSessionPipeline";

        public JBrowseLucenePipelineProvider(Module owningModule)
        {
            super(NAME, owningModule);
        }

        @Override
        public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
        {

        }
    }


    @Override
    public String getDescription()
    {
        return "Preparing VCF index";
    }


    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public TaskPipeline<?> getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(JBrowseLucenePipelineJob.class));
    }

    public List<String> getInfoFields()
    {
        return _infoFields;
    }

    public void setInfoFields(List<String> infoFields)
    {
        _infoFields = infoFields;
    }

    public String getJbrowseTrackId()
    {
        return _jbrowseTrackId;
    }

    public void setJbrowseTrackId(String jbrowseTrackId)
    {
        _jbrowseTrackId = jbrowseTrackId;
    }

    public File getVcf()
    {
        return _vcf;
    }

    public void setVcf(File vcf)
    {
        _vcf = vcf;
    }

    public File getTargetDir()
    {
        return _targetDir;
    }

    public void setTargetDir(File targetDir)
    {
        _targetDir = targetDir;
    }

    public boolean isAllowLenientLuceneProcessing()
    {
        return _allowLenientLuceneProcessing;
    }

    public void setAllowLenientLuceneProcessing(boolean allowLenientLuceneProcessing)
    {
        _allowLenientLuceneProcessing = allowLenientLuceneProcessing;
    }

    public static void prepareLuceneIndex(File vcf, File indexDir, Logger log, List<String> infoFieldsForFullTextSearch, boolean allowLenientLuceneProcessing) throws PipelineJobException
    {
        log.debug("Generating VCF full text index for file: " + vcf.getName());

        DISCVRSeqRunner runner = new DISCVRSeqRunner(log);
        if (!runner.jarExists())
        {
            log.error("Unable to find DISCVRSeq.jar, skipping lucene index creation");
            return;
        }

        if (indexDir.exists())
        {
            try
            {
                FileUtils.deleteDirectory(indexDir);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        List<String> args = runner.getBaseArgs("VcfToLuceneIndexer");
        args.add("-V");
        args.add(vcf.getPath());

        args.add("-O");
        args.add(indexDir.getPath());

        if (allowLenientLuceneProcessing)
        {
            args.add("--validation-stringency");
            args.add("LENIENT");
        }

        infoFieldsForFullTextSearch = infoFieldsForFullTextSearch.stream().sorted().toList();
        for (String field : infoFieldsForFullTextSearch)
        {
            args.add("-IF");
            args.add(field);
        }

        args.add("--allow-missing-fields");

        args.add("--index-stats");
        args.add(getExpectedLocationOfLuceneIndexStats(indexDir).getPath());

        if (allowLenientLuceneProcessing)
        {
            args.add("--validation-stringency");
            args.add("LENIENT");
        }

        Integer threads = SequencePipelineService.get().getMaxThreads(log);
        if (threads != null)
        {
            args.add("--threads");
            args.add(threads.toString());
        }

        runner.execute(args);

        if (!SystemUtils.IS_OS_WINDOWS)
        {
            try
            {
                log.debug("Updating file permissions");
                recursivelyChangeDirectoryPermissions(indexDir);
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        File fieldFile = getFieldListFile(indexDir);
        try (PrintWriter writer = PrintWriters.getPrintWriter(fieldFile))
        {
            infoFieldsForFullTextSearch.forEach(writer::println);
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    public static File getExpectedLocationOfLuceneIndexStats(File indexDir)
    {
        return new File(indexDir.getPath() + ".stats.txt");
    }

    private static void recursivelyChangeDirectoryPermissions(File f) throws IOException
    {
        if (f.isDirectory())
        {
            Runtime.getRuntime().exec(new String[]{"chmod", "775", f.getPath()});

            File[] children = f.listFiles();
            if (children != null)
            {
                for (File child : children)
                {
                    recursivelyChangeDirectoryPermissions(child);
                }
            }
        }
        else
        {
            Runtime.getRuntime().exec(new String[]{"chmod", "664", f.getPath()});
        }
    }

    public static File getFieldListFile(File indexDir)
    {
        return new File(indexDir, "fieldList.txt");
    }
}
