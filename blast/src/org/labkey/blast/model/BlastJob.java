package org.labkey.blast.model;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.security.User;
import org.labkey.blast.BLASTManager;
import org.labkey.blast.BLASTSchema;
import org.labkey.blast.BLASTWrapper;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/20/2014
 * Time: 3:51 PM
 */
public class BlastJob    
{
    private int _rowid;
    private String _databaseId;
    private String _title;
    private Map<String, Object> _params;
    private boolean _saveResults;
    private boolean _hasRun;
    private String _objectid;
    private String _container;
    private int _createdBy;
    private Date _created;
    private int _modifiedBy;
    private Date _modified;
    private String _jobId;
    private Integer _htmlFile;
    
    public BlastJob()
    {
        
    }

    public int getRowid()
    {
        return _rowid;
    }

    public void setRowid(int rowid)
    {
        _rowid = rowid;
    }

    public String getDatabaseId()
    {
        return _databaseId;
    }

    public void setDatabaseId(String databaseId)
    {
        _databaseId = databaseId;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public Map<String, Object> getParams()
    {
        return Collections.unmodifiableMap(_params);
    }

    public void setParams(String params)
    {
        if (params == null)
            _params = null;

        _params = new HashMap<>(new JSONObject(params));
    }

    public void addParam(String name, String value)
    {
        if (_params == null)
            _params = new HashMap<>();

        _params.put(name, value);
    }

    public Map<String, Object> getParamMap()
    {
        Map<String, Object> ret = new HashMap<>(_params);
        if (ret.containsKey("outputFmt"))
        {
            ret.remove("outputFmt");
        }

        return ret;
    }

    public boolean isSaveResults()
    {
        return _saveResults;
    }

    public void setSaveResults(boolean saveResults)
    {
        _saveResults = saveResults;
    }

    public boolean getHasRun()
    {
        return _hasRun;
    }

    public String getObjectid()
    {
        return _objectid;
    }

    public void setObjectid(String objectid)
    {
        _objectid = objectid;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public boolean isHasRun()
    {
        return _hasRun;
    }

    public void setHasRun(boolean hasRun)
    {
        _hasRun = hasRun;
    }

    public String getJobId()
    {
        return _jobId;
    }

    public void setJobId(String jobId)
    {
        _jobId = jobId;
    }

    public Integer getHtmlFile()
    {
        return _htmlFile;
    }

    public void setHtmlFile(Integer htmlFile)
    {
        _htmlFile = htmlFile;
    }

    public File getOutputDir()
    {
        Container c = ContainerManager.getForId(_container);
        if (c == null)
        {
            return null;
        }

        File outputDir = BLASTManager.get().getBlastRoot(c, true);
        if (!outputDir.exists())
        {
            outputDir.mkdirs();
        }

        return outputDir;
    }

    public File getExpectedOutputFile()
    {
        return new File(getOutputDir(), "blast-" + _objectid + ".asn");
    }

    public File getExpectedInputFile()
    {
        return new File(getOutputDir(), "blast-" + _objectid + ".input");
    }

    public void setComplete(User u, @Nullable PipelineJob job)
    {
        setHasRun(true);

        if (!isSaveResults())
        {
            if (getExpectedInputFile().exists())
            {
                getExpectedInputFile().delete();
            }
        }
        else
        {
            File output = getExpectedOutputFile();
            Container c = ContainerManager.getForId(_container);
            ExpData data = ExperimentService.get().createData(c, new DataType("BLAST Output"));
            data.setName(output.getName());
            data.setDataFileURI(output.toURI());
            data.save(u);
            setHtmlFile(data.getRowId());
        }

        if (job != null)
        {
            setJobId(job.getJobGUID());
        }
        TableInfo jobs = DbSchema.get(BLASTSchema.NAME).getTable(BLASTSchema.TABLE_BLAST_JOBS);
        Table.update(u, jobs, this, getObjectid());
    }

    public void getResults(BLAST_OUTPUT_FORMAT outputFormat, Writer out) throws IOException
    {
        File output = getExpectedOutputFile();
        if (!output.exists())
        {
            return;
        }

        outputFormat.processResults(output, out);
    }

    public boolean hasError(User u)
    {
        if (getJobId() == null)
        {
            return false;
        }

        Integer jobId = PipelineService.get().getJobId(u, ContainerManager.getForId(getContainer()), getJobId());
        PipelineStatusFile statusFile = PipelineService.get().getStatusFile(jobId);
        return "ERROR".equalsIgnoreCase(statusFile.getStatus());
    }

    public static enum BLAST_OUTPUT_FORMAT
    {
        pairwise("Pairwise", "0", false, null),
        queryAnchoredWithIdentities("Query-anchored showing identities", "1", false, null),
        queryAnchoredNoIdentities("Query-anchored no identities", "2", false, null),
        flatQueryAnchoredWithIdentities("Flat query-anchored, show identities", "3", false, null),
        flatQueryAnchoredNoIdentities("Flat query-anchored, no identities", "4", false, null),
        xml("XML Blast output", "5", false, null),
        tabular("Tabular", "7", false, null),
        alignmentSummary("Summary of perfect matches", "6 qseqid qlen sseqid slen qstart qend sstart send qseq sseq length mismatch ", true, new BlastResultProcessor()
        {
            private Map<String, Set<String>> _perfectHitSummary;

            @Override
            public void processResults(File results, Writer out) throws IOException
            {
                //summary of perfect hits by query seq
                try (StringWriter writer = new StringWriter())
                {
                    _perfectHitSummary = new HashMap<>();

                    new BLASTWrapper().runBlastFormatter(results, BLAST_OUTPUT_FORMAT.alignmentSummary, writer);

                    Scanner scan = new Scanner(writer.getBuffer().toString());
                    while (scan.hasNextLine())
                    {
                        String line = scan.nextLine();
                        if (StringUtils.trimToNull(line) == null)
                        {
                            continue;
                        }

                        String[] tokens = line.split("\t");
                        if (tokens.length < 11)
                        {
                            continue;
                        }

                        int mismatch = Integer.parseInt(tokens[11]);
                        String qname = tokens[0];
                        String sname = tokens[2];
                        if (mismatch == 0)
                        {
                            appendHit(qname, sname);

                        }
                        else
                        {
                            String qseq = tokens[8];
                            String sseq = tokens[9];

                            int i=0;
                            int qmatch = 0;
                            int qmismatch = 0;
                            while (i < qseq.length())
                            {
                                String qbase = qseq.substring(i, i + 1);
                                if (qbase.equalsIgnoreCase(sseq.substring(i, i + 1)) || "N".equalsIgnoreCase(qbase))
                                {
                                    qmatch++;
                                }
                                else
                                {
                                    qmismatch++;
                                }

                                i++;
                            }

                            if (qmismatch == 0)
                            {
                                appendHit(qname, sname);
                            }
                            else
                            {
                                appendHit(qname, null);
                            }
                        }
                    }
                }

                out.write("<br><br><b>Summary of Perfect Hits:</b><br>");
                out.write("<table border=1 cellpadding=\"3\" style=\"border-collapse: collapse;\"><tr><td>Query</td><td># Perfect Hits</td><td>Perfect Hits</td></tr>");
                for (String qname : _perfectHitSummary.keySet())
                {
                    out.write("<tr>");
                    out.write("<td>" + qname + "</td>");
                    out.write("<td>" + _perfectHitSummary.get(qname).size() + "</td>");
                    out.write("<td>" + StringUtils.join(_perfectHitSummary.get(qname), "<br>") + "</td>");

                    out.write("</tr>");
                }

                out.write("</table><br><br>");
                out.write("<hr>");

                out.write("<b>BLAST Output:</b>");
                out.write("<pre>");
                new BLASTWrapper().runBlastFormatter(results, BLAST_OUTPUT_FORMAT.flatQueryAnchoredWithIdentities, out);
                out.write("</pre>");
            }

            private void appendHit(String qname, String sname)
            {
                Set<String> hits = _perfectHitSummary.get(qname);
                if (hits == null)
                {
                    hits = new HashSet<>();
                }

                if (sname != null)
                {
                    hits.add(sname);
                }

                _perfectHitSummary.put(qname, hits);
            }
        });

        private String _label;
        private String _cmd;
        private boolean _supportsHTML;
        private BlastResultProcessor _processor;

        BLAST_OUTPUT_FORMAT(String label, String cmd, boolean supportsHtml, @Nullable BlastResultProcessor processor)
        {
            _label = label;
            _cmd = cmd;
            _supportsHTML = supportsHtml;
            _processor = processor;
        }

        public String getLabel()
        {
            return _label;
        }

        public String getCmd()
        {
            return _cmd;
        }

        public boolean supportsHTML()
        {
            return _supportsHTML;
        }

        public void processResults(File results, Writer out) throws IOException
        {
            if (_processor == null)
            {
                new BLASTWrapper().runBlastFormatter(results, this, out);
            }
            else
            {
                _processor.processResults(results, out);
            }
        }

    }

    public static interface BlastResultProcessor
    {
        public void processResults(File results, Writer out) throws IOException;
    }
}

