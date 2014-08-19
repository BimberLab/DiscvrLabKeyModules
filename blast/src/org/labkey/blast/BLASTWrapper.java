package org.labkey.blast;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.FileType;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
    public static FileType DB_TYPE = new FileType(Arrays.asList("nhr", "nin", "nsq"), "nhr");
    private Logger _log = null;

    public BLASTWrapper()
    {

    }

    public void setLog(Logger log)
    {
        _log = log;
    }

    public File runBlastN(String blastDbGuid, File input, File outputFile, Map<String, Object> params) throws IllegalArgumentException, IOException
    {
        File binDir = BLASTManager.get().getBinDir();
        if (binDir == null || !binDir.exists())
        {
            throw new IllegalArgumentException("BLAST bin dir does not exist");
        }

        File exe = new File(binDir, "blastn" + getExtension());
        if (!exe.exists())
        {
            throw new IllegalArgumentException("Unable to find blastn executable");
        }

        File dbDir = BLASTManager.get().getDatabaseDir();
        if (dbDir == null || !dbDir.exists())
        {
            throw new IllegalArgumentException("BLAST database dir does not exist");
        }

        File db = new File(dbDir, blastDbGuid);

        List<String> args = new ArrayList<>();
        args.add(exe.getPath());

        args.add("-db");
        args.add(db.getPath());

        args.add("-query");
        args.add(input.getPath());

        args.add("-outfmt");
        args.add("1");

        args.add("-html");

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

        execute(args);
        if (!outputFile.exists())
        {
            throw new IOException("Expected file not created: " + outputFile.getPath());
        }

        return outputFile;
    }

    public File createDatabase(String dbName, String title, File fastaFile) throws IllegalArgumentException, IOException
    {
        File binDir = BLASTManager.get().getBinDir();
        if (binDir == null || !binDir.exists())
        {
            throw new IllegalArgumentException("BLAST bin dir does not exist");
        }

        File exe = new File(binDir, "makeblastdb" + getExtension());
        if (!exe.exists())
        {
            throw new IllegalArgumentException("Unable to find makeblastdb executable");
        }

        File dbDir = BLASTManager.get().getDatabaseDir();
        if (dbDir == null || !dbDir.exists())
        {
            throw new IllegalArgumentException("BLAST database dir does not exist");
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

        execute(args);

        File dbFile = new File(outFile.getParentFile(), outFile.getName() + ".nhr");
        if (!dbFile.exists())
        {
            throw new IOException("Expected file not created: " + dbFile.getPath());
        }

        return outFile;
    }

    private void execute(List<String> args) throws IOException
    {
        StringBuffer output = new StringBuffer();

        if (_log != null)
        {
            _log.info("running BLAST program: ");
            _log.info(StringUtils.join(args, " "));
        }

        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        //Map<String, String> env = pb.environment();
        //env.put("BLASTDB", BLASTManager.get().getDatabaseDir().getPath());

        Process p = null;
        try
        {
            p = pb.start();
            try (BufferedReader procReader = new BufferedReader(new InputStreamReader(p.getInputStream())))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    output.append(line);
                    output.append(System.getProperty("line.separator"));
                }
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

//    public static class Parameter
//    {
//        private String _name;
//        private String _label;
//        private String _description;
//        private String _category;
//        private String _defaultValue;
//        private String _xtype;
//
//        public Parameter(String name, String label, String description, String category)
//        {
//            this(name, label, description, category, "textfield");
//        }
//
//        public Parameter(String name, String label, String description, String category, String xtype)
//        {
//            _name = name;
//            _label = label;
//            _category = category;
//            _description = description;
//            _xtype = xtype;
//        }
//
//        public String getName()
//        {
//            return _name;
//        }
//
//        public String getLabel()
//        {
//            return _label;
//        }
//
//        public String getCategory()
//        {
//            return _category;
//        }
//
//        public String getDescription()
//        {
//            return _description;
//        }
//
//        public String getDefaultValue()
//        {
//            return _defaultValue;
//        }
//
//        public String getXtype()
//        {
//            return _xtype;
//        }
//
//        public JSONObject toJSON()
//        {
//            JSONObject json = new JSONObject();
//            json.put("name", _name);
//            json.put("label", _label);
//            json.put("description", _description);
//            json.put("xtype", _xtype);
//
//            return json;
//        }
//    }
}
