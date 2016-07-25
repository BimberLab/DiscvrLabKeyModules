package org.labkey.blast;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.reader.Readers;
import org.labkey.api.util.FileType;
import org.labkey.blast.model.BlastJob;

import java.io.BufferedReader;
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
public class BLASTWrapper
{
    public static FileType DB_TYPE = new FileType(Arrays.asList("nhr", "nin", "nsq", "idx"), "nhr");
    private Logger _log = null;

    public BLASTWrapper()
    {

    }

    public void setLog(Logger log)
    {
        _log = log;
    }

    public File runBlastN(String blastDbGuid, File input, File outputFile, Map<String, Object> params, File binDir, File dbDir) throws IllegalArgumentException, IOException
    {
        if (binDir == null || !binDir.exists())
        {
            throw new IllegalArgumentException("BLAST bin dir does not exist: " + binDir);
        }

        File exe = new File(binDir, "blastn" + getExtension());
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

        execute(args, null);
        if (!outputFile.exists())
        {
            throw new IOException("Expected file not created: " + outputFile.getPath());
        }

        return outputFile;
    }

    public void runBlastFormatter(File inputFile, BlastJob.BLAST_OUTPUT_FORMAT outputFormat, Writer out) throws IllegalArgumentException, IOException
    {
        File binDir = BLASTManager.get().getBinDir();
        if (binDir == null || !binDir.exists())
        {
            throw new IllegalArgumentException("BLAST bin dir does not exist: " + binDir);
        }

        File exe = new File(binDir, "blast_formatter" + getExtension());
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

        execute(args, out);
    }

    public File createDatabase(String dbName, String title, File fastaFile, File dbDir, Logger log) throws IllegalArgumentException, IOException
    {
        File binDir = BLASTManager.get().getBinDir();
        if (binDir == null)
        {
            throw new IllegalArgumentException("BLAST bin dir has not been set, aborting.");
        }
        else if (!binDir.exists())
        {
            throw new IllegalArgumentException("BLAST bin dir does not exist: " + binDir);
        }

        File exe = new File(binDir, "makeblastdb" + getExtension());
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

        execute(args, null);

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
        File indexExe = new File(binDir, "makembindex" + getExtension());
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

        indexArgs.add("-output");
        indexArgs.add(outFile.getPath());

        execute(indexArgs, null);

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

    private void execute(List<String> args, @Nullable Writer out) throws IOException
    {
        StringBuilder output = new StringBuilder();

        if (_log != null)
        {
            _log.info("running BLAST program: ");
            _log.info(StringUtils.join(args, " "));
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        if (out != null)
        {
            pb.redirectErrorStream(true);
        }

        Process p = null;
        try
        {
            p = pb.start();
            if (out == null)
            {
                try (BufferedReader procReader = Readers.getReader(p.getInputStream()))
                {
                    String line;
                    while ((line = procReader.readLine()) != null)
                    {
                        output.append(line);
                        output.append(System.getProperty("line.separator"));
                    }
                }
            }
            else
            {
                IOUtils.copy(p.getInputStream(), out);
            }

            p.waitFor();
        }
        catch (IOException | InterruptedException e)
        {
            throw new IOException(e);
        }
        finally
        {
            if (p != null)
            {
                p.destroy();
            }
        }

        if (_log != null && output.length() > 0)
        {
            _log.info(output.toString());
        }
    }

    private String getExtension()
    {
        return SystemUtils.IS_OS_WINDOWS ? ".exe" : "";
    }
}
