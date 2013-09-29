/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

package org.labkey.sequenceanalysis;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.sequenceanalysis.analysis.AASnpByCodonAggregator;
import org.labkey.sequenceanalysis.analysis.AASnpByReadAggregator;
import org.labkey.sequenceanalysis.analysis.AlignmentAggregator;
import org.labkey.sequenceanalysis.analysis.AvgBaseQualityAggregator;
import org.labkey.sequenceanalysis.analysis.BamIterator;
import org.labkey.sequenceanalysis.analysis.NtCoverageAggregator;
import org.labkey.sequenceanalysis.analysis.NtSnpByPosAggregator;
import org.labkey.sequenceanalysis.model.AnalysisModel;
import org.labkey.sequenceanalysis.pipeline.SequencePipelineSettings;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.FastqcRunner;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class SequenceAnalysisController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SequenceAnalysisController.class);

    public SequenceAnalysisController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class FastqcReportAction extends SimpleViewAction<FastqcForm>
    {
        @Override
        public ModelAndView getView(FastqcForm form, BindException errors) throws Exception
        {
            if (form.getFilenames() == null && form.getDataIds() == null)
                errors.reject("Must provide a filename or Exp data Ids");

            //resolve files
            List<File> files = new ArrayList<>();

            String html = "";

            if (form.getFilenames() != null)
            {
                for (String fn : form.getFilenames())
                {
                    WebdavResource r = WebdavService.get().getRootResolver().lookup(Path.parse(fn));
                    if (r.getFile().exists())
                    {
                        files.add(r.getFile());
                    }
                }

            }

            if (form.getDataIds() != null)
            {
                for (int id : form.getDataIds())
                {
                    ExpData data = ExperimentService.get().getExpData(id);
                    if (data != null && data.getContainer().hasPermission(getUser(), ReadPermission.class))
                    {
                        if (data.getFile().exists())
                            files.add(data.getFile());
                    }
                }
            }

            if (form.getReadsets() != null)
            {
                for (int id : form.getReadsets())
                {
                    SequenceReadset rs = SequenceReadset.getFromId(getContainer(), id);
                    files.addAll(rs.getExpDatasForReadset(getUser()));
                }
            }

            if (files.size() == 0)
            {
                return new HtmlView("Error: either no files provided or the files did not exist on the server");
            }

            FastqcRunner runner = new FastqcRunner();
            try
            {
                runner.execute(files);
            }
            catch (FileNotFoundException e)
            {
                return new HtmlView("Error: " + e.getMessage());
            }

            return new HtmlView("FastQC Report", runner.processOutput(getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("FastQC Report"); //necessary to set page title, it seems
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @IgnoresTermsOfUse
    public class DownloadFastqImageAction extends ExportAction<FastqImageForm>
    {
        public void export(FastqImageForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            File parentDir = new File(FileUtil.getTempDirectory(), form.getDirectory());
            parentDir = new File(parentDir, form.getFileName());
            parentDir = new File(parentDir, "Images");

            File imageFile = new File(parentDir, form.getImage());
            imageFile = FileUtil.getAbsoluteCaseSensitiveFile(imageFile);

            if (!NetworkDrive.exists(imageFile))
            {
                throw new FileNotFoundException("Could not find file: " + imageFile.getPath());
            }

            if (parentDir.listFiles() == null)
            {
                throw new FileNotFoundException("Unable to list the contents of folder: " + parentDir.getPath());
            }

            PageFlowUtil.streamFile(response, imageFile, false);

            //the file will be recreated, so delete upon running
            FileUtils.deleteQuietly(imageFile);

            //if the folder if empty, remove it too.  other simultaneous requests might have deleted this folder before we get to it
            if (parentDir != null && parentDir.exists())
            {
                File[] children = parentDir.listFiles();
                if (children != null && children.length == 0)
                {
                    FileUtils.deleteQuietly(parentDir); //the Images folder
                    File parent = parentDir.getParentFile();
                    FileUtils.deleteQuietly(parent); //the FASTQ file's folder

                    if (parent != null && parent.getParentFile() != null)
                    {
                        File[] children2 = parent.getParentFile().listFiles();
                        if (children2 != null && children2.length == 0)
                            FileUtils.deleteQuietly(parent.getParentFile()); //the FASTQ file's folder
                    }
                }
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @IgnoresTermsOfUse
    public class ConvertTextToFileAction extends ExportAction<ConvertTextToFileForm>
    {
        public void export(ConvertTextToFileForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String text = form.getText();

            if (text == null)
            {
                errors.reject(ERROR_MSG, "Need to provide text");
                return;
            }
            if (form.getFileName() == null)
            {
                errors.reject(ERROR_MSG, "Need to provide a filename");
                return;
            }

            Map<String, String> headers = new HashMap<>();

            PageFlowUtil.prepareResponseForFile(response, headers, form.getFileName(), true);
            response.getOutputStream().print(text);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class MigrateLegacySnpDataAction extends ConfirmAction<Object>
    {
        public void validateCommand(Object form, Errors errors)
        {
            if (!ContainerManager.getRoot().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException("User must be a site admin");
        }

        @Override
        public ModelAndView getConfirmView(Object form, BindException errors) throws Exception
        {
            return new HtmlView("This allows a site admin to migrate legacy SNP data into the newer format.  Depending on the size of your DB, it can take a long time to run.  Do you want to continue?");
        }

        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            try
            {
                //first condense / delete NT SNPs:
                TableInfo ntSnps = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_NT_SNPS);
                SqlExecutor sql = new SqlExecutor(ntSnps.getSchema());

                sql.execute("INSERT INTO sequenceanalysis.nt_snps_by_pos " +
                    "(analysis_id, ref_nt_id, ref_nt_name, ref_nt_position, ref_nt_insert_index, ref_nt, q_nt, container, createdby, created, readcount, depth, adj_depth, pct)\n" +
                    "(SELECT\n" +
                    "n.*,\n" +
                    "c.depth,\n" +
                    "c.adj_depth,\n" +
                    "(cast(n.readcount as float) / c.adj_depth) as pct\n" +
                    "FROM (\n" +
                    "  SELECT analysis_id, ref_nt_id, null as ref_nt_name, ref_nt_position, ref_nt_insert_index, ref_nt, q_nt, container, createdby, created, count(*) as readcount\n" +
                    "  FROM sequenceanalysis.nt_snps\n" +
                    "  GROUP BY analysis_id, ref_nt_id, ref_nt_position, ref_nt_insert_index, ref_nt, q_nt, container, createdby, created\n" +
                    ") n\n" +
                    "LEFT JOIN sequenceanalysis.sequence_coverage c ON (c.analysis_id = n.analysis_id AND c.ref_nt_id = n.ref_nt_id AND c.ref_nt_position = n.ref_nt_position AND c.ref_nt_insert_index = n.ref_nt_insert_index)\n" +
                    ");\n" +
                    "\n");

                sql.execute("TRUNCATE sequenceanalysis." + ntSnps.getSelectName());

                //now AA SNPs
                sql.execute("INSERT INTO sequenceanalysis.aa_snps_by_codon " +
                "()\n" +
                "select *,\n" +
                "case\n" +
                "        when aa.depth > 0 THEN round((aa.total_reads / aa.depth)*100, 2)\n" +
                "    ELSE 0\n" +
                "    END as percent,\n" +
                "case\n" +
                "        when aa.q_aa = ':' then null\n" +
                "    when aa.depth > 0 THEN round((aa.total_reads / (aa.depth-aa.incompletecodons))*100, 2)\n" +
                "    ELSE 0\n" +
                "    END as adj_percent,\n" +
                "        (aa.depth-aa.incompletecodons) as adj_depth\n" +
                "    FROM (\n" +
                "            SELECT\n" +
                "            aa_inner.analysis_id,\n" +
                "            aa_inner.ref_nt_id,\n" +
                "            aa_inner.ref_aa_id,\n" +
                "            aa_inner.ref_aa,\n" +
                "            aa_inner.ref_aa_position,\n" +
                "            aa_inner.ref_aa_insert_index,\n" +
                "            aa_inner.ref_nt_positions,\n" +
                "            aa_inner.q_aa,\n" +
                "            aa_inner.q_codon,\n" +
                "            aa_inner.total_reads,\n" +
                "            cast(case when aa_inner.q_aa = ':' then 0 else aa_inner.total_reads end as integer) as adj_total_reads,\n" +
                "aa_inner.depth,\n" +
                "        (select\n" +
                "count(distinct alignment_id)\n" +
                "from sequenceanalysis.aa_snps\n" +
                "        WHERE\n" +
                "aa_snps.analysis_id = aa_inner.analysis_id AND\n" +
                "aa_snps.ref_nt_id = aa_inner.ref_nt_id AND\n" +
                "aa_snps.ref_aa_id = aa_inner.ref_aa_id AND\n" +
                "aa_snps.ref_aa_position = aa_inner.ref_aa_position AND\n" +
                "aa_snps.ref_aa_insert_index = aa_inner.ref_aa_insert_index AND\n" +
                "aa_snps.q_aa = ':'\n" +
                ") as incompletecodons\n" +
                "\n" +
                "FROM (\n" +
                "        SELECT\n" +
                "        aa.analysis_id,\n" +
                "        aa.ref_aa_id,\n" +
                "        aa.ref_nt_id,\n" +
                "        group_concat(DISTINCT nt.ref_nt_position) AS ref_nt_positions,\n" +
                "        aa.ref_aa,\n" +
                "        aa.ref_aa_position,\n" +
                "        aa.ref_aa_insert_index,\n" +
                "        aa.q_aa,\n" +
                "        aa.q_codon,\n" +

                "avg(sc.depth) as depth,\n" +
                "cast(count(distinct aa.alignment_id) as float) as total_reads\n" +
                "\n" +
                "FROM sequenceanalysis.aa_snps aa\n" +
                "JOIN sequenceanalysis.nt_snps nt\n" +
                "on (aa.nt_snp_id = nt.rowid)\n" +
                "\n" +
                "JOIN sequenceanalysis.sequence_coverage sc\n" +
                "on (\n" +
                "        sc.analysis_id=aa.analysis_id AND\n" +
                "        sc.ref_nt_id=aa.ref_nt_id AND\n" +
                "        sc.ref_nt_position = nt.ref_nt_position AND\n" +
                "        --sc.ref_nt_insert_index = aa.nt_snp_id.ref_nt_insert_index\n" +
                "        sc.ref_nt_insert_index = 0\n" +
                ")\n" +
                "\n" +
                "        --WHERE aa.status=true\n" +
                "\n" +
                "GROUP BY\n" +
                "aa.analysis_id,\n" +
                "        aa.ref_nt_id,\n" +
                "        aa.ref_aa_id,\n" +
                "        aa.ref_aa_position,\n" +
                "        aa.ref_aa_insert_index,\n" +
                "        aa.ref_aa,\n" +
                "        aa.q_aa,\n" +
                "        aa.q_codon\n" +
                ") aa_inner\n" +
                ") aa\n");

                sql.execute("TRUNCATE sequenceanalysis." + ntSnps.getSelectName());

                return true;
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
        }

        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteRecordsAction extends ConfirmAction<QueryForm>
    {
        private TableInfo _table;

        public void validateCommand(QueryForm form, Errors errors)
        {
            if (form.getSchema() == null)
            {
                errors.reject("No schema provided");
                return;
            }

            if (form.getQueryName() == null)
            {
                errors.reject("No queryName provided");
                return;
            }

            _table = SequenceAnalysisSchema.getInstance().getSchema().getTable(form.getQueryName());
            if (_table == null)
            {
                errors.reject("Unknown table: " + form.getQueryName());
                return;
            }

            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
            List<Object> keys = new ArrayList<Object>(ids);
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_table.getPkColumns().get(0).getColumnName()), StringUtils.join(keys, ";"), CompareType.IN);

            if (_table.getColumn("container") != null)
            {
                TableSelector ts = new TableSelector(_table, Collections.singleton("container"), filter, null);
                ts.forEach(new TableSelector.ForEachBlock<ResultSet>(){
                    public void exec(ResultSet rs) throws SQLException
                    {
                        Container c = ContainerManager.getForId(rs.getString("container"));
                        if (!c.hasPermission(getUser(), DeletePermission.class))
                            throw new UnauthorizedException("User does not have delete permission on folder: " + c.getTitle());
                    }
                });
            }
        }

        @Override
        public ModelAndView getConfirmView(QueryForm form, BindException errors) throws Exception
        {
            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
            List<Object> keys = new ArrayList<Object>(ids);

            StringBuilder msg = new StringBuilder("Are you sure you want to delete the ");
            if (SequenceAnalysisSchema.TABLE_ANALYSES.equals(_table.getName()))
            {
                msg.append("analyses " + StringUtils.join(keys, ", ") + "?  This will delete the analyses, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENTS, "alignments", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY, "alignment records", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNPS, "NT SNPs", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNPS, "AA SNPs", keys, "analysis_id");
            }
            else if (SequenceAnalysisSchema.TABLE_READSETS.equals(_table.getName()))
            {
                msg.append("readsets " + StringUtils.join(keys, ", ") + "?  This will delete the readsets, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ANALYSES, "analyses", keys, "readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENTS, "alignments", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY, "alignment records", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNPS, "NT SNPs", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNPS, "AA SNPs", keys, "analysis_id/readset");
            }
            else if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equals(_table.getName()))
            {
                msg.append("NT reference sequences " + StringUtils.join(keys, ", ") + "?  This will delete the reference sequences, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES, "reference AA sequences", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_FEATURES, "NT features", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_COVERAGE, "coverage positions", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENTS, "alignments", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION, "alignment records", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNPS, "NT SNPs", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNPS, "AA SNPs", keys, "ref_nt_id");
            }
            else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equals(_table.getName()))
            {
                msg.append("AA reference sequences " + StringUtils.join(keys, ", ") + "?  This will delete the reference sequences, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_FEATURES, "AA features", keys, "ref_aa_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_DRUG_RESISTANCE, "drug resistance mutations", keys, "ref_aa_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "ref_aa_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNPS, "AA SNPs", keys, "ref_aa_id");
            }

            return new HtmlView(msg.toString());
        }

        private void appendTotal(StringBuilder sb, String tableName, String noun, List<Object> keys, String filterCol)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString(filterCol), StringUtils.join(keys, ";"), CompareType.IN);
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(tableName), filter, null);
            long total = ts.getRowCount();
            sb.append("<br>" + total + " " + noun);
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            try
            {
                Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
                List<Integer> rowIds = new ArrayList<>();
                for (String id : ids)
                {
                    rowIds.add(Integer.parseInt(id));
                }

                if (SequenceAnalysisSchema.TABLE_ANALYSES.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteAnalysis(rowIds);
                }
                else if (SequenceAnalysisSchema.TABLE_READSETS.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteReadset(rowIds);
                }
                else if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteRefNtSequence(rowIds);
                }
                else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteRefAaSequence(rowIds);
                }
            }
            catch (Table.OptimisticConflictException e)
            {
                //if someone else already deleted this, no need to throw exception
            }
            return true;
        }

        public URLHelper getSuccessURL(QueryForm form)
        {
            URLHelper url = form.getReturnURLHelper();
            return url != null ? url :
                _table.getGridURL(getContainer()) != null ? _table.getGridURL(getContainer()) :
                getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ValidateReadsetFiles extends ApiAction<ValidateReadsetImportForm>
    {
        public ApiResponse execute(ValidateReadsetImportForm form, BindException errors) throws Exception
        {
            Map<String, Object> resultProperties = new HashMap<>();

            try
            {
                List<Map<String, Object>> fileInfo = new ArrayList<>();
                Set<String> distinctBasenames = new HashSet<>();
                Set<ExpData> datas = new HashSet<>();

                List<String> errorsList = new ArrayList<>();
                if (form.getFileIds() != null)
                {
                    for (Integer id : form.getFileIds())
                    {
                        ExpData data = ExperimentService.get().getExpData(id);
                        if (data != null)
                            datas.add(data);
                        else
                            errorsList.add("Unable to find file with ExpData Id: " + id);
                    }
                }

                if (form.getFileNames() != null)
                {
                    File base = PipelineService.get().getPipelineRootSetting(getContainer()).getRootPath();
                    if (form.getPath() != null)
                        base = new File(base, form.getPath());

                    for (String fileName : form.getFileNames())
                    {
                        File f = new File(base, fileName);
                        ExpData data = ExperimentService.get().getExpDataByURL(f, getContainer());
                        if (data != null)
                        {
                            datas.add(data);
                        }
                        else
                        {
                            Map<String, Object> map = new HashMap<>();
                            map.put("fileName", fileName);
                            map.put("filePath", f.getPath());
                            map.put("container", getContainer().getId());
                            map.put("containerPath", getContainer().getPath());
                            String basename = SequenceTaskHelper.getMinimalBaseName(fileName);
                            map.put("basename", basename);

                            if (distinctBasenames.contains(basename))
                            {
                                errorsList.add("File has a duplicate basename: " + basename);
                                map.put("error", "File has a duplicate basename: " + basename);
                            }

                            if (!f.exists())
                            {
                                String msg = "File does not exist: " + f.getPath();
                                errorsList.add(msg);
                                map.put("error", msg);
                            }

                            fileInfo.add(map);
                        }
                    }
                }

                if (datas.size() > 0)
                {
                    for (ExpData d : datas)
                    {
                        //we perform 2 checks.  first make sure no sequence readsets start with a run using this file as an input
                        //next we make sure no readset has this file as an input, which would actually refer to the output of the run that created it
                        Map<String, Object> map = new HashMap<>();
                        map.put("fileName", d.getFile().getName());
                        map.put("filePath", d.getFile().getPath());
                        map.put("dataId", d.getRowId());
                        map.put("container", getContainer().getId());
                        map.put("containerPath", getContainer().getPath());

                        String basename = SequenceTaskHelper.getMinimalBaseName(d.getFile());
                        map.put("basename", basename);
                        if (distinctBasenames.contains(basename))
                        {
                            errorsList.add("File has a duplicate basename: " + basename);
                            map.put("error", "File has a duplicate basename: " + basename);
                        }

                        if (!d.getFile().exists())
                        {
                            String msg = "File does not exist: " + d.getFile().getPath();
                            errorsList.add(msg);
                            map.put("error", msg);
                        }

                        SQLFragment sql = new SQLFragment("select i.dataid from exp.datainput i " +
                                "left join exp.protocolapplication p on (i.targetapplicationid = p.rowid) " +
                                "left join sequenceanalysis.sequence_readsets r ON (r.runid = p.runid) " +
                                "where i.role = ? and (p.name = ? OR p.name = ?) and i.dataid = ? and r.rowid IS NOT NULL", SequenceTaskHelper.SEQUENCE_DATA_INPUT_NAME, "Run inputs", "Run outputs", d.getRowId());
                        SqlSelector ss = new SqlSelector(ExperimentService.get().getSchema(), sql);
                        if (ss.getRowCount() > 0)
                        {
                            String msg = "File has already been used as an input for readsets";
                            errorsList.add(msg);
                            map.put("error", msg);
                        }

                        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READSETS);

                        //forward reads
                        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("fileid"), d.getRowId());
                        Container c = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
                        filter.addClause(ContainerFilter.CURRENT.createFilterClause(SequenceAnalysisSchema.getInstance().getSchema(), FieldKey.fromString("container"), c));
                        TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), filter, null);
                        Integer[] readsets1 = ts.getArray(Integer.class);

                        //reverse reads
                        SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("fileid"), d.getRowId());
                        filter2.addClause(ContainerFilter.CURRENT.createFilterClause(SequenceAnalysisSchema.getInstance().getSchema(), FieldKey.fromString("container"), c));
                        TableSelector ts2 = new TableSelector(ti, Collections.singleton("rowid"), filter2, null);
                        Integer[] readsets2 = ts2.getArray(Integer.class);


                        if (readsets1.length > 0 || readsets2.length > 0)
                        {
                            Set<Integer> ids = new HashSet<>();
                            ids.addAll(Arrays.asList(readsets1));
                            ids.addAll(Arrays.asList(readsets2));

                            if (ids.size() > 0)
                            {
                                String msg = "File is already used in existing readsets (" + StringUtils.join(new ArrayList<>(ids), ", ") + "): " + d.getFile().getName();
                                errorsList.add(msg);
                                map.put("error", msg);
                            }
                        }

                        fileInfo.add(map);
                    }
                }

                resultProperties.put("success", true);
                resultProperties.put("fileInfo", fileInfo);
                resultProperties.put("validationErrors", errorsList);
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                errors.reject(ERROR_MSG, e.getMessage());
            }
            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class AnalyzeBamAction extends ApiAction<AnalyzeBamForm>
    {
        public ApiResponse execute(AnalyzeBamForm form, BindException errors) throws Exception
        {
            Map<String, Object> resultProperties = new HashMap<>();

            if (form.getAnalysisIds() == null || form.getAnalysisIds().length == 0)
            {
                errors.reject(ERROR_MSG, "No analysisIds provided");
                return null;
            }

            if (form.getAggregators() == null || form.getAggregators().length == 0)
            {
                errors.reject(ERROR_MSG, "No aggregators provided");
                return null;
            }
            List<String> aggregatorTypes = new ArrayList<>();
            aggregatorTypes.addAll(Arrays.asList(form.getAggregators()));

            List<Integer> analysisIds = Arrays.asList(form.getAnalysisIds());
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_ANALYSES), new SimpleFilter(FieldKey.fromString("rowid"), analysisIds, CompareType.IN), null);
            AnalysisModel[] models = ts.getArray(AnalysisModel.class);
            if (models == null)
            {
                errors.reject(ERROR_MSG, "Unable selected analyses");
                return null;
            }

            Logger log = Logger.getLogger(SequenceAnalysisController.class);

            for (AnalysisModel m : models)
            {
                File inputFile = m.getAlignmentData().getFile();
                File refFile = m.getReferenceLibraryData().getFile();
                Container c = ContainerManager.getForId(m.getContainer());

                log.info("Calculating avg quality scores");
                AvgBaseQualityAggregator avg = new AvgBaseQualityAggregator(inputFile, refFile, log);

                if (form.getRefName() != null && form.getStart() > 0 && form.getStop() > 0)
                {
                    log.info("Using reference: " + form.getRefName() + " and the interval: " + form.getStart() + " / " + form.getStop());
                    avg.calculateAvgQuals(form.getRefName(), form.getStart(), form.getStop());
                }
                else
                {
                    avg.calculateAvgQuals();
                }

                log.info("\tCalculation complete");

                log.info("Inspecting alignments in BAM");
                BamIterator bi = new BamIterator(inputFile, refFile, log);

                //NOTE: this is a hack for testing purposes.  i need a registry mechanism
                List<AlignmentAggregator> aggregators = new ArrayList<>();
                NtCoverageAggregator coverage = null;

                Map<String, String> params = new HashMap<>();
                if (form.getMinAvgSnpQual() > 0)
                    params.put("snp.minAvgSnpQual", String.valueOf(form.getMinAvgSnpQual()));
                if (form.getMinAvgDipQual() > 0)
                    params.put("snp.minAvgDipQual", String.valueOf(form.getMinAvgDipQual()));
                if (form.getMinSnpQual() > 0)
                    params.put("snp.minSnpQual", String.valueOf(form.getMinSnpQual()));
                if (form.getMinDipQual() > 0)
                    params.put("snp.minDipQual", String.valueOf(form.getMinDipQual()));

                SequencePipelineSettings settings = new SequencePipelineSettings(params);

                if (aggregatorTypes.contains("coverage"))
                {
                    coverage = new NtCoverageAggregator(settings, log, avg);
                    aggregators.add(coverage);
                }

                if (aggregatorTypes.contains("ntbypos"))
                {
                    NtSnpByPosAggregator ntSnp = new NtSnpByPosAggregator(settings, log, avg);
                    if (coverage == null)
                        coverage = new NtCoverageAggregator(settings, log, avg);

                    ntSnp.setCoverageAggregator(coverage);
                    aggregators.add(ntSnp);
                }

                if (aggregatorTypes.contains("aabycodon"))
                {
                    AASnpByCodonAggregator aaCodon = new AASnpByCodonAggregator(settings, log, avg);
                    if (coverage == null)
                        coverage = new NtCoverageAggregator(settings, log, avg);

                    aaCodon.setCoverageAggregator(coverage);
                    aggregators.add(aaCodon);
                }

                bi.addAggregators(aggregators);

                if (form.getRefName() != null && form.getStart() > 0 && form.getStop() > 0)
                    bi.iterateReads(form.getRefName(), form.getStart(), form.getStop());
                else
                    bi.iterateReads();

                for (AlignmentAggregator agg : aggregators)
                {
                    agg.saveToDb(getUser(), c, m);
                }
            }

            resultProperties.put("success", true);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    public static class AnalyzeBamForm
    {
        private Integer[] _analysisIds;
        private String[] _analyses;
        private String[] _aggregators;

        private int _minAvgSnpQual = 0;
        private int _minSnpQual = 0;
        private int _minAvgDipQual = 0;
        private int _minDipQual = 0;

        private int _start;
        private int _stop;
        private String _refName;

        public Integer[] getAnalysisIds()
        {
            return _analysisIds;
        }

        public void setAnalysisIds(Integer[] analysisIds)
        {
            _analysisIds = analysisIds;
        }

        public String[] getAnalyses()
        {
            return _analyses;
        }

        public void setAnalyses(String[] analyses)
        {
            _analyses = analyses;
        }

        public String[] getAggregators()
        {
            return _aggregators;
        }

        public void setAggregators(String[] aggregators)
        {
            _aggregators = aggregators;
        }

        public int getMinAvgSnpQual()
        {
            return _minAvgSnpQual;
        }

        public void setMinAvgSnpQual(int minAvgSnpQual)
        {
            _minAvgSnpQual = minAvgSnpQual;
        }

        public int getMinSnpQual()
        {
            return _minSnpQual;
        }

        public void setMinSnpQual(int minSnpQual)
        {
            _minSnpQual = minSnpQual;
        }

        public int getMinAvgDipQual()
        {
            return _minAvgDipQual;
        }

        public void setMinAvgDipQual(int minAvgDipQual)
        {
            _minAvgDipQual = minAvgDipQual;
        }

        public int getMinDipQual()
        {
            return _minDipQual;
        }

        public void setMinDipQual(int minDipQual)
        {
            _minDipQual = minDipQual;
        }

        public int getStart()
        {
            return _start;
        }

        public void setStart(int start)
        {
            _start = start;
        }

        public int getStop()
        {
            return _stop;
        }

        public void setStop(int stop)
        {
            _stop = stop;
        }

        public String getRefName()
        {
            return _refName;
        }

        public void setRefName(String refName)
        {
            _refName = refName;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetAASnps extends ApiAction<AASNPForm>
    {
        public ApiResponse execute(AASNPForm form, BindException errors) throws Exception
        {
            Map<String, Object> resultProperties = new HashMap<>();
            if (form.getAnalysisId() == 0)
            {
                resultProperties.put("success", false);
                resultProperties.put("exception", "Must provide an analysis ID");
                return new ApiSimpleResponse(resultProperties);
            }

            TableInfo ti = SequenceAnalysisSchema.getInstance().getSchema().getTable(SequenceAnalysisSchema.TABLE_ANALYSES);
            TableSelector ts = new TableSelector(ti, Table.ALL_COLUMNS, new SimpleFilter(FieldKey.fromString("analysis_id"), form.getAnalysisId()), null);
            AnalysisModel[] records = ts.getArray(AnalysisModel.class);
            if (records.length != 1)
            {
                resultProperties.put("success", false);
                resultProperties.put("exception", "Unknown analysis ID: " + form.getAnalysisId());
                return new ApiSimpleResponse(resultProperties);
            }

            AnalysisModel model = records[0];

            ExpData bam = model.getAlignmentData();
            if (bam == null || !bam.getFile().exists())
            {
                resultProperties.put("success", false);
                resultProperties.put("exception", "Unable to find BAM file, or the file does not exist");
                return new ApiSimpleResponse(resultProperties);
            }

            ExpData ref = model.getReferenceLibraryData();
            if (ref == null || !ref.getFile().exists())
            {
                resultProperties.put("success", false);
                resultProperties.put("exception", "Unable to find reference library FASTA, or the file does not exist");
                return new ApiSimpleResponse(resultProperties);
            }

            Logger log = Logger.getLogger(BamIterator.class);
            BamIterator bi = new BamIterator(bam.getFile(), ref.getFile(), log);

            Map<String, String> params = new HashMap<>();
            SequencePipelineSettings settings = new SequencePipelineSettings(params);
            AvgBaseQualityAggregator avg = new AvgBaseQualityAggregator(bam.getFile(), ref.getFile(), log);
            AASnpByReadAggregator aaAggregator = new AASnpByReadAggregator(settings, log, avg);
            bi.addAggregators(Collections.singletonList((AlignmentAggregator)aaAggregator));

            String refName = SequenceAnalysisManager.get().getNTRefForAARef(form.getRefAaId());
            bi.iterateReads(refName, form.getStart(), form.getEnd());
            List<Map<String, Object>> rows = aaAggregator.getResults(getUser(), getContainer(), model);

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @IgnoresTermsOfUse
    public class MergeFastqFilesAction extends ExportAction<MergeFastqFilesForm>
    {
        public void export(MergeFastqFilesForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            if (form.getDataIds() == null || form.getDataIds().length == 0)
            {
                throw new NotFoundException("No files provided");
            }

            String filename = form.getZipFileName();
            if (filename == null)
            {
                throw new NotFoundException("Must provide a filename for the archive");
            }
            filename += ".fastq.gz";

            Set<File> files = new HashSet<>();
            for (Integer id : form.getDataIds())
            {
                ExpData d = ExperimentService.get().getExpData(id);
                if (d == null)
                {
                    throw new NotFoundException("Unable to find ExpData for ID: " + id);
                }
                if (!d.getContainer().hasPermission(getUser(), ReadPermission.class))
                {
                    throw new SecurityException("You do not have read permissions for the file with ID: " + id);
                }
                if (!FastqUtils.FqFileType.isType(d.getFile()))
                {
                    continue;
                }
                files.add(d.getFile());
            }

            PageFlowUtil.prepareResponseForFile(response, Collections.<String, String>emptyMap(), filename, true);
            FileInputStream in = null;
            GZIPInputStream gis = null;
            FileType gz = new FileType(".gz");
            try
            {
                for (File f : files)
                {
                    if (!f.exists())
                    {
                        throw new NotFoundException("File " + f.getPath() + " does not exist");
                    }
                    in = new FileInputStream(f);

                    if (!gz.isType(f))
                    {
                        gis = new GZIPInputStream(in);
                        IOUtils.copy(gis, response.getOutputStream());
                    }
                    else
                    {
                        IOUtils.copy(in, response.getOutputStream());
                    }
                }
            }
            finally
            {
                if (in != null)
                    in.close();
                if (gis != null)
                    gis.close();
            }
        }
    }

    public static class MergeFastqFilesForm extends ReturnUrlForm
    {
        private int[] _dataIds;
        private String _zipFileName;

        public int[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(int[] dataIds)
        {
            _dataIds = dataIds;
        }

        public String getZipFileName()
        {
            return _zipFileName;
        }

        public void setZipFileName(String zipFileName)
        {
            _zipFileName = zipFileName;
        }
    }

    public static class ConvertTextToFileForm
    {
        private String _text;
        private String _fileName;

        public String getText()
        {
            return _text;
        }

        public void setText(String text)
        {
            _text = text;
        }

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }
    }

    public static class FastqImageForm
    {
        String _image;
        String _fileName;
        String _directory;

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }

        public String getDirectory()
        {
            return _directory;
        }

        public void setDirectory(String directory)
        {
            _directory = directory;
        }

        public String getImage()
        {
            return _image;
        }

        public void setImage(String image)
        {
            _image = image;
        }
    }

    public static class FastqcForm
    {
        private String[] _filenames;
        private Integer[] _dataIds;
        private Integer[] _readsets;

        public void setFilenames(String... filename)
        {
            _filenames = filename;
        }
        public String[] getFilenames()
        {
            return _filenames;
        }

        public Integer[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(Integer... dataIds)
        {
            _dataIds = dataIds;
        }

        public Integer[] getReadsets()
        {
            return _readsets;
        }

        public void setReadsets(Integer... readsets)
        {
            _readsets = readsets;
        }
    }

    public static class AASNPForm
    {
        private int _analysisId;
        private boolean _saveResults = false;
        private int _refAaId;
        private Integer _start;
        private Integer _end;

        public int getAnalysisId()
        {
            return _analysisId;
        }

        public void setAnalysisId(int analysisId)
        {
            _analysisId = analysisId;
        }

        public int getRefAaId()
        {
            return _refAaId;
        }

        public void setRefAaId(int refAaId)
        {
            _refAaId = refAaId;
        }

        public Integer getStart()
        {
            return _start;
        }

        public void setStart(Integer start)
        {
            _start = start;
        }

        public Integer getEnd()
        {
            return _end;
        }

        public void setEnd(Integer end)
        {
            _end = end;
        }

        public boolean isSaveResults()
        {
            return _saveResults;
        }

        public void setSaveResults(boolean saveResults)
        {
            _saveResults = saveResults;
        }
    }

    public static class ValidateReadsetImportForm
    {
        String path;
        String[] _fileNames;
        Integer[] _fileIds;

        public String[] getFileNames()
        {
            return _fileNames;
        }

        public void setFileNames(String[] fileNames)
        {
            _fileNames = fileNames;
        }

        public Integer[] getFileIds()
        {
            return _fileIds;
        }

        public void setFileIds(Integer[] fileIds)
        {
            _fileIds = fileIds;
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = path;
        }
    }
}
