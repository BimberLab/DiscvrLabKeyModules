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
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.JFreeChart;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocol;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceFileHandler;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.sequenceanalysis.api.model.AnalysisModel;
import org.labkey.sequenceanalysis.api.model.ReadsetModel;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStep;
import org.labkey.sequenceanalysis.api.pipeline.PipelineStepProvider;
import org.labkey.sequenceanalysis.api.pipeline.SequencePipelineService;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryPipelineJob;
import org.labkey.sequenceanalysis.pipeline.SequenceAlignmentTask;
import org.labkey.sequenceanalysis.pipeline.SequenceAnalysisJob;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.run.analysis.AASnpByCodonAggregator;
import org.labkey.sequenceanalysis.run.analysis.AASnpByReadAggregator;
import org.labkey.sequenceanalysis.run.analysis.AlignmentAggregator;
import org.labkey.sequenceanalysis.run.analysis.AvgBaseQualityAggregator;
import org.labkey.sequenceanalysis.run.analysis.BamIterator;
import org.labkey.sequenceanalysis.run.analysis.NtCoverageAggregator;
import org.labkey.sequenceanalysis.run.analysis.NtSnpByPosAggregator;
import org.labkey.sequenceanalysis.run.util.FastqcRunner;
import org.labkey.sequenceanalysis.run.util.QualiMapRunner;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.visualization.VariationChart;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SequenceAnalysisController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(SequenceAnalysisController.class);
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

            FastqcRunner runner = new FastqcRunner(null);
            try
            {
                String html = runner.execute(files);
                return new HtmlView("FastQC Report", html);
            }
            catch (FileNotFoundException e)
            {
                return new HtmlView("Error: " + e.getMessage());
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("FastQC Report"); //necessary to set page title, it seems
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class QualiMapReportAction extends SimpleViewAction<FastqcForm>
    {
        @Override
        public ModelAndView getView(FastqcForm form, BindException errors) throws Exception
        {
            if (form.getFilenames() == null && form.getDataIds() == null)
                errors.reject("Must provide a filename or Exp data Ids");

            //resolve files
            List<File> files = new ArrayList<>();

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

            if (form.getAnalysisIds() != null)
            {
                for (int id : form.getAnalysisIds())
                {
                    AnalysisModel m = AnalysisModelImpl.getFromDb(id, getUser());
                    if (m != null && m.getAlignmentFile() != null)
                    {
                        ExpData data = ExperimentService.get().getExpData(m.getAlignmentFile());
                        if (data != null && data.getContainer().hasPermission(getUser(), ReadPermission.class))
                        {
                            if (data.getFile().exists())
                                files.add(data.getFile());
                        }
                    }
                }
            }

            if (files.size() == 0)
            {
                return new HtmlView("Error: either no files provided or the files did not exist on the server");
            }

            QualiMapRunner runner = new QualiMapRunner();
            try
            {
                HtmlView view = new HtmlView("QualiMap Report", runner.execute(files));
                view.setHidePageTitle(true);

                return view;
            }
            catch (Exception e)
            {
                return new HtmlView("Error: " + e.getMessage());
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("QualiMap Report"); //necessary to set page title, it seems
            return root;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class SequenceAnalysisAction extends SimpleViewAction<Object>
    {
        public void validateCommand(Object form, Errors errors)
        {

        }

        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }

        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();
            for (PipelineStepProvider fact : SequencePipelineService.get().getAllProviders())
            {
                cds.addAll(fact.getClientDependencies());
            }

            Resource r = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class).getModuleResource(Path.parse("views/sequenceAnalysis.html"));
            assert r != null;

            ModuleHtmlView view = new ModuleHtmlView(r);
            view.addClientDependencies(cds);
            //getPageConfig().setTemplate(view.getPageTemplate());

            return view;
        }

        public NavTree appendNavTrail(NavTree tree)
        {
            return tree.addChild("Sequence Analysis");
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class AlignmentAnalysisAction extends SimpleViewAction<Object>
    {
        public void validateCommand(Object form, Errors errors)
        {

        }

        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }

        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();
            for (PipelineStepProvider fact : SequencePipelineService.get().getAllProviders())
            {
                cds.addAll(fact.getClientDependencies());
            }

            Resource r = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class).getModuleResource(Path.parse("views/alignmentAnalysis.html"));
            assert r != null;

            ModuleHtmlView view = new ModuleHtmlView(r);
            view.addClientDependencies(cds);
            //getPageConfig().setTemplate(view.getPageTemplate());

            return view;
        }

        public NavTree appendNavTrail(NavTree tree)
        {
            return tree.addChild("Analyze Alignments");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @IgnoresTermsOfUse
    public class DownloadTempImageAction extends ExportAction<TempImageAction>
    {
        public void export(TempImageAction form, HttpServletResponse response, BindException errors) throws Exception
        {
            File parentDir = form.getDirectory() == null ? FileUtil.getTempDirectory() : new File(FileUtil.getTempDirectory(), form.getDirectory());
            File targetFile = new File(parentDir, form.getFileName());
            targetFile = FileUtil.getAbsoluteCaseSensitiveFile(targetFile);

            if (!NetworkDrive.exists(targetFile))
            {
                throw new FileNotFoundException("Could not find file: " + targetFile.getPath());
            }

            if (parentDir.listFiles() == null)
            {
                throw new FileNotFoundException("Unable to list the contents of folder: " + parentDir.getPath());
            }

            PageFlowUtil.streamFile(response, targetFile, false);

            //the file will be recreated, so delete upon running
            FileUtils.deleteQuietly(targetFile);

            //if the folder if empty, remove it too.  other simultaneous requests might have deleted this folder before we get to it
            if (parentDir != null && parentDir.exists())
            {
                File[] children = parentDir.listFiles();
                if (children != null && children.length == 0 && !parentDir.equals(FileUtil.getTempDirectory()))
                {
                    FileUtils.deleteQuietly(parentDir); //the Images folder
                    File parent = parentDir.getParentFile();
                    FileUtils.deleteQuietly(parent); //the file's folder

                    if (parent != null && parent.getParentFile() != null)
                    {
                        File[] children2 = parent.getParentFile().listFiles();
                        if (children2 != null && children2.length == 0)
                            FileUtils.deleteQuietly(parent.getParentFile()); //the file's folder
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
                ts.forEach(new TableSelector.ForEachBlock<ResultSet>()
                {
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
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY, "Alignment Records", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_OUTPUTFILES, "Output Files", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_COVERAGE, "Coverage Records", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "analysis_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "analysis_id");
            }
            else if (SequenceAnalysisSchema.TABLE_READSETS.equals(_table.getName()))
            {
                msg.append("readsets " + StringUtils.join(keys, ", ") + "?  This will delete the readsets, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ANALYSES, "Analyses", keys, "readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY, "Alignment Records", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_COVERAGE, "Coverage Records", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "analysis_id/readset");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "analysis_id/readset");
            }
            else if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equals(_table.getName()))
            {
                msg.append("NT reference sequences " + StringUtils.join(keys, ", ") + "?  This will delete the reference sequences, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES, "Reference AA Sequences", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_FEATURES, "NT Features", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_COVERAGE, "Coverage Records", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION, "Alignment Records", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "ref_nt_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "ref_nt_id");
            }
            else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equals(_table.getName()))
            {
                msg.append("AA reference sequences " + StringUtils.join(keys, ", ") + "?  This will delete the reference sequences, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_FEATURES, "AA features", keys, "ref_aa_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_DRUG_RESISTANCE, "drug resistance mutations", keys, "ref_aa_id");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "ref_aa_id");
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

    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class RunVariantEvalAction extends ApiAction<RunVariantEvalForm>
    {
        public ApiResponse execute(RunVariantEvalForm form, BindException errors) throws Exception
        {
            Map<String, Object> ret = new HashMap<>();

            return new ApiSimpleResponse(ret);
        }
    }

    public static class RunVariantEvalForm
    {
        int[] _dataIds;

        public int[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(int[] dataIds)
        {
            _dataIds = dataIds;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @CSRF
    public class GetAnalysisToolDetailsAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Map<String, Object> ret = new HashMap<>();

            for (PipelineStep.StepType stepType : PipelineStep.StepType.values())
            {
                JSONArray list = new JSONArray();
                for (PipelineStepProvider fact : SequencePipelineService.get().getProviders(stepType.getStepClass()))
                {
                    list.put(fact.toJSON());
                }

                ret.put(stepType.name(), list);
            }

            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class SaveAnalysisAsTemplateAction extends ApiAction<SaveAnalysisAsTemplateForm>
    {
        public ApiResponse execute(SaveAnalysisAsTemplateForm form, BindException errors) throws Exception
        {
            Map<String, Object> ret = new HashMap<>();

            if (form.getAnalysisId() == null)
            {
                errors.reject(ERROR_MSG, "No analysis Id provided");
                return null;
            }

            AnalysisModel model = AnalysisModelImpl.getFromDb(form.getAnalysisId(), getUser());
            if (model == null)
            {
                errors.reject(ERROR_MSG, "Unable to find run for analysis: " + form.getAnalysisId());
                return null;
            }

            ExpRun run = ExperimentService.get().getExpRun(model.getRunId());
            List<? extends ExpData> datas = run.getInputDatas("AnalysisParameters", ExpProtocol.ApplicationType.ExperimentRun);
            if (datas.size() != 1 || !datas.get(0).getFile().exists())
            {
                errors.reject(ERROR_MSG, "Unable to find paramters file for selected job");
                return null;
            }

            String[] tokens = run.getProtocol().getLSID().split(":");
            String taskId = tokens[tokens.length - 1];
            taskId = PageFlowUtil.decode(taskId);

            File xml = datas.get(0).getFile();
            ParamParser parser = PipelineJobService.get().createParamParser();
            parser.parse(new ReaderInputStream(new FileReader(xml)));

            Map<String, Object> toSave = new CaseInsensitiveHashMap<>();
            toSave.put("name", form.getName());
            toSave.put("description", form.getDescription());
            toSave.put("taskid", taskId);
            toSave.put("originalAnalysisId", form.getAnalysisId());
            toSave.put("json", new JSONObject(parser.getInputParameters()).toString());

            TableInfo ti = QueryService.get().getUserSchema(getUser(), getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_SAVED_ANALYSES);
            ti.getUpdateService().insertRows(getUser(), getContainer(), Arrays.asList(toSave), new BatchValidationException(), null, new HashMap<String, Object>());

            return new ApiSimpleResponse("Success", true);
        }
    }

    public static class SaveAnalysisAsTemplateForm
    {
        private Integer _analysisId;
        private String _name;
        private String _description;

        public Integer getAnalysisId()
        {
            return _analysisId;
        }

        public void setAnalysisId(Integer analysisId)
        {
            _analysisId = analysisId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @CSRF
    public class ValidateReadsetFilesAction extends ApiAction<ValidateReadsetImportForm>
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
                    //TODO: consider proper container??
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
    @CSRF
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
            AnalysisModel[] models = ts.getArray(AnalysisModelImpl.class);
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
                AvgBaseQualityAggregator avg = new AvgBaseQualityAggregator(log, inputFile, refFile);

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
                    params.put("minAvgSnpQual", String.valueOf(form.getMinAvgSnpQual()));
                if (form.getMinAvgDipQual() > 0)
                    params.put("minAvgDipQual", String.valueOf(form.getMinAvgDipQual()));
                if (form.getMinSnpQual() > 0)
                    params.put("minSnpQual", String.valueOf(form.getMinSnpQual()));
                if (form.getMinDipQual() > 0)
                    params.put("minDipQual", String.valueOf(form.getMinDipQual()));

                if (aggregatorTypes.contains("coverage"))
                {
                    coverage = new NtCoverageAggregator(log, refFile, avg, params);
                    aggregators.add(coverage);
                }

                if (aggregatorTypes.contains("ntbypos"))
                {
                    NtSnpByPosAggregator ntSnp = new NtSnpByPosAggregator(log, refFile, avg, params);
                    if (coverage == null)
                        coverage = new NtCoverageAggregator(log, refFile, avg, params);

                    ntSnp.setCoverageAggregator(coverage);
                    aggregators.add(ntSnp);
                }

                if (aggregatorTypes.contains("aabycodon"))
                {
                    AASnpByCodonAggregator aaCodon = new AASnpByCodonAggregator(log, refFile, avg, params);
                    if (coverage == null)
                        coverage = new NtCoverageAggregator(log, refFile, avg, params);

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

                bi.saveSynopsis(getUser(), m);
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
            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("analysis_id"), form.getAnalysisId()), null);
            AnalysisModel[] records = ts.getArray(AnalysisModelImpl.class);
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
                resultProperties.put("exception", "Unable to find reference FASTA, or the file does not exist");
                return new ApiSimpleResponse(resultProperties);
            }

            Logger log = Logger.getLogger(BamIterator.class);
            BamIterator bi = new BamIterator(bam.getFile(), ref.getFile(), log);

            Map<String, String> params = new HashMap<>();
            AvgBaseQualityAggregator avg = new AvgBaseQualityAggregator(log, bam.getFile(), ref.getFile());
            AASnpByReadAggregator aaAggregator = new AASnpByReadAggregator(log, ref.getFile(), avg, params);
            bi.addAggregators(Collections.singletonList((AlignmentAggregator) aaAggregator));

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

    public static class TempImageAction
    {
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
    }

    public static class FastqcForm
    {
        private String[] _filenames;
        private Integer[] _dataIds;
        private Integer[] _readsets;
        private Integer[] _analysisIds;

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

        public Integer[] getAnalysisIds()
        {
            return _analysisIds;
        }

        public void setAnalysisIds(Integer... analysisIds)
        {
            _analysisIds = analysisIds;
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

    @RequiresPermissionClass(ReadPermission.class)
    @CSRF
    public class GenerateChartAction extends ApiAction<GenerateChartForm>
    {
        public ApiResponse execute(GenerateChartForm form, BindException errors) throws Exception
        {
            Map<String, Object> resultProperties = new HashMap<>();

            try
            {
                VariationChart vc = new VariationChart();
                List<JFreeChart> charts = vc.createChart(form.getSeries(), form.getGff(), form.getMaxBases());

                resultProperties.putAll(vc.toSVG(charts, form.getWidth(), form.getSectionHeight()));
                resultProperties.put("success", true);
            }
            catch (IOException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadChartAction extends ExportAction<GenerateChartForm>
    {
        @Override
        public void validate(GenerateChartForm form, BindException errors)
        {
            if (form.getSeries() == null || form.getSeries().length == 0)
            {
                errors.reject(ERROR_MSG, "Unable to convert create graph: no variscan output provided");
            }
        }

        public void export(GenerateChartForm form, HttpServletResponse response, BindException errors) throws Exception
        {

            response.setContentType("svg");
            response.setHeader("Content-disposition", "attachment; filename=\"sequence.svg");
            response.setHeader("Pragma", "private");
            response.setHeader("Cache-Control", "private");

            VariationChart vc = new VariationChart();
            List<JFreeChart> charts = vc.createChart(form.getSeries(), form.getGff(), form.getMaxBases());

            Map<String, Object> props = vc.toSVG(charts, form.getWidth(), form.getSectionHeight());
            if (props.containsKey("filePath"))
            {
                File targetFile = new File((String) props.get("filePath"));
                if (targetFile.exists())
                {
                    PageFlowUtil.streamFile(response, targetFile, false);
                    FileUtils.deleteQuietly(targetFile);
                }
                else
                {
                    throw new NotFoundException("Unable to generate chart");
                }
            }
        }
    }

    public static class GenerateChartForm
    {
        private String[] _series;
        private String _gff;
        private int _width = 1000;
        private int _sectionHeight = 400;
        private int _maxBases = 10000;

        public String[] getSeries()
        {
            return _series;
        }

        public void setSeries(String[] series)
        {
            _series = series;
        }

        public String getGff()
        {
            return _gff;
        }

        public void setGff(String gff)
        {
            _gff = gff;
        }

        public int getWidth()
        {
            return _width;
        }

        public void setWidth(int width)
        {
            _width = width;
        }

        public int getSectionHeight()
        {
            return _sectionHeight;
        }

        public void setSectionHeight(int sectionHeight)
        {
            _sectionHeight = sectionHeight;
        }

        public int getMaxBases()
        {
            return _maxBases;
        }

        public void setMaxBases(int maxBases)
        {
            _maxBases = maxBases;
        }
    }

    /**
     * Called from LABKEY.Pipeline.startAnalysis()
     */
    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class StartAnalysisAction extends ApiAction<AnalyzeForm>
    {
        public ApiResponse execute(AnalyzeForm form, BindException errors) throws Exception
        {
            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !pr.isValid())
                throw new NotFoundException();

            File dirData = null;
            if (form.getPath() != null)
            {
                dirData = pr.resolvePath(form.getPath());
                if (dirData == null || !NetworkDrive.exists(dirData))
                    throw new NotFoundException("Could not resolve path: " + form.getPath());
            }

            TaskId taskId = new TaskId(form.getTaskId());
            TaskPipeline taskPipeline = PipelineJobService.get().getTaskPipeline(taskId);

            AbstractFileAnalysisProtocolFactory factory = getProtocolFactory(taskPipeline);
            return execute(form, pr, dirData, factory);
        }

        protected ApiResponse execute(AnalyzeForm form, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory) throws IOException, PipelineValidationException
        {
            try
            {
                TaskId taskId = new TaskId(form.getTaskId());
                TaskPipeline taskPipeline = PipelineJobService.get().getTaskPipeline(taskId);

                if (form.getProtocolName() == null)
                {
                    throw new IllegalArgumentException("Must specify a protocol name");
                }

                JSONObject o = new JSONObject(form.getConfigureJson());
                Map<String, String> params = new HashMap<>();
                for (Map.Entry<String, Object> entry : o.entrySet())
                {
                    params.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
                }

                Boolean allowNonExistentFiles = form.isAllowNonExistentFiles() != null ? form.isAllowNonExistentFiles() : false;
                List<File> filesInputList = form.getValidatedFiles(getContainer(), allowNonExistentFiles);

                if (form.isActiveJobs())
                {
                    throw new IllegalArgumentException("Active jobs already exist for this protocol.");
                }

                Map<String, Object> resultProperties = new HashMap<>();
                if (form.getSplitJobs())
                {
                    List<String> jobGUIDs = new ArrayList<>();
                    Map<ReadsetModel, Pair<File, File>> toRun = SequenceAlignmentTask.getAlignmentFiles(params, filesInputList, false);
                    _log.info("creating split sequence jobs for " + filesInputList.size() + " files.  These divided into: " + toRun.size() + " jobs.");
                    int idx = 1;
                    for (Pair<File, File> files : toRun.values())
                    {
                        List<File> fileList = new ArrayList<>();
                        fileList.add(files.first);
                        if (files.second != null)
                        {
                            fileList.add(files.second);
                        }

                        String protocolName = form.getProtocolName() + (toRun.size() == 1 ? "" : "_" + idx);
                        AbstractFileAnalysisProtocol protocol = getFileAnalysisProtocol(form, taskPipeline, params, root, dirData, factory, protocolName);
                        protocol.getFactory().ensureDefaultParameters(root);

                        File fileParameters = factory.getParametersFile(dirData, protocolName, root);
                        // Make sure configure.xml file exists for the job when it runs.
                        if (fileParameters != null && !fileParameters.exists())
                        {
                            protocol.setEmail(getUser().getEmail());
                            protocol.saveInstance(fileParameters, getContainer());
                        }

                        _log.info("starting for file(s): " + fileList.get(0).getName() + (files.second != null ? " and " + fileList.get(1).getName() : ""));
                        AbstractFileAnalysisJob job = new SequenceAnalysisJob(protocol, protocolName, getViewBackgroundInfo(), root, taskPipeline.getId(), fileParameters, fileList);
                        PipelineService.get().queueJob(job);
                        jobGUIDs.add(job.getJobGUID());
                        idx++;
                    }

                    resultProperties.put("jobGUIDs", jobGUIDs);
                }
                else
                {
                    AbstractFileAnalysisProtocol protocol = getFileAnalysisProtocol(form, taskPipeline, params, root, dirData, factory, form.getProtocolName());
                    protocol.getFactory().ensureDefaultParameters(root);

                    File fileParameters = protocol.getParametersFile(dirData, root);
                    // Make sure configure.xml file exists for the job when it runs.
                    if (fileParameters != null && !fileParameters.exists())
                    {
                        protocol.setEmail(getUser().getEmail());
                        protocol.saveInstance(fileParameters, getContainer());
                    }

                    _log.info("creating single sequence job for " + filesInputList.size() + " files.");
                    AbstractFileAnalysisJob job = new SequenceAnalysisJob(protocol, protocol.getName(), getViewBackgroundInfo(), root, taskPipeline.getId(), fileParameters, filesInputList);
                    PipelineService.get().queueJob(job);

                    resultProperties.put("jobGUIDs", Arrays.asList(job.getJobGUID()));
                }

                resultProperties.put("status", "success");

                return new ApiSimpleResponse(resultProperties);
            }
            catch (IOException | ClassNotFoundException | PipelineValidationException e)
            {
                throw new ApiUsageException(e);
            }
        }

        private AbstractFileAnalysisProtocol getFileAnalysisProtocol(AnalyzeForm form, TaskPipeline taskPipeline, Map<String, String> params, PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory, String protocolName) throws PipelineValidationException, IOException
        {
            AbstractFileAnalysisProtocol protocol = getProtocol(root, dirData, factory, protocolName);
            if (protocol == null)
            {
                String xml;
                if (form.getConfigureXml() != null)
                {
                    if (form.getConfigureJson() != null)
                    {
                        throw new IllegalArgumentException("The parameters should be defined as XML or JSON, not both");
                    }
                    xml = form.getConfigureXml();
                }
                else
                {
                    if (form.getConfigureJson() == null)
                    {
                        throw new IllegalArgumentException("Parameters must be defined, either as XML or JSON");
                    }
                    ParamParser parser = PipelineJobService.get().createParamParser();
                    xml = parser.getXMLFromMap(params);
                }

                protocol = getProtocolFactory(taskPipeline).createProtocolInstance(
                        protocolName,
                        form.getProtocolDescription(),
                        xml);

                protocol.setEmail(getUser().getEmail());
                protocol.validateToSave(root);
                if (form.isSaveProtocol())
                {
                    protocol.saveDefinition(root);
                    PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(), getContainer(), getUser(), protocol.getName());
                }
            }
            else
            {
                if (form.getConfigureXml() != null || form.getConfigureJson() != null)
                {
                    throw new IllegalArgumentException("Cannot redefine an existing protocol");
                }
                PipelineService.get().rememberLastProtocolSetting(protocol.getFactory(), getContainer(), getUser(), protocol.getName());
            }

            return protocol;
        }
    }

    public static class AnalyzeForm extends PipelinePathForm
    {
        public enum Params
        {
            path, taskId, file
        }

        private String _taskId = "";
        private String _protocolName = "";
        private String _protocolDescription = "";
        private String[] _fileInputStatus = null;
        private String _configureXml;
        private String _configureJson;
        private boolean _saveProtocol = false;
        private boolean _runAnalysis = false;
        private boolean _activeJobs = false;
        private Boolean _allowNonExistentFiles;
        private Boolean _splitJobs = false;

        private static final String UNKNOWN_STATUS = "UNKNOWN";

        public void initStatus(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis)
        {
            if (_fileInputStatus != null)
                return;

            _activeJobs = false;

            int len = getFile().length;
            _fileInputStatus = new String[len + 1];
            for (int i = 0; i < len; i++)
                _fileInputStatus[i] = initStatusFile(protocol, dirData, dirAnalysis, getFile()[i], true);
            _fileInputStatus[len] = initStatusFile(protocol, dirData, dirAnalysis, null, false);
        }

        private String initStatusFile(AbstractFileAnalysisProtocol protocol, File dirData, File dirAnalysis,
                                      String fileInputName, boolean statusSingle)
        {
            if (protocol == null)
            {
                return UNKNOWN_STATUS;
            }

            File fileStatus = null;

            if (!statusSingle)
            {
                fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis,
                        protocol.getJoinedBaseName());
            }
            else if (fileInputName != null)
            {
                File fileInput = new File(dirData, fileInputName);
                FileType ft = protocol.findInputType(fileInput);
                if (ft != null)
                    fileStatus = PipelineJob.FT_LOG.newFile(dirAnalysis, ft.getBaseName(fileInput));
            }

            if (fileStatus != null)
            {
                PipelineStatusFile sf = PipelineService.get().getStatusFile(fileStatus);
                if (sf == null)
                    return null;

                _activeJobs = _activeJobs || sf.isActive();
                return sf.getStatus();
            }

            // Failed to get status.  Assume job is active, and return unknown status.
            _activeJobs = true;
            return UNKNOWN_STATUS;
        }

        public String getTaskId()
        {
            return _taskId;
        }

        public void setTaskId(String taskId)
        {
            _taskId = taskId;
        }

        public String getConfigureXml()
        {
            return _configureXml;
        }

        public void setConfigureXml(String configureXml)
        {
            _configureXml = (configureXml == null ? "" : configureXml);
        }

        public String getConfigureJson()
        {
            return _configureJson;
        }

        public void setConfigureJson(String configureJson)
        {
            _configureJson = configureJson;
        }

        public String getProtocolName()
        {
            return _protocolName;
        }

        public void setProtocolName(String protocolName)
        {
            _protocolName = (protocolName == null ? "" : protocolName);
        }

        public String getProtocolDescription()
        {
            return _protocolDescription;
        }

        public void setProtocolDescription(String protocolDescription)
        {
            _protocolDescription = (protocolDescription == null ? "" : protocolDescription);
        }

        public String[] getFileInputStatus()
        {
            return _fileInputStatus;
        }

        public boolean isActiveJobs()
        {
            return _activeJobs;
        }

        public boolean isSaveProtocol()
        {
            return _saveProtocol;
        }

        public void setSaveProtocol(boolean saveProtocol)
        {
            _saveProtocol = saveProtocol;
        }

        public boolean isRunAnalysis()
        {
            return _runAnalysis;
        }

        public void setRunAnalysis(boolean runAnalysis)
        {
            _runAnalysis = runAnalysis;
        }

        public Boolean isAllowNonExistentFiles()
        {
            return _allowNonExistentFiles;
        }

        public void setAllowNonExistentFiles(Boolean allowNonExistentFiles)
        {
            _allowNonExistentFiles = allowNonExistentFiles;
        }

        public Boolean getSplitJobs()
        {
            return _splitJobs == null ? false : _splitJobs;
        }

        public void setSplitJobs(Boolean splitJobs)
        {
            _splitJobs = splitJobs;
        }
    }

    private AbstractFileAnalysisProtocol getProtocol(PipeRoot root, File dirData, AbstractFileAnalysisProtocolFactory factory, String protocolName)
    {
        try
        {
            File protocolFile = factory.getParametersFile(dirData, protocolName, root);
            AbstractFileAnalysisProtocol result;
            if (NetworkDrive.exists(protocolFile))
            {
                result = factory.loadInstance(protocolFile);

                // Don't allow the instance file to override the protocol name.
                result.setName(protocolName);
            }
            else
            {
                result = factory.load(root, protocolName);
            }
            return result;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private AbstractFileAnalysisProtocolFactory getProtocolFactory(TaskPipeline taskPipeline)
    {
        //TODO: FileAnalysisPipelineProvider.name
        AbstractFileAnalysisProvider provider = (AbstractFileAnalysisProvider) PipelineService.get().getPipelineProvider("File Analysis");
        if (provider == null)
            throw new NotFoundException("No pipeline provider found for task pipeline: " + taskPipeline);

        if (!(taskPipeline instanceof FileAnalysisTaskPipeline))
            throw new NotFoundException("Task pipeline is not a FileAnalysisTaskPipeline: " + taskPipeline);

        FileAnalysisTaskPipeline fatp = (FileAnalysisTaskPipeline) taskPipeline;
        //noinspection unchecked
        return provider.getProtocolFactory(fatp);
    }

    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class CreateReferenceLibraryAction extends ApiAction<CreateReferenceLibraryForm>
    {
        public ApiResponse execute(CreateReferenceLibraryForm form, BindException errors) throws Exception
        {
            if (form.getName() == null)
            {
                errors.reject(ERROR_MSG, "Must provide a name for the library");
                return null;
            }

            if (form.getSequenceIds() == null || form.getSequenceIds().length == 0)
            {
                errors.reject(ERROR_MSG, "Must provide at least one sequence to include in the library");
                return null;
            }

            if (form.getIntervals() != null && form.getIntervals().length != form.getSequenceIds().length)
            {
                errors.reject(ERROR_MSG, "If supplying a custom list of intervals, this array must be the same length as sequence IDs");
                return null;
            }

            List<ReferenceLibraryMember> members = new ArrayList<>();
            int idx = 0;
            for (Integer seqId : form.getSequenceIds())
            {
                if (form.getIntervals() != null)
                {
                    String intervals = form.getIntervals()[idx];
                    if (StringUtils.trimToNull(intervals) != null)
                    {
                        for (String t : intervals.split(","))
                        {
                            ReferenceLibraryMember m = new ReferenceLibraryMember();
                            m.setRef_nt_id(seqId);

                            String[] coordinates = t.split("-");
                            if (coordinates.length != 2)
                            {
                                errors.reject("Inproper interval: [" + t + "]");
                                return null;
                            }

                            Integer start = StringUtils.trimToNull(coordinates[0]) == null ? null : ConvertHelper.convert(coordinates[0], Integer.class);
                            m.setStart(start);
                            Integer stop = StringUtils.trimToNull(coordinates[1]) == null ? null : ConvertHelper.convert(coordinates[1], Integer.class);
                            m.setStop(stop);

                            members.add(m);
                        }
                    }
                    else
                    {
                        ReferenceLibraryMember m = new ReferenceLibraryMember();
                        m.setRef_nt_id(seqId);
                        members.add(m);
                    }
                }
                else
                {
                    ReferenceLibraryMember m = new ReferenceLibraryMember();
                    m.setRef_nt_id(seqId);
                    members.add(m);
                }

                idx++;
            }

            SequenceAnalysisManager.get().createReferenceLibrary(getContainer(), getUser(), form.getName(), form.getDescription(), members);

            return new ApiSimpleResponse("Success", true);
        }
    }

    public static class CreateReferenceLibraryForm
    {
        private String _name;
        private String _description;
        private Integer[] _sequenceIds;
        private String[] _intervals;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public Integer[] getSequenceIds()
        {
            return _sequenceIds;
        }

        public void setSequenceIds(Integer[] sequenceIds)
        {
            _sequenceIds = sequenceIds;
        }

        public String[] getIntervals()
        {
            return _intervals;
        }

        public void setIntervals(String[] intervals)
        {
            _intervals = intervals;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ImportFastaSequencesAction extends AbstractFileUploadAction<ImportFastaSequencesForm>
    {
        @Override
        public void export(ImportFastaSequencesForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);

            super.export(form, response, errors);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading FASTA files", HttpServletResponse.SC_NOT_FOUND);

            AssayFileWriter writer = new AssayFileWriter();
            try
            {
                File targetDirectory = writer.ensureUploadDirectory(getContainer());
                return writer.findUniqueFileName(filename, targetDirectory);
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        protected String getResponse(Map<String, Pair<File, String>> files, ImportFastaSequencesForm form) throws UploadException
        {
            JSONObject resp = new JSONObject();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();

                    Map<String, String> params = new HashMap<>();
                    if (form.getSpecies() != null)
                    {
                        params.put("species", form.getSpecies());
                    }

                    if (form.getMolType() != null)
                    {
                        params.put("mol_type", form.getMolType());
                    }

                    List<Integer> sequenceIds = SequenceAnalysisManager.get().importRefSequencesFromFasta(getContainer(), getUser(), file, params);
                    file.delete();

                    if (form.isCreateLibrary())
                    {
                        SequenceAnalysisManager.get().createReferenceLibrary(sequenceIds, getContainer(), getUser(), form.getLibraryName(), form.getLibraryDescription());
                    }

                    resp.put("success", true);
                }
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
                logger.error(e.getMessage(), e);
                resp.put("success", false);
                resp.put("exception", e.getMessage());
            }

            return resp.toString();
        }
    }

    public static class ImportFastaSequencesForm extends AbstractFileUploadAction.FileUploadForm
    {
        private String _jsonData;
        private String _species;
        private String _molType;
        private boolean _createLibrary = false;
        private String _libraryName;
        private String _libraryDescription;

        public String getJsonData()
        {
            return _jsonData;
        }

        public void setJsonData(String jsonData)
        {
            _jsonData = jsonData;
        }

        public String getSpecies()
        {
            return _species;
        }

        public void setSpecies(String species)
        {
            _species = species;
        }

        public String getMolType()
        {
            return _molType;
        }

        public void setMolType(String molType)
        {
            _molType = molType;
        }

        public boolean isCreateLibrary()
        {
            return _createLibrary;
        }

        public void setCreateLibrary(boolean createLibrary)
        {
            _createLibrary = createLibrary;
        }

        public String getLibraryName()
        {
            return _libraryName;
        }

        public void setLibraryName(String libraryName)
        {
            _libraryName = libraryName;
        }

        public String getLibraryDescription()
        {
            return _libraryDescription;
        }

        public void setLibraryDescription(String libraryDescription)
        {
            _libraryDescription = libraryDescription;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ImportOutputFileAction extends AbstractFileUploadAction<ImportOutputFileForm>
    {
        @Override
        public void export(ImportOutputFileForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);

            super.export(form, response, errors);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading files", HttpServletResponse.SC_NOT_FOUND);

            AssayFileWriter writer = new AssayFileWriter();
            try
            {
                File targetDirectory = writer.ensureUploadDirectory(getContainer(), "sequenceOutputs");
                return writer.findUniqueFileName(filename, targetDirectory);
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        protected String getResponse(Map<String, Pair<File, String>> files, ImportOutputFileForm form) throws UploadException
        {
            JSONObject resp = new JSONObject();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();

                    //TODO: consider automatically processing certain types, such as gzipping VCFs, etc.

                    Map<String, Object> params = new CaseInsensitiveHashMap<>();
                    params.put("name", form.getName());
                    params.put("description", form.getDescription());

                    ExpData data = ExperimentService.get().createData(getContainer(), new DataType("Sequence Output"), file.getName());
                    data.setDataFileURI(file.toURI());
                    data.save(getUser());
                    params.put("dataid", data.getRowId());
                    params.put("library_id", form.getLibraryId());
                    if (form.getReadset() > 0)
                        params.put("readset", form.getReadset());
                    params.put("category", form.getCategory());

                    params.put("container", getContainer().getId());
                    params.put("created", new Date());
                    params.put("createdby", getUser().getUserId());
                    params.put("modified", new Date());
                    params.put("modifiedby", getUser().getUserId());

                    Table.insert(getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), params);

                    resp.put("success", true);
                }
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
                logger.error(e.getMessage(), e);
                resp.put("success", false);
                resp.put("exception", e.getMessage());
            }

            return resp.toString();
        }
    }

    public static class ImportOutputFileForm extends AbstractFileUploadAction.FileUploadForm
    {
        private String _name;
        private String _description;
        private String _category;
        private int _libraryId;
        private int _readset;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public int getLibraryId()
        {
            return _libraryId;
        }

        public void setLibraryId(int libraryId)
        {
            _libraryId = libraryId;
        }

        public int getReadset()
        {
            return _readset;
        }

        public void setReadset(int readset)
        {
            _readset = readset;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ImportTrackAction extends AbstractFileUploadAction<ImportTrackForm>
    {
        @Override
        public void export(ImportTrackForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);

            super.export(form, response, errors);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading files", HttpServletResponse.SC_NOT_FOUND);

            AssayFileWriter writer = new AssayFileWriter();
            try
            {
                File targetDirectory = writer.ensureUploadDirectory(getContainer());
                return writer.findUniqueFileName(filename, targetDirectory);
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        protected String getResponse(Map<String, Pair<File, String>> files, ImportTrackForm form) throws UploadException
        {
            JSONObject resp = new JSONObject();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();

                    if (form.getTrackName() == null || form.getLibraryId() == null)
                    {
                        throw new UploadException("Must provide the track name and library Id", HttpServletResponse.SC_BAD_REQUEST);
                    }

                    SequenceAnalysisManager.get().addTrackForLibrary(getContainer(), getUser(), file, form.getLibraryId(), form.getTrackName(), form.getTrackDescription(), null);

                    resp.put("success", true);
                }
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
                logger.error(e.getMessage(), e);
                resp.put("success", false);
                resp.put("exception", e.getMessage());
            }

            return resp.toString();
        }
    }

    public static class ImportTrackForm extends AbstractFileUploadAction.FileUploadForm
    {
        private Integer _libraryId;
        private String _trackName;
        private String _trackDescription;

        public Integer getLibraryId()
        {
            return _libraryId;
        }

        public void setLibraryId(Integer libraryId)
        {
            _libraryId = libraryId;
        }

        public String getTrackName()
        {
            return _trackName;
        }

        public void setTrackName(String trackName)
        {
            _trackName = trackName;
        }

        public String getTrackDescription()
        {
            return _trackDescription;
        }

        public void setTrackDescription(String trackDescription)
        {
            _trackDescription = trackDescription;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class ImportChainFileAction extends AbstractFileUploadAction<ImportChainFileForm>
    {
        @Override
        public void export(ImportChainFileForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);

            super.export(form, response, errors);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading files", HttpServletResponse.SC_NOT_FOUND);

            AssayFileWriter writer = new AssayFileWriter();
            try
            {
                File targetDirectory = writer.ensureUploadDirectory(getContainer());
                return writer.findUniqueFileName(filename, targetDirectory);
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        protected String getResponse(Map<String, Pair<File, String>> files, ImportChainFileForm form) throws UploadException
        {
            JSONObject resp = new JSONObject();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();

                    if (form.getGenomeId1() == null || form.getGenomeId2() == null)
                    {
                        throw new UploadException("Must provide the source and target genomes", HttpServletResponse.SC_BAD_REQUEST);
                    }

                    SequenceAnalysisManager.get().addChainFile(getContainer(), getUser(), file, form.getGenomeId1(), form.getGenomeId2(), form.getVersion());

                    resp.put("success", true);
                }
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
                logger.error(e.getMessage(), e);
                resp.put("success", false);
                resp.put("exception", e.getMessage());
            }

            return resp.toString();
        }
    }

    public static class ImportChainFileForm extends AbstractFileUploadAction.FileUploadForm
    {
        private Integer _genomeId1;
        private Integer _genomeId2;
        private Double _version;

        public Integer getGenomeId1()
        {
            return _genomeId1;
        }

        public void setGenomeId1(Integer genomeId1)
        {
            _genomeId1 = genomeId1;
        }

        public Integer getGenomeId2()
        {
            return _genomeId2;
        }

        public void setGenomeId2(Integer genomeId2)
        {
            _genomeId2 = genomeId2;
        }

        public Double getVersion()
        {
            return _version;
        }

        public void setVersion(Double version)
        {
            _version = version;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @IgnoresTermsOfUse
    public class DownloadReferencesAction extends ExportAction<DownloadReferencesForm>
    {
        public void export(DownloadReferencesForm form, final HttpServletResponse response, final BindException errors) throws Exception
        {
            if (form.getRowIds() == null || form.getRowIds().length == 0)
            {
                throw new NotFoundException("No sequence IDs provided");
            }

            if (form.getLineLength() == 0)
            {
                throw new IllegalArgumentException("Line length must be provided and greater than 0");
            }
            final int lineLength = form.getLineLength();

            if (StringUtils.isEmpty(form.getHeaderFormat()))
            {
                throw new IllegalArgumentException("No header format provided");
            }

            if (StringUtils.isEmpty(form.getFileName()))
            {
                throw new NotFoundException("Must provide an output filename");
            }
            String filename = form.getFileName();
            final StringExpressionFactory.FieldKeyStringExpression se = StringExpressionFactory.URLStringExpression.create(form.getHeaderFormat(), false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank);
            Set<FieldKey> keys = new HashSet<>(se.getFieldKeys());
            keys.add(FieldKey.fromString("sequenceFile"));
            keys.add(FieldKey.fromString("container"));
            keys.add(FieldKey.fromString("rowid"));
            TableInfo ti = QueryService.get().getUserSchema(getUser(), getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
            if (ti == null)
            {
                throw new NotFoundException("Ref NT table not found");
            }

            final Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, keys);
            final JSONObject intervalMap = StringUtils.trimToNull(form.getIntervals()) == null ? new JSONObject() : new JSONObject(form.getIntervals());
            PageFlowUtil.prepareResponseForFile(response, Collections.<String, String>emptyMap(), filename, true);
            TableSelector ts = new TableSelector(ti, cols.values(), new SimpleFilter(FieldKey.fromString("rowid"), Arrays.asList(form.getRowIds()), CompareType.IN), null);
            ts.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet object) throws SQLException
                {
                    Results rs = new ResultsImpl(object, cols);
                    Integer rowId = rs.getInt(FieldKey.fromString("rowid"));
                    String header = se.eval(rs.getFieldKeyRowMap());
                    RefNtSequenceModel model = new RefNtSequenceModel();
                    if (rs.getObject(FieldKey.fromString("sequenceFile")) != null)
                        model.setSequenceFile(rs.getInt(FieldKey.fromString("sequenceFile")));

                    model.setContainer(rs.getString(FieldKey.fromString("container")));

                    try
                    {
                        if (intervalMap.containsKey(rowId.toString()))
                        {
                            String wholeSequence = model.getSequence();
                            for (String t : intervalMap.getString(rowId.toString()).split(","))
                            {
                                String[] coordinates = t.split("-");
                                if (coordinates.length != 2)
                                {
                                    errors.reject("Inproper interval: [" + t + "]");
                                    return;
                                }

                                Integer start = StringUtils.trimToNull(coordinates[0]) == null ? null : ConvertHelper.convert(coordinates[0], Integer.class);
                                Integer stop = StringUtils.trimToNull(coordinates[1]) == null ? null : ConvertHelper.convert(coordinates[1], Integer.class);

                                response.getWriter().write(">" + header + "_" + start + "-" + stop + "\n");

                                //convert start to 0-based
                                writeSequence(wholeSequence.substring(start == null || start == 0 ? null : start - 1, stop), lineLength, response);
                            }
                        }
                        else
                        {
                            response.getWriter().write(">" + header + "\n");
                            writeSequence(model.getSequence(), lineLength, response);
                        }
                    }
                    catch (IOException e)
                    {
                        throw new SQLException(e);
                    }
                }
            });
        }

        private void writeSequence(String seq, int lineLength, HttpServletResponse response) throws IOException
        {
            if (seq != null)
            {
                int len = seq.length();
                for (int i = 0; i < len; i += lineLength)
                {
                    response.getWriter().write(seq.substring(i, Math.min(len, i + lineLength)) + "\n");
                }
            }
            else
            {
                response.getWriter().write("\n");
            }
        }
    }

    public static class DownloadReferencesForm
    {
        private String _headerFormat;
        private Integer[] _rowIds;
        private String _fileName;
        private int _lineLength = 60;
        private String _intervals;

        public String getHeaderFormat()
        {
            return _headerFormat;
        }

        public void setHeaderFormat(String headerFormat)
        {
            _headerFormat = headerFormat;
        }

        public Integer[] getRowIds()
        {
            return _rowIds;
        }

        public void setRowIds(Integer[] rowIds)
        {
            _rowIds = rowIds;
        }

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }

        public int getLineLength()
        {
            return _lineLength;
        }

        public void setLineLength(int lineLength)
        {
            _lineLength = lineLength;
        }

        public String getIntervals()
        {
            return _intervals;
        }

        public void setIntervals(String intervals)
        {
            _intervals = intervals;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    @CSRF
    public class RecreateReferenceLibraryAction extends ApiAction<RecreateReferenceLibraryForm>
    {
        public ApiResponse execute(RecreateReferenceLibraryForm form, BindException errors)
        {
            if (form.getLibraryIds() == null || form.getLibraryIds().length == 0)
            {
                errors.reject("Must provide a list of reference genomes to re-process");
                return null;
            }

            try
            {
                PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
                for (Integer libraryId : form.getLibraryIds())
                {
                    TableInfo ti = DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
                    String containerId = new TableSelector(ti, PageFlowUtil.set("container"), new SimpleFilter(FieldKey.fromString("rowid"), libraryId), null).getObject(String.class);
                    if (containerId == null)
                    {
                        throw new PipelineValidationException("Unknown reference genome: " + libraryId);
                    }

                    Container c = ContainerManager.getForId(containerId);
                    if (c == null)
                    {
                        throw new PipelineValidationException("Unknown container: " + containerId);
                    }

                    if (!c.hasPermission(getUser(), UpdatePermission.class))
                    {
                        throw new PipelineValidationException("Insufficient permissions to update reference genome: " + libraryId);
                    }

                    PipelineService.get().queueJob(new ReferenceLibraryPipelineJob(c, getUser(), null, root, libraryId));
                }

                return new ApiSimpleResponse("success", true);
            }
            catch (PipelineValidationException | IOException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
        }
    }

    public static class RecreateReferenceLibraryForm
    {
        private int[] _libraryIds;

        public int[] getLibraryIds()
        {
            return _libraryIds;
        }

        public void setLibraryIds(int[] libraryIds)
        {
            _libraryIds = libraryIds;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class CheckFileStatusAction extends ApiAction<CheckFileStatusForm>
    {
        public ApiResponse execute(CheckFileStatusForm form, BindException errors)
        {
            Map<String, Object> ret = new HashMap<>();

            if (StringUtils.isEmpty(form.getHandlerClass()))
            {
                errors.reject(ERROR_MSG, "Must provide the file handler");
                return null;
            }

            SequenceFileHandler handler = SequenceAnalysisManager.get().getFileHandler(form.getHandlerClass());

            JSONArray arr = new JSONArray();
            if (form.getDataIds() != null)
            {
                for (int dataId : form.getDataIds())
                {
                    arr.put(getDataJson(handler, dataId, null));
                }
            }

            if (form.getOutputFileIds() != null)
            {
                TableInfo ti = DbSchema.get(SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
                for (int outputFileId : form.getOutputFileIds())
                {
                    Map rowMap = new TableSelector(ti, PageFlowUtil.set("dataId", "library_id"), new SimpleFilter(FieldKey.fromString("rowid"), outputFileId), null).getObject(Map.class);
                    if (rowMap == null || rowMap.get("dataid") == null)
                    {
                        JSONObject o = new JSONObject();
                        o.put("outputFileId", outputFileId);
                        o.put("fileExists", false);
                        o.put("error", true);
                        arr.put(o);
                        continue;
                    }

                    Integer dataId = (Integer) rowMap.get("dataid");
                    Integer libraryId = (Integer) rowMap.get("library_id");
                    ExpData d = ExperimentService.get().getExpData(dataId);
                    if (d == null)
                    {
                        JSONObject o = new JSONObject();
                        o.put("outputFileId", outputFileId);
                        o.put("fileExists", false);
                        o.put("error", true);
                        arr.put(o);
                        continue;
                    }

                    JSONObject o = getDataJson(handler, d.getRowId(), outputFileId);
                    o.put("libraryId", libraryId);
                    arr.put(o);
                }
            }

            ret.put("files", arr);

            return new ApiSimpleResponse(ret);
        }

        private JSONObject getDataJson(SequenceFileHandler handler, int dataId, @Nullable Integer outputFileId)
        {
            JSONObject o = new JSONObject();
            o.put("dataId", dataId);
            o.put("outputFileId", outputFileId);

            ExpData d = ExperimentService.get().getExpData(dataId);
            if (d.getFile() == null || !d.getFile().exists())
            {
                o.put("fileExists", false);
                o.put("error", true);
                return o;
            }

            o.put("fileName", d.getFile().getName());
            o.put("fileExists", true);
            o.put("extension", FileUtil.getExtension(d.getFile()));

            boolean canProcess = handler.canProcess(d.getFile());
            o.put("canProcess", canProcess);

            return o;
        }
    }

    public static class CheckFileStatusForm
    {
        private String _handlerClass;
        private int[] _outputFileIds;
        private int[] _dataIds;

        public String getHandlerClass()
        {
            return _handlerClass;
        }

        public void setHandlerClass(String handlerClass)
        {
            _handlerClass = handlerClass;
        }

        public int[] getOutputFileIds()
        {
            return _outputFileIds;
        }

        public void setOutputFileIds(int[] outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }

        public int[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(int[] dataIds)
        {
            _dataIds = dataIds;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class OutputFileHandler extends RedirectAction<OutputFileHandlerForm>
    {
        private URLHelper _url;

        public void validateCommand(OutputFileHandlerForm form, Errors errors)
        {
            if (form.getHandlerClass() == null || form.getOutputFileIds() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the name of the file handler and list of files to process");
            }

            SequenceFileHandler handler = SequenceAnalysisManager.get().getFileHandler(form.getHandlerClass());
            if (handler == null)
            {
                errors.reject(ERROR_MSG, "Unable to find handler matching: " + form.getHandlerClass());
            }

            List<Integer> idList = new ArrayList<>();
            for (String token : StringUtils.split(form.getOutputFileIds()))
            {
                idList.add(ConvertHelper.convert(token, Integer.class));
            }

            _url = handler.getSuccessURL(getContainer(), getUser(), idList);
            if (_url == null)
            {
                errors.reject(ERROR_MSG, "This handler is not supported through this action.  This is probably an error by the software developer.");
            }
        }

        public URLHelper getSuccessURL(OutputFileHandlerForm form)
        {
            return _url;
        }

        public boolean doAction(OutputFileHandlerForm form, BindException errors) throws Exception
        {
            return true;
        }
    }

    public static class OutputFileHandlerForm
    {
        private String _handlerClass;
        private String _outputFileIds;

        public String getHandlerClass()
        {
            return _handlerClass;
        }

        public void setHandlerClass(String handlerClass)
        {
            _handlerClass = handlerClass;
        }

        public String getOutputFileIds()
        {
            return _outputFileIds;
        }

        public void setOutputFileIds(String outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class ExportSequenceFilesAction extends ExportAction<ExportSequenceFilesForm>
    {
        public void export(ExportSequenceFilesForm form, HttpServletResponse response, BindException errors) throws Exception
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

            if (!"zip".equalsIgnoreCase(FileUtil.getExtension(filename)))
            {
                filename += ".zip";
            }

            List<File> files = new ArrayList<>();
            FileType bamFileType = new FileType("bam");
            FileType fastaFileType = new FileType("fasta", FileType.gzSupportLevel.SUPPORT_GZ);
            FileType gzFileType = new FileType("gz");
            for (int id : form.getDataIds())
            {
                ExpData data = ExperimentService.get().getExpData(id);
                if (data == null || !data.getContainer().hasPermission(getUser(), ReadPermission.class) || !data.getFile().exists())
                {
                    throw new NotFoundException("Could not find file " + id);
                }

                files.add(data.getFile());

                if (bamFileType.isType(data.getFile()))
                {
                    File index = new File(data.getFile() + ".bai");
                    if (index.exists())
                    {
                        files.add(index);
                    }
                }
                else if (fastaFileType.isType(data.getFile()))
                {
                    File index = new File(data.getFile() + ".fai");
                    if (index.exists())
                    {
                        files.add(index);
                    }
                    else
                    {
                        if (gzFileType.isType(data.getFile()))
                        {
                            File compressedFileIndex = new File(data.getFile().getParentFile(), FileUtil.getBaseName(data.getFile()) + ".fai");
                            if (compressedFileIndex.exists())
                            {
                                files.add(compressedFileIndex);
                            }
                        }
                    }
                }
            }

            PageFlowUtil.prepareResponseForFile(response, Collections.<String, String>emptyMap(), filename, true);

            try (ZipOutputStream zOut = new ZipOutputStream(response.getOutputStream()))
            {
                for (File f : files)
                {
                    if (!f.exists())
                    {
                        throw new NotFoundException("File " + f.getPath() + " does not exist");
                    }

                    ZipEntry fileEntry = new ZipEntry(f.getName());
                    zOut.putNextEntry(fileEntry);

                    try (FileInputStream in = new FileInputStream(f))
                    {
                        IOUtils.copy(new FileInputStream(f), zOut);
                        zOut.closeEntry();
                    }
                    catch (Exception e)
                    {
                        // insert the stack trace into the zip file
                        ZipEntry errorEntry = new ZipEntry("error.log");
                        zOut.putNextEntry(errorEntry);

                        final PrintStream ps = new PrintStream(zOut, true);
                        ps.println("Failed to complete export of the file: ");
                        e.printStackTrace(ps);
                        zOut.closeEntry();
                    }
                }
            }
        }
    }

    public static class ExportSequenceFilesForm
    {
        private String _zipFileName;
        private int[] _dataIds;

        public String getZipFileName()
        {
            return _zipFileName;
        }

        public void setZipFileName(String zipFileName)
        {
            _zipFileName = zipFileName;
        }

        public int[] getDataIds()
        {
            return _dataIds;
        }

        public void setDataIds(int[] dataIds)
        {
            _dataIds = dataIds;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @CSRF
    public class GetQualiMapPathAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object form, BindException errors)
        {
            PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(QualiMapRunner.CONFIG_PROPERTY_DOMAIN, true);

            Map<String, Object> props = new HashMap<>();
            props.put(QualiMapRunner.QUALIMAP_DIR, configMap.get(QualiMapRunner.QUALIMAP_DIR));
            try
            {
                QualiMapRunner.isQualiMapDirValid();
                props.put("isValid", true);
            }
            catch (ConfigurationException e)
            {
                props.put("validationMessage", e.getMessage());
                props.put("isValid", false);
            }

            return new ApiSimpleResponse(props);
        }
    }

    @RequiresSiteAdmin
    @CSRF
    public class SetQualiMapPathAction extends ApiAction<SetQualiMapForm>
    {
        public ApiResponse execute(SetQualiMapForm form, BindException errors)
        {
            PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(QualiMapRunner.CONFIG_PROPERTY_DOMAIN, true);

            String path = StringUtils.trimToNull(form.getPath());
            configMap.put(QualiMapRunner.QUALIMAP_DIR, path);

            PropertyManager.saveProperties(configMap);

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SetQualiMapForm
    {
        private String _path;

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }
    }
}
