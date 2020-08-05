package org.labkey.blast;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.util.FileType;
import org.labkey.blast.model.BlastJob;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 12:18 PM
 */
public class BLASTWrapper extends AbstractCommandWrapper
{
    public static FileType DB_TYPE = new FileType(Arrays.asList("nhr", "nin", "nsq", "idx"), "nhr");

    public BLASTWrapper(Logger log)
    {
        super(log);
    }

    public File runBlastN(String blastDbGuid, File input, File outputFile, Map<String, Object> params, @Nullable File binDir, File dbDir) throws PipelineJobException, IOException
    {
        File exe = getExe("blastn", binDir);
        if (!exe.exists())
        {
            throw new IllegalArgumentException("Unable to find blastn executable");
        }

        if (dbDir == null || !dbDir.exists())
        {
            throw new IllegalArgumentException("BLAST database dir does not exist: " + dbDir);
        }

        File db = new File(dbDir, blastDbGuid);

        List<String> args = new ArrayList<>();
        args.add(exe.getPath());

        args.add("-db");
        args.add(db.getPath());

        args.add("-query");
        args.add(input.getPath());

        //always produce ASN.  will convert later
        args.add("-outfmt");
        args.add("11");

        args.add("-use_index");
        args.add("true");

        args.add("-index_name");
        args.add(db.getPath());

        if (PipelineJobService.get().getLocationType() != PipelineJobService.LocationType.WebServer)
        {
            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getLogger());
            if (maxThreads != null)
            {
                args.add("-num_threads");
                args.add(maxThreads.toString());
            }
        }

        args.add("-out");
        args.add(outputFile.getPath());

        if (params != null)
        {
            for (String paramName : params.keySet())
            {
                args.add("-" + paramName);
                if (params.get(paramName) != null)
                {
                    args.add(params.get(paramName).toString());
                }
            }
        }

        executeBlast(args, null);
        if (!outputFile.exists())
        {
            throw new PipelineJobException("Expected file not created: " + outputFile.getPath());
        }

        return outputFile;
    }

    public void runBlastFormatter(File inputFile, BlastJob.BLAST_OUTPUT_FORMAT outputFormat, Writer out) throws PipelineJobException, IOException
    {
        File exe = getExe("blast_formatter", null);
        if (!exe.exists())
        {
            throw new IllegalArgumentException("Unable to find blast_formatter executable");
        }

        List<String> args = new ArrayList<>();
        args.add(exe.getPath());

        args.add("-archive");
        args.add(inputFile.getPath());

        args.add("-outfmt");
        args.add(outputFormat.getCmd());

        executeBlast(args, out);
    }

    public File createDatabase(String dbName, String title, File fastaFile, File dbDir, Logger log) throws PipelineJobException, IOException
    {
        File exe = getExe("makeblastdb", null);
        if (!exe.exists())
        {
            throw new IllegalArgumentException("Unable to find makeblastdb executable.  This location is defined through the admin console.");
        }

        if (dbDir == null || !dbDir.exists())
        {
            throw new IllegalArgumentException("BLAST database dir does not exist.  This filepath is defined through the admin console.");
        }

        //delete any existing files from this DB
        File[] preexistingFiles = dbDir.listFiles();
        if (preexistingFiles != null)
        {
            for (File f : preexistingFiles)
            {
                if (f.getName().startsWith(dbName) && !f.getName().endsWith(".fasta"))
                {
                    f.delete();
                }
            }
        }

        List<String> args = new ArrayList<>();
        args.add(exe.getPath());

        args.add("-in");
        args.add(fastaFile.getPath());

        args.add("-input_type");
        args.add("fasta");

        args.add("-dbtype");
        args.add("nucl");

        args.add("-parse_seqids");
        args.add("-hash_index");

        if (title != null)
        {
            args.add("-title");
            args.add(title);
        }

        File outFile = new File(dbDir, dbName);
        args.add("-out");
        args.add(outFile.getPath());

        executeBlast(args, null);

        File[] files = dbDir.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.startsWith(dbName) && !name.endsWith("fasta");
            }
        });

        log.info("total files created: " + files.length);

        if (files.length == 0)
        {
            throw new IOException("No files created");
        }

        //make index
        File indexExe = getExe("makembindex", null);
        if (!indexExe.exists())
        {
            throw new IllegalArgumentException("Unable to find makembindex executable.  This location is defined through the admin console.");
        }

        List<String> indexArgs = new ArrayList<>();
        indexArgs.add(indexExe.getPath());

        indexArgs.add("-input");
        indexArgs.add(outFile.getPath());

        indexArgs.add("-iformat");
        indexArgs.add("blastdb");

        executeBlast(indexArgs, null);

        File[] idxFiles = dbDir.listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return name.startsWith(dbName) && name.endsWith(".idx");
            }
        });

        log.info("total index files: " + idxFiles.length);
        if (idxFiles.length == 0)
        {
            throw new IOException("Index files not created");
        }

        return outFile;
    }

    private void executeBlast(List<String> args, @Nullable Writer writer) throws PipelineJobException, IOException
    {
        StringBuilder output = new StringBuilder();

        if (getLogger() != null)
        {
            getLogger().info("running BLAST program: ");
            getLogger().info(StringUtils.join(args, " "));
        }

        if (writer == null)
        {
            execute(args);
        }
        else
        {
            String ret = executeWithOutput(args);
            writer.write(ret);
        }


        if (getLogger() != null && output.length() > 0)
        {
            getLogger().info(output.toString());
        }
    }

    private String getExtension()
    {
        return SystemUtils.IS_OS_WINDOWS ? ".exe" : "";
    }

    private File getExe(String name, @Nullable File binDir)
    {
        name = name + getExtension();

        File ret;
        if (binDir == null && PipelineJobService.get().getLocationType() == PipelineJobService.LocationType.WebServer)
        {
            binDir = BLASTManager.get().getBinDir();
            if (binDir != null && !binDir.exists())
            {
                throw new IllegalArgumentException("BLAST bin dir does not exist: " + binDir);
            }
        }

        if (binDir != null)
        {
            ret = new File(binDir, name);
        }
        else
        {
            ret = SequencePipelineService.get().getExeForPackage("BLASTPATH", name);
        }

        if (!ret.exists())
        {
            throw new IllegalArgumentException("Unable to find BLAST executable, expected: " + ret.getPath());
        }

        return ret;
    }
}
