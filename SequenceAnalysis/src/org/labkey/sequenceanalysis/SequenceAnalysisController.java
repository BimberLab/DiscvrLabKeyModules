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

import htsjdk.samtools.SAMException;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.compound.AmbiguityDNACompoundSet;
import org.biojava.nbio.core.sequence.compound.NucleotideCompound;
import org.biojava.nbio.core.sequence.io.DNASequenceCreator;
import org.biojava.nbio.core.sequence.io.FastaReader;
import org.biojava.nbio.core.sequence.io.GenericFastaHeaderParser;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.JFreeChart;
import org.json.old.JSONArray;
import org.json.old.JSONException;
import org.json.old.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.files.FileContentService;
import org.labkey.api.laboratory.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.sequenceanalysis.SequenceDataProvider;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.JobResourceSettings;
import org.labkey.api.sequenceanalysis.pipeline.ParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.pipeline.VariantProcessingStep;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.PrintWriters;
import org.labkey.sequenceanalysis.model.AnalysisModelImpl;
import org.labkey.sequenceanalysis.model.ReferenceLibraryMember;
import org.labkey.sequenceanalysis.pipeline.AlignmentAnalysisJob;
import org.labkey.sequenceanalysis.pipeline.AlignmentImportJob;
import org.labkey.sequenceanalysis.pipeline.IlluminaImportJob;
import org.labkey.sequenceanalysis.pipeline.ImportFastaSequencesPipelineJob;
import org.labkey.sequenceanalysis.pipeline.ImportGenomeTrackPipelineJob;
import org.labkey.sequenceanalysis.pipeline.OrphanFilePipelineJob;
import org.labkey.sequenceanalysis.pipeline.ReadsetImportJob;
import org.labkey.sequenceanalysis.pipeline.ReferenceGenomeImpl;
import org.labkey.sequenceanalysis.pipeline.ReferenceLibraryPipelineJob;
import org.labkey.sequenceanalysis.pipeline.SequenceAlignmentJob;
import org.labkey.sequenceanalysis.pipeline.SequenceConcatPipelineJob;
import org.labkey.sequenceanalysis.pipeline.SequenceJob;
import org.labkey.sequenceanalysis.pipeline.SequenceOutputHandlerJob;
import org.labkey.sequenceanalysis.pipeline.SequenceReadsetHandlerJob;
import org.labkey.sequenceanalysis.pipeline.SequenceTaskHelper;
import org.labkey.sequenceanalysis.pipeline.VariantProcessingJob;
import org.labkey.sequenceanalysis.run.BamHaplotyper;
import org.labkey.sequenceanalysis.run.analysis.AASnpByCodonAggregator;
import org.labkey.sequenceanalysis.run.analysis.AASnpByReadAggregator;
import org.labkey.sequenceanalysis.run.analysis.AlignmentAggregator;
import org.labkey.sequenceanalysis.run.analysis.AvgBaseQualityAggregator;
import org.labkey.sequenceanalysis.run.analysis.BamIterator;
import org.labkey.sequenceanalysis.run.analysis.NtCoverageAggregator;
import org.labkey.sequenceanalysis.run.analysis.NtSnpByPosAggregator;
import org.labkey.sequenceanalysis.run.util.FastqcRunner;
import org.labkey.sequenceanalysis.util.ChainFileValidator;
import org.labkey.sequenceanalysis.util.FastqUtils;
import org.labkey.sequenceanalysis.util.SequenceUtil;
import org.labkey.sequenceanalysis.visualization.VariationChart;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.bind.ServletRequestParameterPropertyValues;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.labkey.sequenceanalysis.SequenceIntegrationTests.PIPELINE_PROP_NAME;

public class SequenceAnalysisController extends SpringActionController
{
    private static final Logger _log = LogManager.getLogger(SequenceAnalysisController.class);
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SequenceAnalysisController.class);

    public final static String CONFIG_PROPERTY_DOMAIN_IMPORT = "org.labkey.sequenceanalysis.importsettings";

    public SequenceAnalysisController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class FastqcReportAction extends SimpleViewAction<FastqcForm>
    {
        @Override
        public void validate(FastqcForm form, BindException errors)
        {
            if (form.getReadsets() == null && form.getFilenames() == null && form.getDataIds() == null && form.getAnalysisIds() == null)
            {
                errors.reject(ERROR_MSG, "Must provide a filename or Exp data Ids");
            }
        }

        @Override
        public ModelAndView getView(FastqcForm form, BindException errors) throws Exception
        {
            //resolve files
            List<File> files = new ArrayList<>();
            Map<File, String> labels = new HashMap<>();

            if (form.getFilenames() != null)
            {
                for (String fn : form.getFilenames())
                {
                    File root = FileContentService.get().getFileRoot(getContainer(), FileContentService.ContentType.files);
                    File target = new File(root, fn);
                    if (target.exists())
                    {
                        files.add(target);
                    }
                }
            }

            if (form.getDataIds() != null)
            {
                if (ArrayUtils.isEmpty(form.getDataIds()))
                {
                    errors.reject(ERROR_MSG, "List of data IDs is empty");
                    return null;
                }

                for (Integer id : form.getDataIds())
                {
                    if (id == null)
                    {
                        continue;
                    }

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
                    SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(id, getUser());
                    for (ReadDataImpl d : rs.getReadDataImpl())
                    {
                        if (d.getFile1() != null)
                        {
                            files.add(d.getFile1());
                            labels.put(d.getFile1(), "Readset " + rs.getName());
                        }

                        if (d.getFile2() != null)
                        {
                            files.add(d.getFile2());
                            labels.put(d.getFile2(), "Readset " + rs.getName());
                        }
                    }
                }
            }

            if (form.getAnalysisIds() != null)
            {
                for (int id : form.getAnalysisIds())
                {
                    AnalysisModel m = AnalysisModelImpl.getFromDb(id, getUser());
                    if (m != null && m.getAlignmentFileObject() != null)
                    {
                        files.add(m.getAlignmentFileObject());
                        labels.put(m.getAlignmentFileObject(), "Analysis " + m.getRowId());
                    }
                }
            }

            if (files.size() == 0)
            {
                return new HtmlView("Error: either no files provided or the files did not exist on the server");
            }

            FastqcRunner runner = new FastqcRunner(null);
            if (!form.isCacheResults())
            {
                runner.setCacheResults(false);
            }
            try
            {
                String html = runner.execute(files, labels);
                return new HtmlView("FastQC Report", HtmlString.unsafe(html));
            }
            catch (FileNotFoundException e)
            {
                return new HtmlView("Error: " + e.getMessage());
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("FastQC Report"); //necessary to set page title, it seems
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class SequenceAnalysisAction extends BasePipelineStepAction
    {
        public SequenceAnalysisAction()
        {
            super("views/sequenceAnalysis.html");
        }

        @Override
        public void addNavTrail(NavTree tree)
        {
            tree.addChild("Sequence Analysis");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class VariantProcessingAction extends BasePipelineStepAction
    {
        public VariantProcessingAction()
        {
            super("views/variantProcessing.html");
        }

        @Override
        public void addNavTrail(NavTree tree)
        {
            tree.addChild("1".equals(getViewContext().getActionURL().getParameter("showGenotypeGVCFs")) ? "Genotype gVCFs" : "Filter/Process Variants");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class AlignmentImportAction extends BasePipelineStepAction
    {
        public AlignmentImportAction()
        {
            super("views/alignmentImport.html");
        }

        @Override
        public void addNavTrail(NavTree tree)
        {
            tree.addChild("Import Alignments");
        }
    }

    abstract public class BasePipelineStepAction extends SimpleViewAction<Object>
    {
        protected String _htmlFile;

        public BasePipelineStepAction(String htmlFile)
        {
            _htmlFile = htmlFile;
        }

        @Override
        public ModelAndView getView(Object form, BindException errors)
        {
            LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();
            for (PipelineStepProvider<?> fact : SequencePipelineService.get().getAllProviders())
            {
                cds.addAll(fact.getClientDependencies());
            }

            Module module = ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class);

            ModuleHtmlView view = ModuleHtmlView.get(module, Path.parse(_htmlFile));
            assert view != null;
            view.addClientDependencies(cds);
            getPageConfig().setIncludePostParameters(true);

            return view;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class AlignmentAnalysisAction extends BasePipelineStepAction
    {
        public AlignmentAnalysisAction()
        {
            super("views/alignmentAnalysis.html");
        }

        public void addNavTrail(NavTree tree)
        {
            tree.addChild("Analyze Alignments");
        }
    }

    @RequiresPermission(ReadPermission.class)
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

    @RequiresPermission(ReadPermission.class)
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

    @RequiresPermission(ReadPermission.class)
    public class FindOrphanFilesAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            setTitle("Find Orphan Sequence Files");

            HtmlView view = new HtmlView("This will start a pipeline job that will inspect all files in this folder to identify potential orphan or otherwise unnecessary files.  Do you want to continue?");
            return view;
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(getContainer());
            PipelineService.get().queueJob(new OrphanFilePipelineJob(getContainer(), getUser(), getViewContext().getActionURL(), pipelineRoot));

            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {

        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(getContainer());
        }
    }

    @RequiresSiteAdmin
    public class UpdateSequenceLengthAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object form, BindException errors) throws Exception
        {
            if (!getContainer().isRoot())
            {
                throw new UnauthorizedException("This can only be used from the root container");
            }

            StringBuilder html = new StringBuilder();

            html.append("This will calculate the sequence length field for any reference NT sequences lacking it.  Do you want to continue?");

            return new HtmlView(html.toString());
        }

        @Override
        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            SequenceAnalysisManager.get().apppendSequenceLength(getUser(), _log);

            return true;
        }

        @Override
        public void validateCommand(Object form, Errors errors)
        {

        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }
    }

    public static class DeleteForm extends QueryForm
    {
        private Integer[] _jobIds;
        private boolean _doDelete = false;

        public Integer[] getJobIds()
        {
            return _jobIds;
        }

        public void setJobIds(Integer[] jobIds)
        {
            _jobIds = jobIds;
        }

        public boolean isDoDelete()
        {
            return _doDelete;
        }

        public void setDoDelete(boolean doDelete)
        {
            _doDelete = doDelete;
        }
    }

    //TODO: add genome
    @RequiresPermission(DeletePermission.class)
    public class DeleteRecordsAction extends ConfirmAction<DeleteForm>
    {
        private TableInfo _table;

        public void validateCommand(DeleteForm form, Errors errors)
        {
            if (form.getSchema() == null)
            {
                errors.reject(ERROR_MSG, "No schema provided");
                return;
            }

            if (form.getQueryName() == null)
            {
                errors.reject(ERROR_MSG, "No queryName provided");
                return;
            }

            _table = SequenceAnalysisSchema.getInstance().getSchema().getTable(form.getQueryName());
            if (_table == null)
            {
                errors.reject(ERROR_MSG, "Unknown table: " + form.getQueryName());
                return;
            }

            if (_table.getColumn("container") != null)
            {
                Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
                List<Integer> keys = new ArrayList<>();
                for (String key : ids)
                {
                    keys.add(ConvertHelper.convert(key, Integer.class));
                }
                SimpleFilter filter = new SimpleFilter(FieldKey.fromString(_table.getPkColumns().get(0).getColumnName()), keys, CompareType.IN);

                TableSelector ts = new TableSelector(_table, Collections.singleton("container"), filter, null);
                ts.forEachResults(rs -> {
                    Container c = ContainerManager.getForId(rs.getString("container"));
                    if (!c.hasPermission(getUser(), DeletePermission.class))
                        throw new UnauthorizedException("User does not have delete permission on folder: " + c.getTitle());
                });
            }
        }

        private void findAnalysesToDelete(Collection<Integer> keys, StringBuilder msg, Set<Integer> outputFileIds, Set<Integer> expRunsToDelete)
        {
            appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY, "Alignment Records", keys, "analysis_id", "rowid");
            outputFileIds.addAll(appendTotal(msg, SequenceAnalysisSchema.TABLE_OUTPUTFILES, "Output Files", keys, "analysis_id", "rowid"));
            appendTotal(msg, SequenceAnalysisSchema.TABLE_COVERAGE, "Coverage Records", keys, "analysis_id", "rowid");
            appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "analysis_id", "rowid");
            appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "analysis_id", "rowid");
            appendTotal(msg, SequenceAnalysisSchema.TABLE_QUALITY_METRICS, "Quality Metrics", keys, "analysis_id", "rowid");

            expRunsToDelete.addAll(getExpRunIds(SequenceAnalysisSchema.TABLE_ANALYSES, keys, "rowid", "runId"));
        }

        @Override
        public ModelAndView getConfirmView(DeleteForm form, BindException errors) throws Exception
        {
            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);
            List<Integer> keys = new ArrayList<>();
            for (String key : ids)
            {
                keys.add(ConvertHelper.convert(key, Integer.class));
            }

            Set<Integer> expRunsToDelete = new HashSet<>();
            Set<Integer> readsetIds = new HashSet<>();
            Set<Integer> readDataIds = new HashSet<>();
            Set<Integer> analysisIds = new HashSet<>();
            Set<Integer> outputFileIds = new HashSet<>();

            StringBuilder msg = new StringBuilder("Are you sure you want to delete the following " + keys.size() + " ");
            if (SequenceAnalysisSchema.TABLE_ANALYSES.equals(_table.getName()))
            {
                msg.append("analyses: " + StringUtils.join(keys, ", ") + "?  This will delete the analyses, plus all associated data.  This includes:<br>");
                analysisIds.addAll(keys);
                findAnalysesToDelete(keys, msg, outputFileIds, expRunsToDelete);
            }
            else if (SequenceAnalysisSchema.TABLE_READSETS.equals(_table.getName()))
            {
                msg.append("readsets: " + StringUtils.join(keys, ", ") + "?  This will delete the readsets, plus all associated data.  This includes:<br>");
                readsetIds.addAll(keys);
                readDataIds.addAll(appendTotal(msg, SequenceAnalysisSchema.TABLE_READ_DATA, "Sequence File Records", keys, "readset", "rowid"));
                analysisIds.addAll(appendTotal(msg, SequenceAnalysisSchema.TABLE_ANALYSES, "Analyses", keys, "readset", "rowid"));
                outputFileIds.addAll(appendTotal(msg, SequenceAnalysisSchema.TABLE_OUTPUTFILES, "Output Files", keys, "readset", "rowid"));
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY, "Alignment Records", keys, "analysis_id/readset", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_COVERAGE, "Coverage Records", keys, "analysis_id/readset", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "analysis_id/readset", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "analysis_id/readset", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_QUALITY_METRICS, "Quality Metrics", keys, "readset", "rowid");

                expRunsToDelete.addAll(getExpRunIds(SequenceAnalysisSchema.TABLE_READSETS, keys, "rowid", "runId"));
            }
            else if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equals(_table.getName()))
            {
                msg.append("NT reference sequences: " + StringUtils.join(keys, ", ") + "?  This will delete the reference sequences, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES, "Reference AA Sequences", keys, "ref_nt_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_FEATURES, "NT Features", keys, "ref_nt_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_COVERAGE, "Coverage Records", keys, "ref_nt_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_ALIGNMENT_SUMMARY_JUNCTION, "Alignment Records", keys, "ref_nt_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS, "Reference Library Member Records", keys, "ref_nt_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_NT_SNP_BY_POS, "NT SNP Records", keys, "ref_nt_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "ref_nt_id", "rowid");
            }
            else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equals(_table.getName()))
            {
                msg.append("AA reference sequences: " + StringUtils.join(keys, ", ") + "?  This will delete the reference sequences, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_FEATURES, "AA features", keys, "ref_aa_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_DRUG_RESISTANCE, "drug resistance mutations", keys, "ref_aa_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_AA_SNP_BY_CODON, "AA SNP Records", keys, "ref_aa_id", "rowid");
            }
            else if (SequenceAnalysisSchema.TABLE_REF_LIBRARIES.equals(_table.getName()))
            {
                msg.append("Reference genomes: " + StringUtils.join(keys, ", ") + "?  This will delete the reference genomes, plus all associated data.  This includes:<br>");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_REF_LIBRARY_MEMBERS, "genome sequences", keys, "library_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_LIBRARY_TRACKS, "tracks", keys, "library_id", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_CHAIN_FILES, "chain files from this genome", keys, "genomeId1", "rowid");
                appendTotal(msg, SequenceAnalysisSchema.TABLE_CHAIN_FILES, "chain files to this genome", keys, "genomeId2", "rowid");

            }
            else if (SequenceAnalysisSchema.TABLE_OUTPUTFILES.equals(_table.getName()))
            {
                msg.append("output files: " + StringUtils.join(keys, ", ") + "?<br>");
                outputFileIds.addAll(keys);

                //we will delete these expDatas, so find any analysis records matching this file
                Collection<Integer> additionalAnalysisIds = SequenceAnalysisManager.get().deleteOutputFiles(keys, getUser(), getContainer(), false);
                analysisIds.addAll(additionalAnalysisIds);

                if (!additionalAnalysisIds.isEmpty())
                {
                    msg.append("<br><br>");
                    msg.append("The following " + additionalAnalysisIds.size() + " analyses will also be deleted, along with these associated records/files:<br>");
                    findAnalysesToDelete(additionalAnalysisIds, msg, outputFileIds, expRunsToDelete);
                }

                //also pipeline jobs
                expRunsToDelete.addAll(getExpRunIds(SequenceAnalysisSchema.TABLE_OUTPUTFILES, keys, "rowid", "runId"));
                expRunsToDelete.addAll(getExpRunIds(SequenceAnalysisSchema.TABLE_ANALYSES, additionalAnalysisIds, "rowid", "runId"));

                //Note: this number could be reduced if there are other records from the same ExpData.  A check is performed at actual delete-time.
                List<Integer> dataIds = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), PageFlowUtil.set("dataId"), new SimpleFilter(FieldKey.fromString("rowid"), outputFileIds, CompareType.IN), null).getArrayList(Integer.class);
                if (!dataIds.isEmpty())
                {
                    appendTotal(msg, SequenceAnalysisSchema.TABLE_QUALITY_METRICS, "quality metrics", dataIds, "dataId", "rowid");
                }
            }

            if (!expRunsToDelete.isEmpty())
            {
                getAdditionalRuns(readsetIds, readDataIds, analysisIds, outputFileIds, expRunsToDelete);
            }

            if (!expRunsToDelete.isEmpty())
            {
                msg.append("<br><br>");
                msg.append("The following pipeline jobs appear to be unused, and will be deleted.  Be aware, this will delete all files as well:<br><br>");
                for (Integer runId : expRunsToDelete)
                {
                    if (runId == null)
                    {
                        _log.error("attempting to delete a null runId");
                        continue;
                    }

                    ExpRun run = ExperimentService.get().getExpRun(runId);
                    if (run != null)
                    {
                        msg.append("Pipeline run: " + run.getName() + "<br>");
                        if (run.getJobId() != null)
                        {
                            PipelineStatusFile sf = PipelineService.get().getStatusFile(run.getJobId());
                            if (sf != null)
                            {
                                File target = new File(sf.getFilePath()).getParentFile();
                                String relativePath = FileUtil.relativize(PipelineService.get().getPipelineRootSetting(run.getContainer()).getRootPath(), target, false);
                                ActionURL url = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(sf.lookupContainer(), getViewContext().getActionURL(), relativePath);
                                msg.append("Folder: <a href='" + url.toString() + "' target='_blank'>" + target.getPath() + "</a><br><input type='hidden' name='jobIds' value='" + sf.getRowId() + "'/>");
                            }
                        }
                        msg.append("<br>");
                    }
                }
            }

            msg.append("<input type='hidden' name='doDelete' value='1'");

            setTitle("Delete Sequence Records");

            return new HtmlView(msg.toString());
        }

        @Override
        public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            PropertyValues values = getPropertyValues();
            if (null == values)
                values = new ServletRequestParameterPropertyValues(request);

            BindException errors = bindParameters(values);
            DeleteForm form = (DeleteForm)errors.getTarget();

            if (!form.isDoDelete()) {
                validate(form, errors);
                boolean success = !errors.hasErrors();
                if (success)
                {
                    setProperties(values);
                    getViewContext().setBindPropertyValues(getPropertyValues());
                    ModelAndView confirmView = getConfirmView(form, errors);
                    JspView<ConfirmAction> confirmWrapper = new JspView<>("/org/labkey/api/action/confirmWrapper.jsp", this);
                    confirmWrapper.setBody(confirmView);
                    getPageConfig().setTemplate(PageConfig.Template.Dialog);
                    return confirmWrapper;
                }
            }

            return super.handleRequest(request, response);
        }

        private void getAdditionalRuns(Set<Integer> readsetIds, Set<Integer> readDataIds, Set<Integer> analysisIds, Set<Integer> outputFileIds, Set<Integer> expRunsToDelete)
        {
            Set<Integer> runIdsStillInUse = new HashSet<>();

            //work backwards, adding additional pipeline jobs that will become orphans:
            runIdsStillInUse.addAll(getRunIdsInUse(SequenceAnalysisSchema.TABLE_READSETS, expRunsToDelete, readsetIds));
            runIdsStillInUse.addAll(getRunIdsInUse(SequenceAnalysisSchema.TABLE_READ_DATA, expRunsToDelete, readDataIds));
            runIdsStillInUse.addAll(getRunIdsInUse(SequenceAnalysisSchema.TABLE_ANALYSES, expRunsToDelete, analysisIds));
            runIdsStillInUse.addAll(getRunIdsInUse(SequenceAnalysisSchema.TABLE_OUTPUTFILES, expRunsToDelete, outputFileIds));

            expRunsToDelete.removeAll(runIdsStillInUse);
        }

        private List<Integer> getRunIdsInUse(String tableName, Collection<Integer> expRunsToDelete, Collection<Integer> pks)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("runId"), expRunsToDelete, CompareType.IN);
            filter.addCondition(FieldKey.fromString("rowid"), pks, CompareType.NOT_IN);
            filter.addCondition(FieldKey.fromString("runId"), null, CompareType.NONBLANK);

            return new TableSelector(SequenceAnalysisSchema.getTable(tableName), PageFlowUtil.set("runId"), filter, null).getArrayList(Integer.class);
        }

        private Set<Integer> getExpRunIds(String tableName, Collection<Integer> keys, String pkColName, String runIdCol)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString(pkColName), keys, CompareType.IN);
            filter.addCondition(FieldKey.fromString(runIdCol), null, CompareType.NONBLANK);
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(tableName), PageFlowUtil.set(runIdCol), filter, null);

            return new HashSet<>(ts.getArrayList(Integer.class));
        }

        private Set<Integer> appendTotal(StringBuilder sb, String tableName, String noun, Collection<Integer> keys, String filterCol, String pkCol)
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString(filterCol), keys, CompareType.IN);
            TableSelector ts = new TableSelector(SequenceAnalysisSchema.getInstance().getSchema().getTable(tableName), PageFlowUtil.set(pkCol), filter, null);
            Set<Integer> total = new HashSet<>(ts.getArrayList(Integer.class));
            sb.append("<br>" + total.size() + " " + noun);

            return total;
        }

        public boolean handlePost(DeleteForm form, BindException errors) throws Exception
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
                    SequenceAnalysisManager.get().deleteAnalysis(getUser(), getContainer(), rowIds);
                }
                else if (SequenceAnalysisSchema.TABLE_READSETS.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteReadset(rowIds, getUser(), getContainer());
                }
                else if (SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteRefNtSequence(getUser(), getContainer(), rowIds);
                }
                else if (SequenceAnalysisSchema.TABLE_REF_LIBRARIES.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteReferenceLibraries(getUser(), rowIds);
                }
                else if (SequenceAnalysisSchema.TABLE_REF_AA_SEQUENCES.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteRefAaSequence(rowIds);
                }
                else if (SequenceAnalysisSchema.TABLE_OUTPUTFILES.equals(_table.getName()))
                {
                    SequenceAnalysisManager.get().deleteOutputFiles(rowIds, getUser(), getContainer(), true);
                }

                if (form.getJobIds() != null)
                {
                    PipelineService.get().deleteStatusFile(getContainer(), getUser(), true, Arrays.asList(form.getJobIds()));
                }
            }
            catch (OptimisticConflictException e)
            {
                //if someone else already deleted this, no need to throw exception
                _log.error(e.getMessage(), e);
            }
            catch (Exception e)
            {
                _log.error(e.getMessage(), e);

                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            return true;
        }

        public URLHelper getSuccessURL(DeleteForm form)
        {
            URLHelper url = form.getReturnURLHelper();
            return url != null ? url : _table.getGridURL(getContainer()) != null ? _table.getGridURL(getContainer()) : getContainer().getStartURL(getUser());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetSequencePipelineEnabledAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            boolean isExternalPipelineEnabled = Boolean.parseBoolean(System.getProperty(PIPELINE_PROP_NAME));
            JSONObject ret = new JSONObject();
            ret.put("isPipelineEnabled", isExternalPipelineEnabled);

            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetAnalysisToolDetailsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> ret = new HashMap<>();

            Map<Class<? extends PipelineStep>, String> map = SequencePipelineServiceImpl.get().getPipelineStepTypes();
            for (Class<? extends  PipelineStep> step : map.keySet())
            {
                JSONArray list = new JSONArray();
                for (PipelineStepProvider fact : SequencePipelineService.get().getProviders(step))
                {
                    list.put(fact.toJSON());
                }

                ret.put(map.get(step), list);
            }

            JSONObject resourceSettings = getReourceSettingsJson();
            if (resourceSettings != null)
            {
                JSONArray arr = new JSONArray();
                arr.put(resourceSettings);
                ret.put("resourceSettings", arr);
            }

            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetResourceSettingsJsonAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> ret = new HashMap<>();

            JSONObject resourceSettings = getReourceSettingsJson();
            if (resourceSettings != null)
            {
                JSONArray arr = new JSONArray();
                arr.put(resourceSettings);
                ret.put("resourceSettings", arr);
            }

            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class SaveAnalysisAsTemplateAction extends MutatingApiAction<SaveAnalysisAsTemplateForm>
    {
        @Override
        public ApiResponse execute(SaveAnalysisAsTemplateForm form, BindException errors) throws Exception
        {
            if (StringUtils.isEmpty(form.getJson()))
            {
                errors.reject(ERROR_MSG, "No analysis Id provided");
                return null;
            }

            if (StringUtils.isEmpty(form.getTaskId()))
            {
                errors.reject(ERROR_MSG, "No taskId provided");
                return null;
            }

            Map<String, Object> toSave = new CaseInsensitiveHashMap<>();
            toSave.put("name", form.getName());
            toSave.put("description", form.getDescription());
            toSave.put("taskid", form.getTaskId());
            toSave.put("json", new JSONObject(form.getJson()));

            Container c = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();

            TableInfo ti = QueryService.get().getUserSchema(getUser(), c, SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_SAVED_ANALYSES);

            //check if there is an existing
            SimpleFilter filter = new SimpleFilter(FieldKey.fromString("name"), form.getName());
            filter.addCondition(FieldKey.fromString("taskid"), form.getTaskId());
            TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowid"), filter, null);
            if (ts.exists())
            {
                List<Map<String, Object>> oldKeys = Arrays.asList(ts.getMapArray());
                ti.getUpdateService().updateRows(getUser(), getContainer(), Arrays.asList(toSave), oldKeys, null, new HashMap<String, Object>());
            }
            else
            {
                ti.getUpdateService().insertRows(getUser(), getContainer(), Arrays.asList(toSave), new BatchValidationException(), null, new HashMap<String, Object>());
            }

            return new ApiSimpleResponse("Success", true);
        }
    }

    public static class SaveAnalysisAsTemplateForm
    {
        private String _name;
        private String _description;
        private String _taskId;
        private String _json;

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

        public String getTaskId()
        {
            return _taskId;
        }

        public void setTaskId(String taskId)
        {
            _taskId = taskId;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getJson()
        {
            return _json;
        }

        public void setJson(String json)
        {
            _json = json;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ValidateReadsetFilesAction extends ReadOnlyApiAction<ValidateReadsetImportForm>
    {
        @Override
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
                    File root = PipelineService.get().findPipelineRoot(getContainer()).getRootPath();
                    File base = root;
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
                            map.put("relPath", FileUtil.relativePath(FileUtil.getAbsoluteCaseSensitiveFile(root).getPath(), FileUtil.getAbsoluteCaseSensitiveFile(f).getPath()));
                            map.put("container", getContainer().getId());
                            map.put("containerPath", getContainer().getPath());
                            String basename = SequenceTaskHelper.getUnzippedBaseName(fileName);
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

                            if (SequenceUtil.FILETYPE.bam.getFileType().isType((f)))
                            {
                                map.put("readgroups", SequenceUtil.getReadGroupsForBam(f));
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
                        if (SequenceUtil.FILETYPE.bam.getFileType().isType((d.getFile())))
                        {
                            map.put("readgroups", SequenceUtil.getReadGroupsForBam(d.getFile()));
                        }

                        String basename = SequenceTaskHelper.getUnzippedBaseName(d.getFile().getName());
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
                            String msg = "File has already been used as an input for readsets: " + d.getFile().getName();
                            errorsList.add(msg);
                            map.put("error", msg);
                        }

                        TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_READ_DATA);

                        //forward reads
                        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("fileid1"), d.getRowId());
                        Container c = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
                        filter.addClause(ContainerFilter.current(c).createFilterClause(SequenceAnalysisSchema.getInstance().getSchema(), FieldKey.fromString("container")));
                        TableSelector ts = new TableSelector(ti, Collections.singleton("readset"), filter, null);
                        Integer[] readsets1 = ts.getArray(Integer.class);

                        //reverse reads
                        SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("fileid2"), d.getRowId());
                        filter2.addClause(ContainerFilter.current(c).createFilterClause(SequenceAnalysisSchema.getInstance().getSchema(), FieldKey.fromString("container")));
                        TableSelector ts2 = new TableSelector(ti, Collections.singleton("readset"), filter2, null);
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
                _log.error(e);
                return null;
            }
            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class AnalyzeBamAction extends MutatingApiAction<AnalyzeBamForm>
    {
        @Override
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

            Logger log = LogManager.getLogger(SequenceAnalysisController.class);

            for (AnalysisModel m : models)
            {
                File inputFile = m.getAlignmentData().getFile();
                File refFile = m.getReferenceLibraryData(getUser()).getFile();
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

                    ntSnp.setCoverageAggregator(coverage, aggregatorTypes.contains("coverage"));
                    aggregators.add(ntSnp);
                }

                if (aggregatorTypes.contains("aabycodon"))
                {
                    AASnpByCodonAggregator aaCodon = new AASnpByCodonAggregator(log, refFile, avg, params);
                    if (coverage == null)
                        coverage = new NtCoverageAggregator(log, refFile, avg, params);

                    aaCodon.setCoverageAggregator(coverage, aggregatorTypes.contains("coverage"));
                    aggregators.add(aaCodon);
                }

                bi.addAggregators(aggregators);

                if (form.getRefName() != null && form.getStart() > 0 && form.getStop() > 0)
                    bi.iterateReads(form.getRefName(), form.getStart(), form.getStop());
                else
                    bi.iterateReads();

                for (AlignmentAggregator agg : aggregators)
                {
                    agg.writeOutput(getUser(), c, m);
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

    @RequiresPermission(ReadPermission.class)
    public class GetAASnpsAction extends ReadOnlyApiAction<AASNPForm>
    {
        @Override
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

            ExpData ref = model.getReferenceLibraryData(getUser());
            if (ref == null || !ref.getFile().exists())
            {
                resultProperties.put("success", false);
                resultProperties.put("exception", "Unable to find reference FASTA, or the file does not exist");
                return new ApiSimpleResponse(resultProperties);
            }

            Logger log = LogManager.getLogger(BamIterator.class);
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

    @RequiresPermission(ReadPermission.class)
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

            PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), filename, true);
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
        private boolean _cacheResults = true;

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

        public boolean isCacheResults()
        {
            return _cacheResults;
        }

        public void setCacheResults(boolean cacheResults)
        {
            _cacheResults = cacheResults;
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

    @RequiresPermission(ReadPermission.class)
    public class GenerateChartAction extends ReadOnlyApiAction<GenerateChartForm>
    {
        @Override
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

    @RequiresPermission(ReadPermission.class)
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

    @RequiresPermission(InsertPermission.class)
    public class StartPipelineJobAction extends MutatingApiAction<AnalyzeForm>
    {
        @Override
        public ApiResponse execute(AnalyzeForm form, BindException errors) throws Exception
        {
            try
            {
                if (form.getJobName() == null)
                {
                    errors.reject(ERROR_MSG, "Must specify a protocol name");
                    return null;
                }

                if (form.getType() == null)
                {
                    errors.reject(ERROR_MSG, "Must specify a job type");
                    return null;
                }

                if (form.getJobParameters() == null)
                {
                    errors.reject(ERROR_MSG, "Must specify job parameters");
                    return null;
                }

                PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
                if (pr == null || !pr.isValid())
                    throw new NotFoundException();

                Set<SequenceJob> jobs = new HashSet<>();
                switch (form.getType())
                {
                    case alignment:
                        if (form.getReadsetIds() == null)
                        {
                            errors.reject(ERROR_MSG, "Must supply readset Ids");
                            return null;
                        }

                        JSONObject params = new JSONObject(form.getJobParameters());
                        params.remove("readsetIds");
                        jobs.addAll(SequenceAlignmentJob.createForReadsets(getContainer(), getUser(), form.getJobName(), form.getDescription(), params, form.getReadsetIds(), form.isSubmitJobToReadsetContainer()));
                        break;
                    case alignmentAnalysis:
                        if (form.getAnalysisIds() == null)
                        {
                            errors.reject(ERROR_MSG, "Must supply analysis Ids");
                            return null;
                        }

                        jobs.addAll(AlignmentAnalysisJob.createForAnalyses(getContainer(), getUser(), form.getJobName(), form.getDescription(), form.getJobParameters(), form.getAnalysisIds(), form.isSubmitJobToReadsetContainer()));
                        break;
                    case readsetImport:
                        jobs.addAll(ReadsetImportJob.create(getContainer(), getUser(), form.getJobName(), form.getDescription(), form.getJobParameters(), form.getFiles(pr)));
                        break;
                    case illuminaImport:
                        jobs.addAll(IlluminaImportJob.create(getContainer(), getUser(), form.getJobName(), form.getDescription(), form.getJobParameters(), form.getFiles(pr)));
                        break;
                    case alignmentImport:
                        jobs.addAll(AlignmentImportJob.create(getContainer(), getUser(), form.getJobName(), form.getDescription(), form.getJobParameters(), form.getFiles(pr)));
                        break;
                    default:
                        throw new PipelineJobException("Unknown analysis type: " + form.getType());
                }

                Set<String> jobGUIDs = new HashSet<>();
                for (SequenceJob j : jobs)
                {
                    PipelineService.get().queueJob(j);
                    jobGUIDs.add(j.getJobGUID());
                }

                Map<String, Object> resultProperties = new HashMap<>();
                resultProperties.put("jobGUIDs", jobGUIDs);
                resultProperties.put("status", "success");

                return new ApiSimpleResponse(resultProperties);
            }
            catch (ClassNotFoundException | PipelineValidationException e)
            {
                throw new ApiUsageException(e);
            }
        }
    }

    public static class AnalyzeForm extends SimpleApiJsonForm
    {
        public static enum TYPE
        {
            alignment(),
            readsetImport(),
            illuminaImport(),
            alignmentImport(),
            alignmentAnalysis(),
        }

        public String getJobName()
        {
            return getNewJsonObject().optString("jobName");
        }

        public String getDescription()
        {
            return getNewJsonObject().optString("description");
        }

        public TYPE getType()
        {
            return !getNewJsonObject().has("type") ? null : TYPE.valueOf(getNewJsonObject().getString("type"));
        }

        public JSONObject getJobParameters()
        {
            return getJsonObject().optJSONObject("jobParameters");
        }

        public JSONArray getReadsetIds()
        {
            return getJobParameters() == null ? null : getJobParameters().optJSONArray("readsetIds");
        }

        public JSONArray getAnalysisIds()
        {
            return getJobParameters() == null ? null : getJobParameters().optJSONArray("analysisIds");
        }

        public boolean isSubmitJobToReadsetContainer()
        {
            return getJobParameters() == null ? false : getJobParameters().optBoolean("submitJobToReadsetContainer", false);
        }

        public List<File> getFiles(PipeRoot pr) throws PipelineValidationException
        {
            if (getJobParameters() == null || getJobParameters().get("inputFiles") == null)
            {
                return null;
            }

            List<File> ret = new ArrayList<>();
            JSONArray inputFiles = getJobParameters().getJSONArray("inputFiles");
            String path = getJobParameters().optString("path");
            for (JSONObject o : inputFiles.toJSONObjectArray())
            {
                if (o.containsKey("dataId"))
                {
                    ExpData d = ExperimentService.get().getExpData(o.getInt("dataId"));
                    if (d == null || d.getFile() == null)
                    {
                        throw new PipelineValidationException("Unknown dataId: " + o.get("dataId"));
                    }
                    else if (!d.getFile().exists())
                    {
                        throw new PipelineValidationException("Missing file for data: " + o.get("dataId"));
                    }

                    ret.add(d.getFile());
                }
                else if (o.containsKey("relPath") || o.containsKey("fileName"))
                {
                    File f;
                    if (o.get("relPath") == null)
                    {
                        if (path != null)
                        {
                            f = pr.resolvePath(path);
                            f = new File(f, o.getString("fileName"));
                        }
                        else
                        {
                            f = pr.resolvePath(o.getString("fileName"));
                        }
                    }
                    else
                    {
                        f = pr.resolvePath(o.getString("relPath"));
                    }

                    if (f == null || !f.exists())
                    {
                        throw new PipelineValidationException("Unknown file: " + o.getString("relPath") + " / " + o.getString("fileName"));
                    }

                    ret.add(f);
                }
                else if (o.containsKey("filePath"))
                {
                    File f = new File(o.getString("filePath"));
                    if (!f.exists())
                    {
                        throw new PipelineValidationException("Unknown file: " + f.getPath());
                    }
                }
                else
                {
                    throw new PipelineValidationException("Invalid file: " + o.toString());
                }
            }

            return ret;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class CreateReferenceLibraryAction extends MutatingApiAction<CreateReferenceLibraryForm>
    {
        @Override
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

            //always sort by name
            TableInfo refNtTable = QueryService.get().getUserSchema(getUser(), getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
            List<Integer> sequenceIds = new TableSelector(refNtTable, PageFlowUtil.set("rowid"), new SimpleFilter(FieldKey.fromString("rowid"), new ArrayList<>(Arrays.asList(form.getSequenceIds())), CompareType.IN), new Sort("name")).getArrayList(Integer.class);
            if (form.getSequenceIds().length != sequenceIds.size())
            {
                errors.reject(ERROR_MSG, "One or more sequence IDs not found.  Ids found in the database were: " + StringUtils.join(sequenceIds, ";"));
                return null;
            }

            List<ReferenceLibraryMember> members = new ArrayList<>();
            int idx = 0;
            for (Integer seqId : sequenceIds)
            {
                if (form.getIntervals() != null)
                {
                    String intervals = form.getIntervals()[idx];
                    if (StringUtils.trimToNull(intervals) != null)
                    {
                        for (String t : intervals.split(","))
                        {
                            ReferenceLibraryMember m = new ReferenceLibraryMember();
                            m.setRefNtId(seqId);

                            String[] coordinates = t.split("-");
                            if (coordinates.length != 2)
                            {
                                errors.reject(ERROR_MSG, "Inproper interval: [" + t + "]");
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
                        m.setRefNtId(seqId);
                        members.add(m);
                    }
                }
                else
                {
                    ReferenceLibraryMember m = new ReferenceLibraryMember();
                    m.setRefNtId(seqId);
                    members.add(m);
                }

                idx++;
            }

            SequenceAnalysisManager.get().createReferenceLibrary(getContainer(), getUser(), form.getName(), form.getAssemblyId(), form.getDescription(), members, form.isSkipCacheIndexes(), form.isSkipTriggers(), null, null);

            return new ApiSimpleResponse("Success", true);
        }
    }

    public static class CreateReferenceLibraryForm
    {
        private String _name;
        private String _assemblyId;
        private String _description;
        private Integer[] _sequenceIds;
        private String[] _intervals;
        private boolean _skipCacheIndexes = false;
        private boolean _skipTriggers = false;
        private String _genomeBuild;
        private String _compareSpecies;

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

        public boolean isSkipCacheIndexes()
        {
            return _skipCacheIndexes;
        }

        public void setSkipCacheIndexes(boolean skipCacheIndexes)
        {
            _skipCacheIndexes = skipCacheIndexes;
        }

        public String getAssemblyId()
        {
            return _assemblyId;
        }

        public void setAssemblyId(String assemblyId)
        {
            _assemblyId = assemblyId;
        }

        public boolean isSkipTriggers()
        {
            return _skipTriggers;
        }

        public void setSkipTriggers(boolean skipTriggers)
        {
            _skipTriggers = skipTriggers;
        }

        public String getGenomeBuild()
        {
            return _genomeBuild;
        }

        public void setGenomeBuild(String genomeBuild)
        {
            _genomeBuild = genomeBuild;
        }

        public String getCompareSpecies()
        {
            return _compareSpecies;
        }

        public void setCompareSpecies(String compareSpecies)
        {
            _compareSpecies = compareSpecies;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ImportFastaSequencesAction extends AbstractFileUploadAction<ImportFastaSequencesForm>
    {
        @Override
        protected void setContentType(HttpServletResponse response)
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading FASTA files", HttpServletResponse.SC_NOT_FOUND);

            AssayFileWriter writer = new AssayFileWriter();
            PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());

            File targetDirectory = root.getRootPath();

            return writer.findUniqueFileName(filename, targetDirectory);
        }

        @Override
        public String getResponse(ImportFastaSequencesForm form, Map<String, Pair<File, String>> files)
        {
            JSONObject resp = new JSONObject();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();

                    Map<String, String> params = form.getNtParams();
                    Map<String, Object> libraryParams = form.getLibraryParams();

                    try
                    {
                        Container target = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
                        PipeRoot root = PipelineService.get().getPipelineRootSetting(target);

                        ImportFastaSequencesPipelineJob job = new ImportFastaSequencesPipelineJob(target, getUser(), null, root, Arrays.asList(file), params, form.isSplitWhitespace(), form.isCreateLibrary(), libraryParams);
                        job.setDeleteInputs(true);
                        PipelineService.get().queueJob(job);

                        resp.put("jobId", job.getJobGUID());
                    }
                    catch (PipelineValidationException e)
                    {
                        throw new IllegalArgumentException(e);
                    }

                    resp.put("success", true);
                }
            }
            catch (Throwable e)
            {
                handleError(e, logger);
            }

            return resp.toString();
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ImportReferenceSequencesAction extends MutatingApiAction<ImportFastaSequencesForm>
    {
        @Override
        public Object execute(ImportFastaSequencesForm form, BindException errors) throws Exception
        {
            //resolve files
            List<File> files = new ArrayList<>();
            PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
            File baseDir = StringUtils.trimToNull(form.getPath()) == null ? root.getRootPath() : new File(root.getRootPath(), form.getPath());
            if (!baseDir.exists())
            {
                errors.reject(ERROR_MSG, "Unable to find directory: " + baseDir.getPath());
                return null;
            }

            if (form.getFileNames() == null || form.getFileNames().length == 0)
            {
                errors.reject(ERROR_MSG, "No file names provided");
                return null;
            }

            for (String fn : form.getFileNames())
            {
                File f = new File(baseDir, fn);
                if (f.exists())
                {
                    files.add(f);
                }
                else
                {
                    errors.reject(ERROR_MSG, "Unable to find file: " + f.getPath());
                    return null;
                }
            }

            Map<String, String> params = form.getNtParams();
            Map<String, Object> libraryParams = form.getLibraryParams();
            JSONObject resp = new JSONObject();

            try
            {
                Container target = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
                PipeRoot pr = PipelineService.get().getPipelineRootSetting(target);

                ImportFastaSequencesPipelineJob job = new ImportFastaSequencesPipelineJob(target, getUser(), null, pr, files, params, form.isSplitWhitespace(), form.isCreateLibrary(), libraryParams);
                PipelineService.get().queueJob(job);

                resp.put("jobId", job.getJobGUID());
            }
            catch (PipelineValidationException e)
            {
                throw new IllegalArgumentException(e);
            }

            resp.put("success", true);

            return new ApiSimpleResponse(resp);
        }
    }

    public static class ImportFastaSequencesForm extends AbstractFileUploadAction.FileUploadForm
    {
        private String _jsonData;
        private String _species;
        private String _molType;
        private boolean _splitWhitespace = false;
        private boolean _createLibrary = false;
        private String _libraryName;
        private String _assemblyId;
        private String _libraryDescription;
        private String _path;
        private String[] _fileNames;
        private boolean _skipCacheIndexes = false;
        private boolean _skipTriggers = false;

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

        public String getAssemblyId()
        {
            return _assemblyId;
        }

        public void setAssemblyId(String assemblyId)
        {
            _assemblyId = assemblyId;
        }

        public boolean isSkipCacheIndexes()
        {
            return _skipCacheIndexes;
        }

        public void setSkipCacheIndexes(boolean skipCacheIndexes)
        {
            _skipCacheIndexes = skipCacheIndexes;
        }

        public boolean isSkipTriggers()
        {
            return _skipTriggers;
        }

        public void setSkipTriggers(boolean skipTriggers)
        {
            _skipTriggers = skipTriggers;
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

        public boolean isSplitWhitespace()
        {
            return _splitWhitespace;
        }

        public void setSplitWhitespace(boolean splitWhitespace)
        {
            _splitWhitespace = splitWhitespace;
        }

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }

        public String[] getFileNames()
        {
            return _fileNames;
        }

        public void setFileNames(String[] fileNames)
        {
            _fileNames = fileNames;
        }

        public Map<String, String> getNtParams()
        {
            Map<String, String> params = new HashMap<>();
            if (getSpecies() != null)
            {
                params.put("species", getSpecies());
            }

            if (getMolType() != null)
            {
                params.put("mol_type", getMolType());
            }

            return params;
        }

        public Map<String, Object> getLibraryParams()
        {
            Map<String, Object> libraryParams = new HashMap<>();
            if (isCreateLibrary())
            {
                libraryParams.put("name", getLibraryName());
                libraryParams.put("description", getLibraryDescription());
                libraryParams.put("assemblyId", getAssemblyId());
                libraryParams.put("skipCacheIndexes", isSkipCacheIndexes());
                libraryParams.put("skipTriggers", isSkipTriggers());
            }

            return libraryParams;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ImportOutputFileAction extends AbstractFileUploadAction<ImportOutputFileForm>
    {
        @Override
        protected void setContentType(HttpServletResponse response)
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
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

        @Override
        public String getResponse(ImportOutputFileForm form, Map<String, Pair<File, String>> files)
        {
            JSONObject resp = new JSONObject();
            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();

                    Map<String, Object> params = new CaseInsensitiveHashMap<>();
                    params.put("name", form.getName());
                    params.put("description", form.getDescription());

                    ExpData data = ExperimentService.get().createData(getContainer(), new DataType("Sequence Output"));
                    data.setName(file.getName());
                    data.setDataFileURI(file.toURI());
                    data.save(getUser());
                    params.put("dataid", data.getRowId());
                    params.put("library_id", form.getLibraryId());
                    if (form.getReadset() != null)
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
        private Integer _readset;

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

        public Integer getReadset()
        {
            return _readset;
        }

        public void setReadset(Integer readset)
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

    @RequiresPermission(InsertPermission.class)
    public class ImportTrackAction extends AbstractFileUploadAction<ImportTrackForm>
    {
        @Override
        protected void setContentType(HttpServletResponse response)
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
        }

        protected File getTargetFile(String filename) throws IOException
        {
            if (!PipelineService.get().hasValidPipelineRoot(getContainer()))
                throw new UploadException("Pipeline root must be configured before uploading files", HttpServletResponse.SC_NOT_FOUND);

            try
            {
                File targetDirectory = AssayFileWriter.ensureUploadDirectory(getContainer());
                return AssayFileWriter.findUniqueFileName(filename, targetDirectory);
            }
            catch (ExperimentException e)
            {
                throw new UploadException(e.getMessage(), HttpServletResponse.SC_NOT_FOUND);
            }
        }

        @Override
        public void validateCommand(ImportTrackForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            //ensure has write access in genome container
            if (form.getLibraryId() != null)
            {
                Container genomeFolder = ReferenceGenomeImpl.getFolderForGenome(form.getLibraryId());
                if (genomeFolder != null && !genomeFolder.hasPermission(getUser(), UpdatePermission.class))
                {
                    throw new UnauthorizedException("The current user does not have permission to update genome " + form.getLibraryId() + " which is saved in the folder: " + genomeFolder.getPath());
                }
            }
        }

        @Override
        public String getResponse(ImportTrackForm form, Map<String, Pair<File, String>> files) throws UploadException
        {
            JSONObject resp = new JSONObject();
            if (form.getTrackName() == null || form.getLibraryId() == null)
            {
                throw new UploadException("Must provide the track name and library Id", HttpServletResponse.SC_BAD_REQUEST);
            }

            try
            {
                for (Map.Entry<String, Pair<File, String>> entry : files.entrySet())
                {
                    File file = entry.getValue().getKey();

                    try
                    {
                        Container target = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
                        PipeRoot root = PipelineService.get().getPipelineRootSetting(target);

                        Container genomeFolder = ReferenceGenomeImpl.getFolderForGenome(form.getLibraryId());
                        if (!genomeFolder.hasPermission(getUser(), UpdatePermission.class))
                        {
                            throw new UnauthorizedException("You do not have permission to update genome " + form.getLibraryId() + ", which is saved in the folder: " + genomeFolder.getPath());
                        }

                        //note: permissions on source container should be checked in validate()
                        ImportGenomeTrackPipelineJob job = new ImportGenomeTrackPipelineJob(target, getUser(), null, root, form.getLibraryId(), form.getTrackName(), file, entry.getValue().getValue(), form.getTrackDescription(), form.getDoChrTranslation() == null ? true : form.getDoChrTranslation());
                        PipelineService.get().queueJob(job);

                        resp.put("jobId", job.getJobGUID());
                    }
                    catch (PipelineValidationException e)
                    {
                        throw new IllegalArgumentException(e);
                    }

                    resp.put("success", true);
                }
            }
            catch (Throwable e)
            {
                handleError(e, logger);
            }

            return resp.toString();
        }
    }

    private void handleError(Throwable e, Logger log)
    {
        try (OutputStream out = getViewContext().getResponse().getOutputStream(); OutputStreamWriter writer = new OutputStreamWriter(out, StringUtilsLabKey.DEFAULT_CHARSET))
        {
            log.error(e.getMessage(), e);

            getViewContext().getResponse().reset();
            getViewContext().getResponse().setContentType("text/plain");
            getViewContext().getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
            if (e instanceof OutOfMemoryError)
            {
                writer.write(PageFlowUtil.jsString("Your server ran out of memory when processing this file.  You may want to contact your admin and consider increasing the tomcat heap size."));
            }
            else
            {
                writer.write(PageFlowUtil.jsString(e.getMessage()));
            }

            writer.flush();
        }
        catch (IOException err)
        {
            //ignore
        }
    }

    public static class ImportTrackForm extends AbstractFileUploadAction.FileUploadForm
    {
        private Integer _libraryId;
        private String _trackName;
        private String _trackDescription;
        private Boolean _doChrTranslation;

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

        public Boolean getDoChrTranslation()
        {
            return _doChrTranslation;
        }

        public void setDoChrTranslation(Boolean doChrTranslation)
        {
            _doChrTranslation = doChrTranslation;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ImportChainFileAction extends AbstractFileUploadAction<ImportChainFileForm>
    {
        @Override
        protected void setContentType(HttpServletResponse response)
        {
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
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

        @Override
        public String getResponse(ImportChainFileForm form, Map<String, Pair<File, String>> files)
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

                    try
                    {
                        List<String> messages = new ArrayList<>();
                        File chainFile = new ChainFileValidator().processChainFile(file, form.getGenomeId1(), form.getGenomeId2(), form.getAllowUnknownContig(), messages);
                        SequenceAnalysisManager.get().addChainFile(getContainer(), getUser(), chainFile, form.getGenomeId1(), form.getGenomeId2(), form.getSource(), form.getVersion());
                        if (file.exists())
                        {
                            file.delete();
                        }

                        resp.put("messages", messages);
                        resp.put("success", true);
                    }
                    catch (SAMException e)
                    {
                        throw new UploadException(e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
                    }

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
        private String _source;
        private Double _version;
        private Boolean _allowUnknownContig = false;

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

        public Boolean getAllowUnknownContig()
        {
            return _allowUnknownContig;
        }

        public void setAllowUnknownContig(Boolean allowUnknownContig)
        {
            _allowUnknownContig = allowUnknownContig;
        }

        public String getSource()
        {
            return _source;
        }

        public void setSource(String source)
        {
            _source = source;
        }
    }

    @RequiresPermission(ReadPermission.class)
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

            String filename = form.getFileName();
            if (StringUtils.isEmpty(form.getFileName()))
            {
                filename = "output.fasta";
            }

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
            PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), filename, true);
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
                            if (wholeSequence == null)
                            {
                                errors.reject(ERROR_MSG, "Unable to find sequence for: " + rowId);
                                return;
                            }

                            for (String t : intervalMap.getString(rowId.toString()).split(","))
                            {
                                String[] coordinates = t.split("-");
                                if (coordinates.length != 2)
                                {
                                    errors.reject(ERROR_MSG, "Inproper interval: [" + t + "]");
                                    return;
                                }

                                Integer start = StringUtils.trimToNull(coordinates[0]) == null ? null : ConvertHelper.convert(coordinates[0], Integer.class);
                                if (wholeSequence.length() < start)
                                {
                                    errors.reject(ERROR_MSG, "Start is beyond the length of the sequence.  Length: " + wholeSequence.length());
                                    return;
                                }

                                Integer stop = StringUtils.trimToNull(coordinates[1]) == null ? null : ConvertHelper.convert(coordinates[1], Integer.class);
                                if (wholeSequence.length() < stop)
                                {
                                    errors.reject(ERROR_MSG, "Stop is beyond the length of the sequence.  Length: " + wholeSequence.length());
                                    return;
                                }

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

    @RequiresPermission(AdminPermission.class)
    public class RecreateReferenceLibraryAction extends MutatingApiAction<RecreateReferenceLibraryForm>
    {
        @Override
        public ApiResponse execute(RecreateReferenceLibraryForm form, BindException errors)
        {
            if (form.getLibraryIds() == null || form.getLibraryIds().length == 0)
            {
                errors.reject(ERROR_MSG, "Must provide a list of reference genomes to re-process");
                return null;
            }

            try
            {
                PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
                for (Integer libraryId : form.getLibraryIds())
                {
                    TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_REF_LIBRARIES);
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

                    PipelineService.get().queueJob(ReferenceLibraryPipelineJob.recreate(c, getUser(), root, libraryId, form.isSkipCacheIndexes(), false));
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
        private boolean _skipCacheIndexes = false;

        public int[] getLibraryIds()
        {
            return _libraryIds;
        }

        public void setLibraryIds(int[] libraryIds)
        {
            _libraryIds = libraryIds;
        }

        public boolean isSkipCacheIndexes()
        {
            return _skipCacheIndexes;
        }

        public void setSkipCacheIndexes(boolean skipCacheIndexes)
        {
            _skipCacheIndexes = skipCacheIndexes;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CheckFileStatusForHandlerAction extends ReadOnlyApiAction<CheckFileStatusForm>
    {
        @Override
        public ApiResponse execute(CheckFileStatusForm form, BindException errors)
        {
            Map<String, Object> ret = new HashMap<>();

            if (StringUtils.isEmpty(form.getHandlerClass()))
            {
                errors.reject(ERROR_MSG, "Must provide the file handler");
                return null;
            }

            if (StringUtils.isEmpty(form.getHandlerType()))
            {
                errors.reject(ERROR_MSG, "Must provide the file handler type");
                return null;
            }

            SequenceOutputHandler handler = SequenceAnalysisManager.get().getFileHandler(form.getHandlerClass(), form.getHandlerEnum());
            if (handler == null)
            {
                errors.reject(ERROR_MSG, "Unknown handler class: " + form.getHandlerClass());
                return null;
            }

            JSONArray toolArr = new JSONArray();
            if (handler instanceof ParameterizedOutputHandler)
            {
                List<ToolParameterDescriptor> toolParams = form.getHandlerEnum() == SequenceOutputHandler.TYPE.Readset ? ((ParameterizedOutputHandler<SequenceOutputHandler.SequenceReadsetProcessor>) handler).getParameters() : ((ParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>) handler).getParameters();
                for (ToolParameterDescriptor pd : toolParams)
                {
                    toolArr.put(pd.toJSON());
                }
            }

            JSONObject resourceSettings = getReourceSettingsJson();
            if (resourceSettings != null && resourceSettings.get("parameters") != null)
            {
                for (JSONObject o : resourceSettings.getJSONArray("parameters").toJSONObjectArray())
                {
                    //a little unclean.  the ResourceAllocator will expect a fully qualified name
                    o.put("name", "resourceSettings.resourceSettings." + o.getString("name"));
                    toolArr.put(o);
                }
            }

            JSONObject intermediateFiles = new JSONObject();
            intermediateFiles.put("name", "deleteIntermediateFiles");
            intermediateFiles.put("defaultValue", true);
            intermediateFiles.put("label", "Delete Intermediate Files");
            intermediateFiles.put("description", "Is selected, intermediate files from this job will be deleted automatically");
            intermediateFiles.put("fieldXtype", "checkbox");

            toolArr.put(intermediateFiles);

            ret.put("toolParameters", toolArr);

            ret.put("description", handler.getDescription());
            ret.put("name", handler.getName());
            ret.put("requiresSingleGenome", handler.requiresSingleGenome());

            JSONArray arr = new JSONArray();
            if (form.getOutputFileIds() != null)
            {
                for (int outputFileId : form.getOutputFileIds())
                {
                    arr.put(getDataJson(handler, outputFileId));
                }
            }

            JSONObject outputFiles = new JSONObject();
            if (form.getOutputFileIds() != null)
            {
                TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
                for (int outputFileId : form.getOutputFileIds())
                {
                    SequenceOutputFile so = SequenceOutputFile.getForId(outputFileId);
                    outputFiles.put(so.getRowid().toString(), so.toJSON());

                    Map rowMap = new TableSelector(ti, PageFlowUtil.set("dataId", "library_id"), new SimpleFilter(FieldKey.fromString("rowid"), outputFileId), null).getMap();
                    if (rowMap == null || rowMap.get("dataid") == null)
                    {
                        JSONObject o = new JSONObject();
                        o.put("outputFileId", outputFileId);
                        o.put("fileName", (String) null);
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

                    JSONObject o = getDataJson(handler, outputFileId);
                    o.put("libraryId", libraryId);
                    arr.put(o);
                }

                ret.put("outputFiles", outputFiles);
            }

            if (form.getReadsetIds() != null)
            {
                for (Integer readsetId : form.getReadsetIds())
                {
                    SequenceReadsetImpl rs = SequenceAnalysisServiceImpl.get().getReadset(readsetId, getUser());
                    if (rs == null)
                    {
                        errors.reject(ERROR_MSG, "Unknown readset: " + readsetId);
                        return null;
                    }

                    arr.put(getDataJsonForReadset(handler, rs));
                }
            }

            ret.put("files", arr);

            int[] pkArr = form.getOutputFileIds() != null ? form.getOutputFileIds() : form.getReadsetIds();
            ActionURL url = handler.getButtonSuccessUrl(getContainer(), getUser(), Arrays.asList(ArrayUtils.toObject(pkArr)));
            if (url != null)
            {
                Map<String, Object> urlMap = new HashMap<>();
                urlMap.put("controller", url.getController());
                urlMap.put("action", url.getAction());
                urlMap.put("urlParams", url.getParameterMap());

                ret.put("successUrl", urlMap);
            }
            ret.put("jsHandler", handler.getButtonJSHandler());
            ret.put("useWorkbooks", handler.useWorkbooks());

            return new ApiSimpleResponse(ret);
        }

        private JSONObject getDataJson(SequenceOutputHandler handler, Integer outputFileId)
        {
            JSONObject o = new JSONObject();
            SequenceOutputFile outputFile = SequenceOutputFile.getForId(outputFileId);
            if (outputFile == null)
            {
                _log.error("getDataJson was provided with an invalid outputFileId: " + outputFileId, new Exception());

                o = new JSONObject();
                o.put("outputFileId", outputFileId);
                o.put("fileExists", false);
                o.put("error", true);
                return o;
            }

            o.put("dataId", outputFile.getDataId());
            o.put("outputFileId", outputFileId);

            ExpData d = outputFile.getDataId() == 0 ? null : ExperimentService.get().getExpData(outputFile.getDataId());
            if (d == null || d.getFile() == null || !d.getFile().exists())
            {
                o.put("fileExists", false);
                o.put("error", true);
                return o;
            }

            o.put("fileName", d.getFile().getName());
            o.put("fileExists", true);
            o.put("extension", FileUtil.getExtension(d.getFile()));

            boolean canProcess = handler.canProcess(outputFile);
            o.put("canProcess", canProcess);

            return o;
        }

        private JSONObject getDataJsonForReadset(SequenceOutputHandler handler, Readset rs)
        {
            JSONObject o = new JSONObject();
            o.put("readsetId", rs.getReadsetId());

            if (rs.getReadData() == null || rs.getReadData().isEmpty())
            {
                o.put("totalReadData", 0);
                o.put("error", true);
                return o;
            }

            o.put("totalReadData", rs.getReadData().size());
            for (ReadData rd : rs.getReadData())
            {
                if (rd.isArchived() && !handler.supportsSraArchivedData())
                {
                    o.put("fileExists", false);
                    o.put("isArchived", true);
                    o.put("error", true);
                    return o;
                }

                ExpData d = rd.getFileId1() == 0 ? null : ExperimentService.get().getExpData(rd.getFileId1());
                if (d == null || d.getFile() == null || !d.getFile().exists())
                {
                    if (!handler.supportsSraArchivedData())
                    {
                        o.put("fileExists", false);
                        o.put("error", true);
                        return o;
                    }
                }

                if (rd.getFileId2() != null)
                {
                    d = rd.getFileId2() == 0 ? null : ExperimentService.get().getExpData(rd.getFileId2());
                    if (d == null || d.getFile() == null || !d.getFile().exists())
                    {
                        if (!handler.supportsSraArchivedData())
                        {
                            o.put("fileExists", false);
                            o.put("error", true);
                            return o;
                        }
                    }
                }
            }

            o.put("fileExists", true);
            o.put("canProcess", true);

            return o;
        }
    }

    private JSONObject getReourceSettingsJson()
    {
        JSONArray resourceSettings = new JSONArray();
        for (JobResourceSettings settings : SequencePipelineServiceImpl.get().getResourceSettings())
        {
            if (settings.isAvailable(getContainer()))
            {
                for (ToolParameterDescriptor pd : settings.getParams())
                {
                    resourceSettings.put(pd.toJSON());
                }
            }
        }

        if (resourceSettings.length() > 0)
        {
            JSONObject json = new JSONObject();
            json.put("name", "resourceSettings");
            json.put("description", "The following options are available to adjust the resources given to this job when it executes.  The are often chosen automatically, so you can generally leave these blank if you are unsure.");
            json.put("parameters", resourceSettings);

            return json;
        }

        return null;
    }

    public static class CheckFileStatusForm
    {
        private String _handlerType = SequenceOutputHandler.TYPE.OutputFile.name();  //default to this for backward compatibility
        private String _handlerClass;
        private int[] _outputFileIds;
        private int[] _readsetIds;

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

        public int[] getReadsetIds()
        {
            return _readsetIds;
        }

        public void setReadsetIds(int[] readsetIds)
        {
            _readsetIds = readsetIds;
        }

        public String getHandlerType()
        {
            return _handlerType;
        }

        public void setHandlerType(String handlerType)
        {
            _handlerType = handlerType;
        }

        public SequenceOutputHandler.TYPE getHandlerEnum()
        {
            return _handlerType == null ? null : SequenceOutputHandler.TYPE.valueOf(_handlerType);
        }
    }

    @RequiresPermission(ReadPermission.class)
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

            Set<File> files = new HashSet<>();
            FileType bamFileType = new FileType("bam");
            FileType cramFileType = new FileType("cram");
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
                if (cramFileType.isType(data.getFile()))
                {
                    File index = new File(data.getFile() + ".crai");
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

                    File dict = new File(data.getFile().getParentFile(), FileUtil.getBaseName(data.getFile()) + ".dict");
                    if (dict.exists())
                    {
                        files.add(dict);
                    }
                    else
                    {
                        //Try alternate name scheme
                        dict = new File(data.getFile() + ".dict");
                        if (dict.exists())
                        {
                            files.add(dict);
                        }
                    }
                }
            }

            PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), filename, true);

            try (ZipOutputStream zOut = new ZipOutputStream(response.getOutputStream()))
            {
                Set<String> fileNames = new CaseInsensitiveHashSet();
                for (File f : files)
                {
                    if (!f.exists())
                    {
                        throw new NotFoundException("File " + f.getPath() + " does not exist");
                    }

                    String name = f.getName();
                    if (fileNames.contains(name))
                    {
                        int i = 1;
                        String newName = name;
                        while (fileNames.contains(newName))
                        {
                            newName = FileUtil.getBaseName(name) + "." + i + "." + FileUtil.getExtension(name);
                            i++;
                        }

                        name = newName;
                    }
                    fileNames.add(name);

                    ZipEntry fileEntry = new ZipEntry(name);
                    zOut.putNextEntry(fileEntry);

                    try (FileInputStream in = new FileInputStream(f))
                    {
                        IOUtils.copy(in, zOut);
                        zOut.closeEntry();
                    }
                    catch (Exception e)
                    {
                        // insert the stack trace into the zip file
                        ZipEntry errorEntry = new ZipEntry("error.log");
                        zOut.putNextEntry(errorEntry);

                        try (PrintWriter ps = PrintWriters.getPrintWriter(new PrintStream(zOut, true)))
                        {
                            ps.println("Failed to complete export of the file: " + f.getPath());
                            _log.error(e.getMessage(), e);
                        }
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

    @RequiresPermission(ReadPermission.class)
    public class GetDataItemsAction extends ReadOnlyApiAction<GetDataItemsForm>
    {
        @Override
        public ApiResponse execute(GetDataItemsForm form, BindException errors)
        {
            Map<String, List<JSONObject>> results = new HashMap<>();

            for (SequenceDataProvider.SequenceNavItemCategory category : SequenceDataProvider.SequenceNavItemCategory.values())
            {
                List<JSONObject> json = results.get(category.name());
                if (json == null)
                {
                    json = new ArrayList<>();
                }

                for (NavItem item : SequenceAnalysisService.get().getNavItems(getContainer(), getUser(), category))
                {
                    ensureModuleActive(item);

                    if (form.isIncludeAll() || item.isVisible(getContainer(), getUser()))
                    {
                        json.add(item.toJSON(getContainer(), getUser()));
                    }
                }

                results.put(category.name(), json);
            }

            Map<String, Object> ret = new HashMap<>();
            ret.put("success", true);
            ret.put("results", results);

            return new ApiSimpleResponse(ret);
        }

        private void ensureModuleActive(NavItem item)
        {
            if (getContainer().equals(ContainerManager.getSharedContainer()))
            {
                Module m = item.getDataProvider().getOwningModule();
                if (m != null)
                {
                    Set<Module> active = getContainer().getActiveModules();
                    if (!active.contains(m))
                    {
                        Set<Module> newActive = new HashSet<Module>();
                        newActive.addAll(active);
                        newActive.add(m);

                        _log.info("Enabling module " + m.getName() + " in shared container since getDataItems was called");

                        getContainer().setActiveModules(newActive);
                    }
                }

            }
        }
    }

    public static class GetDataItemsForm
    {
        private boolean _includeAll = false;

        public boolean isIncludeAll()
        {
            return _includeAll;
        }

        public void setIncludeAll(boolean includeAll)
        {
            _includeAll = includeAll;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class RunSequenceHandlerAction extends MutatingApiAction<RunSequenceHandlerForm>
    {
        @Override
        public ApiResponse execute(RunSequenceHandlerForm form, BindException errors) throws Exception
        {
            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !pr.isValid())
                throw new ApiUsageException("Pipe root invalid or not set for container: " + getContainer().getPath());

            if (StringUtils.isEmpty(form.getHandlerClass()))
            {
                errors.reject(ERROR_MSG, "Must provide the file handler");
                return null;
            }

            SequenceOutputHandler<?> handler = SequenceAnalysisManager.get().getFileHandler(form.getHandlerClass(), form.getHandlerEnum());
            if (handler == null)
            {
                errors.reject(ERROR_MSG, "Unknown handler: " + form.getHandlerClass());
                return null;
            }

            List<SequenceOutputFile> outputFiles = new ArrayList<>();
            if (form.getOutputFileIds() != null)
            {
                for (int outputFileId : form.getOutputFileIds())
                {
                    SequenceOutputFile o = SequenceOutputFile.getForId(outputFileId);
                    if (o == null || o.getFile() == null)
                    {
                        errors.reject(ERROR_MSG, "Unable to find file: " + outputFileId);
                        return null;
                    }

                    if (!o.getFile().exists())
                    {
                        errors.reject(ERROR_MSG, "Unable to find file: " + o.getFile().getPath());
                        return null;
                    }

                    outputFiles.add(o);
                }

                if (outputFiles.isEmpty())
                {
                    errors.reject(ERROR_MSG, "No valid outputfiles found");
                    return null;
                }
            }

            List<String> errorMsgs = handler.validateParameters(outputFiles, new JSONObject(form.getParams()));
            if (errorMsgs != null && !errorMsgs.isEmpty())
            {
                errorMsgs.forEach(e -> errors.reject(ERROR_MSG, e));
                return null;
            }

            List<SequenceReadsetImpl> readsets = new ArrayList<>();
            if (form.getReadsetIds() != null)
            {
                for (int readsetId : form.getReadsetIds())
                {
                    SequenceReadsetImpl readset = SequenceAnalysisServiceImpl.get().getReadset(readsetId, getUser());
                    if (readset == null)
                    {
                        errors.reject(ERROR_MSG, "Unable to find readset: " + readsetId);
                        return null;
                    }

                    if (readset.getReadData().isEmpty())
                    {
                        errors.reject(ERROR_MSG, "No files have been imported for the readset: " + readsetId);
                        return null;
                    }

                    readsets.add(readset);
                }

                if (readsets.isEmpty())
                {
                    errors.reject(ERROR_MSG, "No readsets found");
                    return null;
                }
            }


            if (outputFiles.isEmpty() && readsets.isEmpty())
            {
                errors.reject(ERROR_MSG, "Must provide either output files or readsets");
                return null;
            }

            try
            {
                List<String> guids = new ArrayList<>();
                boolean doSplit = form.isSplitJobsProvided() ? form.getDoSplitJobs() : handler.doSplitJobs();
                if (doSplit)
                {
                    Map<Container, PipeRoot> containerToPipeRootMap = new HashMap<>();
                    for (SequenceOutputFile o : outputFiles)
                    {
                        JSONObject json = new JSONObject(form.getParams());
                        String jobName = form.getJobName();
                        if (StringUtils.isEmpty(jobName))
                        {
                            jobName = handler.getName().replaceAll(" ", "_") + "_" + FileUtil.getTimestamp();
                        }

                        jobName = jobName + "." + o.getRowid();

                        Container targetContainer = form.getUseOutputFileContainer() ? ContainerManager.getForId(o.getContainer()) : getContainer();
                        PipeRoot pr1 = getPipeRoot(targetContainer, containerToPipeRootMap);
                        PipelineJob job = createOutputJob(form, targetContainer, jobName, pr1, handler, Arrays.asList(o), json);
                        PipelineService.get().queueJob(job);
                        guids.add(job.getJobGUID());
                    }

                    for (SequenceReadsetImpl o : readsets)
                    {
                        JSONObject json = new JSONObject(form.getParams());
                        String jobName = form.getJobName();
                        if (StringUtils.isEmpty(jobName))
                        {
                            jobName = handler.getName().replaceAll(" ", "_") + "_" + FileUtil.getTimestamp();
                        }

                        jobName = jobName + "." + o.getReadsetId();

                        Container targetContainer = form.getUseOutputFileContainer() ? ContainerManager.getForId(o.getContainer()) : getContainer();
                        PipeRoot pr1 = getPipeRoot(targetContainer, containerToPipeRootMap);
                        SequenceReadsetHandlerJob job = new SequenceReadsetHandlerJob(targetContainer, getUser(), jobName, pr1, handler, Arrays.asList(o), json);
                        PipelineService.get().queueJob(job);
                        guids.add(job.getJobGUID());
                    }
                }
                else
                {
                    if (!outputFiles.isEmpty())
                    {
                        JSONObject json = new JSONObject(form.getParams());
                        PipelineJob job = createOutputJob(form, getContainer(), (StringUtils.isEmpty(form.getJobName()) ? handler.getName().replaceAll(" ", "_") + "_" + FileUtil.getTimestamp() : form.getJobName()), pr, handler, outputFiles, json);
                        PipelineService.get().queueJob(job);
                        guids.add(job.getJobGUID());
                    }

                    if (!readsets.isEmpty())
                    {
                        JSONObject json = new JSONObject(form.getParams());
                        SequenceReadsetHandlerJob job = new SequenceReadsetHandlerJob(getContainer(), getUser(), (StringUtils.isEmpty(form.getJobName()) ? handler.getName().replaceAll(" ", "_") + "_" + FileUtil.getTimestamp() : form.getJobName()), pr, handler, readsets, json);
                        PipelineService.get().queueJob(job);
                        guids.add(job.getJobGUID());
                    }
                }

                Map<String, Object> ret = new HashMap<>();
                ret.put("jobGUIDs", guids);
                ret.put("success", true);

                return new ApiSimpleResponse(ret);
            }
            catch (JSONException e)
            {
                errors.reject(ERROR_MSG, "Unable to parse JSON params: " + e.getMessage());
                return null;
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                _log.error("Unable to submit sequence handler", e);
                return null;
            }
        }

        protected PipelineJob createOutputJob(RunSequenceHandlerForm form, Container targetContainer, String jobName, PipeRoot pr1, SequenceOutputHandler<?> handler, List<SequenceOutputFile> inputs, JSONObject json) throws IOException, PipelineJobException
        {
            validateGenomes(inputs, handler);
            return new SequenceOutputHandlerJob(targetContainer, getUser(), jobName, pr1, handler, inputs, json);
        }

        protected void validateGenomes(List<SequenceOutputFile> inputs, SequenceOutputHandler<?> handler) throws IllegalArgumentException
        {
            Set<Integer> genomes = new HashSet<>();
            inputs.forEach(x -> {
                if (x.getLibrary_id() == null && (handler.requiresGenome() || handler.requiresSingleGenome()))
                {
                    throw new IllegalArgumentException("Input missing genome: " + x.getRowid());
                }

                genomes.add(x.getLibrary_id());
            });

            if (genomes.size() > 1 && handler.requiresSingleGenome())
            {
                throw new IllegalArgumentException("All inputs must use the same base genome");
            }
        }

        private PipeRoot getPipeRoot(Container targetContainer, Map<Container, PipeRoot> containerToPipeRootMap)
        {
            if (!containerToPipeRootMap.containsKey(targetContainer))
            {
                PipeRoot pr = PipelineService.get().getPipelineRootSetting(targetContainer);
                if (pr == null || !pr.isValid())
                    throw new ApiUsageException("Pipe root invalid or not set for container: " + targetContainer.getPath());

                containerToPipeRootMap.put(targetContainer, pr);
            }

            return containerToPipeRootMap.get(targetContainer);
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class RunVariantProcessingAction extends RunSequenceHandlerAction
    {
        @Override
        protected PipelineJob createOutputJob(RunSequenceHandlerForm form, Container targetContainer, String jobName, PipeRoot pr1, SequenceOutputHandler<?> handler, List<SequenceOutputFile> inputs, JSONObject json) throws PipelineJobException, IOException
        {
            String method = json.getString("scatterGatherMethod");
            validateGenomes(inputs, handler);

            VariantProcessingStep.ScatterGatherMethod scatterMethod;
            try
            {
                scatterMethod = VariantProcessingStep.ScatterGatherMethod.valueOf(method);
            }
            catch (IllegalArgumentException e)
            {
                throw new IllegalArgumentException("Unknown scatter method: [" + method + "]", e);
            }

            return new VariantProcessingJob(targetContainer, getUser(), jobName, pr1, handler, inputs, json, scatterMethod);
        }
    }

    public static class RunSequenceHandlerForm extends CheckFileStatusForm
    {
        private String _jobName;
        private String _params;
        private Boolean _doSplitJobs = null;
        private Boolean _useOutputFileContainer = false;
        private Boolean _scatterGather = null;

        public String getJobName()
        {
            return _jobName;
        }

        public void setJobName(String jobName)
        {
            _jobName = jobName;
        }

        public String getParams()
        {
            return _params;
        }

        public void setParams(String params)
        {
            _params = params;
        }

        public Boolean getDoSplitJobs()
        {
            return _doSplitJobs;
        }

        public boolean isSplitJobsProvided()
        {
            return _doSplitJobs != null;
        }

        public void setDoSplitJobs(Boolean doSplitJobs)
        {
            _doSplitJobs = doSplitJobs;
        }

        public Boolean getScatterGather()
        {
            return _scatterGather;
        }

        public void setScatterGather(Boolean scatterGather)
        {
            _scatterGather = scatterGather;
        }

        public boolean isScatterGather()
        {
            return _scatterGather != null && _scatterGather;
        }

        public Boolean getUseOutputFileContainer()
        {
            return _useOutputFileContainer == null ? false : _useOutputFileContainer;
        }

        public void setUseOutputFileContainer(Boolean useOutputFileContainer)
        {
            _useOutputFileContainer = useOutputFileContainer;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ImportOutputFilesAction extends MutatingApiAction<ImportOutputFilesForm>
    {
        @Override
        public ApiResponse execute(ImportOutputFilesForm form, BindException errors) throws Exception
        {
            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !pr.isValid())
                throw new NotFoundException();

            File dirData = pr.getRootPath();
            if (form.getPath() != null)
            {
                dirData = pr.resolvePath(form.getPath());
                if (dirData == null || !NetworkDrive.exists(dirData))
                    throw new NotFoundException("Could not resolve path: " + form.getPath());
            }

            if (form.getRecords() == null)
            {
                errors.reject(ERROR_MSG, "No files to save");
                return null;
            }

            AssayFileWriter writer = new AssayFileWriter();
            File targetDirectory = AssayFileWriter.ensureUploadDirectory(getContainer(), "sequenceOutputs");
            if (!targetDirectory.exists())
            {
                targetDirectory.mkdirs();
            }

            if (form.getRecords() != null)
            {
                JSONArray arr = new JSONArray(form.getRecords());

                Map<File, Map<String, Object>> toCreate = new HashMap<>();
                for (JSONObject o : arr.toJSONObjectArray())
                {
                    File file = new File(dirData, o.getString("fileName"));
                    if (!file.exists())
                    {
                        errors.reject(ERROR_MSG, "Unknown file: " + file.getPath());
                        return null;
                    }

                    Map<String, Object> params = new CaseInsensitiveHashMap<>();
                    if (o.get("name") == null)
                    {
                        errors.reject(ERROR_MSG, "Missing name for file: " + file.getName());
                        return null;
                    }

                    if (o.get("libraryId") == null)
                    {
                        errors.reject(ERROR_MSG, "Missing genome Id for file: " + file.getName());
                        return null;
                    }

                    params.put("name", o.getString("name"));
                    params.put("description", o.getString("description"));

                    params.put("library_id", o.getInt("libraryId"));
                    if (StringUtils.trimToNull(o.getString("readset")) != null)
                        params.put("readset", o.getInt("readset"));
                    params.put("category", o.getString("category"));

                    params.put("container", getContainer().getId());
                    params.put("created", new Date());
                    params.put("createdby", getUser().getUserId());
                    params.put("modified", new Date());
                    params.put("modifiedby", getUser().getUserId());

                    toCreate.put(file, params);
                }

                for (File file : toCreate.keySet())
                {
                    File target = writer.findUniqueFileName(file.getName(), targetDirectory);
                    FileUtils.moveFile(file, target);

                    ExpData data = ExperimentService.get().createData(getContainer(), new DataType("Sequence Output"));
                    data.setName(file.getName());
                    data.setDataFileURI(target.toURI());
                    data.save(getUser());

                    Map<String, Object> params = toCreate.get(file);
                    params.put("dataid", data.getRowId());

                    //check for index, also move
                    List<String> associatedFiles = SequenceAnalysisMaintenanceTask.getAssociatedFiles(file, false);
                    for (String indexName : associatedFiles)
                    {
                        File idx = new File(file.getParent(), indexName);
                        if (idx.exists())
                        {
                            String idxTargetName;
                            //if the original file was not renamed on move
                            if (target.getName().equals(file.getName()))
                            {
                                idxTargetName = indexName;
                            }
                            //if it was renamed, match against the new name
                            else
                            {
                                if (indexName.contains(file.getName()))
                                {
                                    String suffix = indexName.replaceFirst(file.getName(), "");
                                    idxTargetName = target.getName() + suffix;
                                }
                                else
                                {
                                    _log.error("unexpected name for index file: " + indexName + " for parent: " + target.getName());
                                    continue;
                                }
                            }

                            _log.info("moving associated file: " + idx.getPath() + ", to: " + idxTargetName);
                            File idxTarget = new File(targetDirectory, idxTargetName);
                            if (idxTarget.exists())
                            {
                                _log.error("target already exists, skipping: " + idxTargetName);
                            }
                            else
                            {
                                FileUtils.moveFile(idx, idxTarget);

                                ExpData idxData = ExperimentService.get().createData(getContainer(), new DataType("Sequence Output"));
                                idxData.setName(idxTarget.getName());
                                idxData.setDataFileURI(idxTarget.toURI());
                                idxData.save(getUser());
                            }
                        }
                    }

                    Table.insert(getUser(), SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), params);
                }
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class ImportOutputFilesForm
    {
        private String[] _fileNames;
        private String _path;
        private String _records;

        public String[] getFileNames()
        {
            return _fileNames;
        }

        public void setFileNames(String[] fileNames)
        {
            _fileNames = fileNames;
        }

        public String getPath()
        {
            return _path;
        }

        public void setPath(String path)
        {
            _path = path;
        }

        public String getRecords()
        {
            return _records;
        }

        public void setRecords(String records)
        {
            _records = records;
        }
    }

    public static class ImportTracksForm extends ImportOutputFilesForm
    {
        private Boolean _doChrTranslation = false;

        public Boolean getDoChrTranslation()
        {
            return _doChrTranslation;
        }

        public void setDoChrTranslation(Boolean doChrTranslation)
        {
            _doChrTranslation = doChrTranslation;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetAvailableHandlersAction extends ReadOnlyApiAction<GetAvailableHandlersForm>
    {
        @Override
        public ApiResponse execute(GetAvailableHandlersForm form, BindException errors) throws Exception
        {
            Map<String, Object> ret = new HashMap<>();
            Set<JSONObject> availableHandlers = new HashSet<>();
            List<Integer> outputFileIds = new ArrayList<>();
            if (form.getOutputFileIds() == null || form.getOutputFileIds().length == 0)
            {
                errors.reject(ERROR_MSG, "No output files provided");
                return null;
            }

            for (int i : form.getOutputFileIds())
            {
                outputFileIds.add(i);
            }
            List<SequenceOutputFile> outputFiles = new TableSelector(SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES), new SimpleFilter(FieldKey.fromString("rowid"), outputFileIds, CompareType.IN), null).getArrayList(SequenceOutputFile.class);

            //test permissions
            List<JSONObject> outputFileJson = new ArrayList<>();
            for (SequenceOutputFile o : outputFiles)
            {
                Container c = ContainerManager.getForId(o.getContainer());
                if (c == null || !c.hasPermission(getUser(), ReadPermission.class))
                {
                    throw new UnauthorizedException("You do not have permission to view all of the selected files");
                }

                JSONObject j = new JSONObject();
                File f = o.getFile();
                j.put("name", o.getName());
                j.put("libraryId", o.getLibrary_id());
                j.put("container", o.getContainer());

                if (f != null)
                {
                    j.put("fileName", f.getName());
                }
                outputFileJson.add(j);
            }

            List<JSONObject> partialHandlers = new ArrayList<>();
            for (SequenceOutputHandler handler : SequenceAnalysisServiceImpl.get().getFileHandlers(getContainer(), form.getHandlerEnum()))
            {
                boolean available = true;
                JSONObject json = new JSONObject();
                json.put("name", handler.getName());
                json.put("description", handler.getDescription());
                json.put("handlerClass", handler.getClass().getName());

                List<Integer> availableIds = new ArrayList<>();
                for (SequenceOutputFile o : outputFiles)
                {
                    if (handler.isVisible() && handler.canProcess(o))
                    {
                        availableIds.add(o.getRowid());
                    }
                    else
                    {
                        available = false;
                    }
                }

                if (available)
                {
                    ActionURL url = handler.getButtonSuccessUrl(getContainer(), getUser(), Arrays.asList(ArrayUtils.toObject(form.getOutputFileIds())));
                    if (url != null)
                    {
                        Map<String, Object> urlMap = new HashMap<>();
                        urlMap.put("controller", url.getController());
                        urlMap.put("action", url.getAction());
                        urlMap.put("urlParams", url.getParameterMap());

                        json.put("successUrl", urlMap);
                    }
                    json.put("jsHandler", handler.getButtonJSHandler());
                    json.put("useWorkbooks", handler.useWorkbooks());

                    availableHandlers.add(json);
                }
                else if (!availableIds.isEmpty())
                {
                    json.put("files", availableIds);
                    partialHandlers.add(json);
                }
            }

            ret.put("handlers", availableHandlers);
            ret.put("partialHandlers", partialHandlers);
            ret.put("outputFiles", outputFileJson);

            return new ApiSimpleResponse(ret);
        }
    }

    public static class GetAvailableHandlersForm
    {
        private String _handlerType;
        int[] _outputFileIds;

        public int[] getOutputFileIds()
        {
            return _outputFileIds;
        }

        public void setOutputFileIds(int[] outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }

        public String getHandlerType()
        {
            return _handlerType;
        }

        public void setHandlerType(String handlerType)
        {
            _handlerType = handlerType;
        }

        public SequenceOutputHandler.TYPE getHandlerEnum()
        {
            return _handlerType == null ? null : SequenceOutputHandler.TYPE.valueOf(_handlerType);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CompareFastaSequencesAction extends ReadOnlyApiAction<CompareFastaSequencesForm>
    {
        @Override
        public ApiResponse execute(CompareFastaSequencesForm form, BindException errors) throws Exception
        {
            Map<String, Object> ret = new HashMap<>();

            if (StringUtils.isEmpty(form.getFasta()))
            {
                errors.reject(ERROR_MSG, "Must provide the FASTA sequences");
                return null;
            }

            List<JSONObject> allHits = new ArrayList<>();

            List<RefNtSequenceModel> allowedReferences = null;
            TableInfo ti = QueryService.get().getUserSchema(getUser(), getContainer(), SequenceAnalysisSchema.SCHEMA_NAME).getTable(SequenceAnalysisSchema.TABLE_REF_NT_SEQUENCES);
            SimpleFilter filter = new SimpleFilter();
            if (!StringUtils.isEmpty(form.getCategory()))
            {
                filter.addCondition(FieldKey.fromString("category"), form.getCategory(), CompareType.EQUAL);
            }

            if (!StringUtils.isEmpty(form.getSpecies()))
            {
                filter.addCondition(FieldKey.fromString("species"), form.getSpecies(), CompareType.EQUAL);
            }

            if (!form.getIncludeDisabled())
            {
                filter.addCondition(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK);
            }

            if (!filter.isEmpty())
            {
                allowedReferences = new TableSelector(ti, filter, null).getArrayList(RefNtSequenceModel.class);
            }

            try (InputStream is = IOUtils.toInputStream(form.getFasta(), StringUtilsLabKey.DEFAULT_CHARSET))
            {
                FastaReader<DNASequence, NucleotideCompound> fastaReader = new FastaReader<>(is, new GenericFastaHeaderParser<>(), new DNASequenceCreator(AmbiguityDNACompoundSet.getDNACompoundSet()));
                LinkedHashMap<String, DNASequence> fastaData = fastaReader.process();

                for (String fastaHeader : fastaData.keySet())
                {
                    List<JSONObject> hits = new ArrayList<>();
                    Set<String> hitNames = new HashSet<>();

                    SimpleFilter filter2 = new SimpleFilter(FieldKey.fromString("name"), fastaHeader);
                    if (!form.getIncludeDisabled())
                    {
                        filter2.addCondition(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK);
                    }

                    List<RefNtSequenceModel> nameMatches = new TableSelector(ti, filter2, null).getArrayList(RefNtSequenceModel.class);
                    for (RefNtSequenceModel m : nameMatches)
                    {
                        if (hitNames.contains(m.getName()))
                        {
                            continue;
                        }

                        if (m.getSeqLength() > 1000000)
                        {
                            continue;
                        }

                        SequenceMatch sm = SequenceMatch.checkReference(fastaHeader, fastaData.get(fastaHeader), m);
                        if (sm != null)
                        {
                            hits.add(sm.toJSON());
                            hitNames.add(sm._refName);
                        }
                    }

                    if (allowedReferences != null)
                    {
                        for (RefNtSequenceModel m : allowedReferences)
                        {
                            if (hitNames.contains(m.getName()))
                            {
                                continue;
                            }

                            if (m.getSeqLength() > 1000000)
                            {
                                continue;
                            }

                            SequenceMatch sm = SequenceMatch.checkReference(fastaHeader, fastaData.get(fastaHeader), m);
                            if (sm != null)
                            {
                                hits.add(sm.toJSON());
                                hitNames.add(sm._refName);
                            }
                        }
                    }

                    JSONObject o = new JSONObject();
                    o.put("hits", hits);
                    o.put("name", fastaHeader);
                    o.put("sequence", fastaData.get(fastaHeader).getSequenceAsString());

                    allHits.add(o);
                }
            }

            ret.put("hits", allHits);
            ret.put("success", true);

            return new ApiSimpleResponse(ret);
        }
    }

    public static class SequenceMatch
    {
        String _name;
        String _refName;
        int _refId;
        int _fastaLength;
        int _refLength;
        boolean _sequencesMatch;
        boolean _fastaSequenceIsSubsetOfReference;
        boolean _referenceSequenceIsSubsetOfFasta;
        boolean _isReverseComplement;

        private SequenceMatch()
        {

        }

        public static SequenceMatch checkReference(String name, DNASequence fastaSequence, RefNtSequenceModel match)
        {
            String seq = fastaSequence.getSequenceAsString();
            seq = (seq == null) ? "" : seq.toLowerCase();

            String refSeq = match.getSequence();
            refSeq = (refSeq == null) ? "" : refSeq.toLowerCase();
            if (refSeq.length() > 10000)
                match.clearCachedSequence();

            SequenceMatch ret = doCheck(name, seq, refSeq, match, fastaSequence, false);
            if (ret != null || seq.isEmpty())
            {
                return ret;
            }

            //if there's no forward match, try RC
            return doCheck(name, fastaSequence.getReverseComplement().getSequenceAsString().toLowerCase(), refSeq, match, fastaSequence, true);
        }

        private static SequenceMatch doCheck(String name, String seq, String refSeq, RefNtSequenceModel match, DNASequence fastaSequence, boolean isRC)
        {
            boolean sequencesMatch = refSeq.length() > 0 && seq.length() > 0 && seq.equals(refSeq);
            boolean fastaSequenceIsSubsetOfReference = refSeq.length() > 0 && seq.length() > 0 && refSeq.contains(seq);
            boolean referenceSequenceIsSubsetOfFasta = refSeq.length() > 0 && seq.length() > 0 && seq.contains(refSeq);

            if (name.equals(match.getName()) || sequencesMatch || fastaSequenceIsSubsetOfReference || referenceSequenceIsSubsetOfFasta)
            {
                SequenceMatch r = new SequenceMatch();
                r._name = name;
                r._refName = match.getName();
                r._refId = match.getRowid();
                r._sequencesMatch = sequencesMatch;
                r._fastaSequenceIsSubsetOfReference = fastaSequenceIsSubsetOfReference;
                r._referenceSequenceIsSubsetOfFasta = referenceSequenceIsSubsetOfFasta;
                r._fastaLength = fastaSequence.getLength();
                r._refLength = refSeq.length();
                r._isReverseComplement = isRC;

                return r;
            }
            else
            {
                return null;
            }
        }

        public JSONObject toJSON()
        {
            JSONObject ret = new JSONObject();
            ret.put("name", _name);
            ret.put("refName", _refName);
            ret.put("refId", _refId);
            ret.put("sequencesMatch", _sequencesMatch);
            ret.put("fastaSequenceIsSubsetOfReference", _fastaSequenceIsSubsetOfReference);
            ret.put("referenceSequenceIsSubsetOfFasta", _referenceSequenceIsSubsetOfFasta);
            ret.put("fastaLength", _fastaLength);
            ret.put("refLength", _refLength);
            ret.put("isReverseComplement", _isReverseComplement);

            return ret;
        }
    }

    public static class CompareFastaSequencesForm
    {
        String _fasta;
        String _category;
        String _species;
        Boolean _includeDisabled;

        public String getFasta()
        {
            return _fasta;
        }

        public void setFasta(String fasta)
        {
            _fasta = fasta;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getSpecies()
        {
            return _species;
        }

        public void setSpecies(String species)
        {
            _species = species;
        }

        public boolean getIncludeDisabled()
        {
            return _includeDisabled != null && _includeDisabled;
        }

        public void setIncludeDisabled(Boolean includeDisabled)
        {
            _includeDisabled = includeDisabled;
        }
    }

//    @IgnoresTermsOfUse
//    @RequiresNoPermission
//    public class ExternalLinkAction extends ExportAction<ExternalLinkForm>
//    {
//        public void export(ExternalLinkForm form, HttpServletResponse response, BindException errors) throws Exception
//        {
//            if (form.getRowId() == 0 || form.getContainer() == null)
//            {
//                errors.reject(ERROR_MSG, "Must provide the rowid and container");
//                return;
//            }
//
//            try
//            {
//                SequenceOutputFile so = SequenceOutputFile.getForId(form.getRowId());
//                if (so == null)
//                {
//                    errors.reject(ERROR_MSG, "Sequence output not found");
//                    return;
//                }
//
//                if (!form.getContainer().equals(so.getContainer()))
//                {
//                    errors.reject(ERROR_MSG, "Sequence output not found in this folder");
//                    return;
//                }
//
//                File f = so.getExpData().getFile();
//                if (f == null || !f.exists())
//                {
//                    errors.reject(ERROR_MSG, "File not found");
//                    return;
//                }
//
//                try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(f)))
//                {
//                    IOUtils.copy(stream, response.getOutputStream());
//                }
//            }
//            catch (Exception e)
//            {
//                _log.error(e.getMessage(), e);
//                errors.reject(ERROR_MSG, e.getMessage());
//            }
//        }
//    }
//
//    public static class ExternalLinkForm
//    {
//        private int _rowId;
//        private String _container;
//
//        public int getRowId()
//        {
//            return _rowId;
//        }
//
//        public void setRowId(int rowId)
//        {
//            _rowId = rowId;
//        }
//
//        public String getContainer()
//        {
//            return _container;
//        }
//
//        public void setContainer(String container)
//        {
//            _container = container;
//        }
//    }

    @RequiresPermission(ReadPermission.class)
    public class GetBamHaplotypesAction extends ReadOnlyApiAction<GetBamHaplotypesForm>
    {
        @Override
        public ApiResponse execute(GetBamHaplotypesForm form, BindException errors) throws Exception
        {
            try
            {
                JSONObject ret;
                if ("nt".equalsIgnoreCase(form.getMode()))
                {
                    ret = new BamHaplotyper(getUser()).calculateNTHaplotypes(form.getOutputFileIds(), form.getRegions(), form.getMinQual() == null ? 0 : form.getMinQual(), form.isRequireCompleteCoverage());
                }
                else if ("aa".equalsIgnoreCase(form.getMode()))
                {
                    ret = new BamHaplotyper(getUser()).calculateAAHaplotypes(form.getOutputFileIds());
                }
                else
                {
                    errors.reject(ERROR_MSG, "Invalid mode: " + form.getMode());
                    return null;
                }

                return new ApiSimpleResponse(ret);
            }
            catch (IOException | SAMException e)
            {
                _log.error(e.getMessage(), e);
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
        }
    }

    public static class GetBamHaplotypesForm
    {
        int[] _outputFileIds;
        String[] _regions;
        Integer _minQual;
        String _mode;
        boolean _requireCompleteCoverage = false;

        public int[] getOutputFileIds()
        {
            return _outputFileIds;
        }

        public void setOutputFileIds(int[] outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }

        public String[] getRegions()
        {
            return _regions;
        }

        public void setRegions(String[] regions)
        {
            _regions = regions;
        }

        public Integer getMinQual()
        {
            return _minQual;
        }

        public void setMinQual(Integer minQual)
        {
            _minQual = minQual;
        }

        public String getMode()
        {
            return _mode;
        }

        public void setMode(String mode)
        {
            _mode = mode;
        }

        public boolean isRequireCompleteCoverage()
        {
            return _requireCompleteCoverage;
        }

        public void setRequireCompleteCoverage(boolean requireCompleteCoverage)
        {
            _requireCompleteCoverage = requireCompleteCoverage;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class ConcatenateSequencesAction extends MutatingApiAction<ConcatenateSequencesForm>
    {
        @Override
        public Object execute(ConcatenateSequencesForm form, BindException errors) throws Exception
        {
            try
            {
                if (form.getName() == null || form.getDescription() == null)
                {
                    errors.reject(ERROR_MSG, "Must provide name and description");
                    return null;
                }

                if (form.getSequenceIds() == null || form.getSequenceIds().length == 0)
                {
                    errors.reject(ERROR_MSG, "Must provide the sequence IDs");
                    return null;
                }

                List<Integer> ntIds = Arrays.asList(form.getSequenceIds());

                PipeRoot pr = PipelineService.get().getPipelineRootSetting(getContainer());
                if (pr == null)
                {
                    errors.reject(ERROR_MSG, "Pipeline root not set");
                    return null;
                }

                SequenceConcatPipelineJob job = new SequenceConcatPipelineJob(getContainer(), getUser(), pr, form.getName(), form.getDescription(), ntIds);
                PipelineService.get().queueJob(job);

                return new ApiSimpleResponse("success", true);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }
        }
    }

    public static class ConcatenateSequencesForm
    {
        private Integer[] _sequenceIds;
        private String _name;
        private String _description;

        public Integer[] getSequenceIds()
        {
            return _sequenceIds;
        }

        public void setSequenceIds(Integer[] sequenceIds)
        {
            _sequenceIds = sequenceIds;
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

    @RequiresPermission(InsertPermission.class)
    public class ImportSequenceTracksAction extends MutatingApiAction<ImportTracksForm>
    {
        @Override
        public ApiResponse execute(ImportTracksForm form, BindException errors) throws Exception
        {
            PipeRoot root = PipelineService.get().getPipelineRootSetting(getContainer());
            if (root == null || !root.isValid())
            {
                throw new NotFoundException();
            }

            if (form.getRecords() == null)
            {
                errors.reject(ERROR_MSG, "No files to save");
                return null;
            }

            File dirData = root.getRootPath();
            if (form.getPath() != null)
            {
                dirData = root.resolvePath(form.getPath());
                if (dirData == null || !NetworkDrive.exists(dirData))
                {
                    throw new NotFoundException("Could not resolve path: " + form.getPath());
                }
            }

            JSONArray arr = new JSONArray(form.getRecords());

            List<PipelineJob> toCreate = new ArrayList<>();
            for (JSONObject o : arr.toJSONObjectArray())
            {
                File file = new File(dirData, o.getString("fileName"));
                if (!file.exists())
                {
                    errors.reject(ERROR_MSG, "Unknown file: " + file.getPath());
                    return null;
                }

                if (o.get("name") == null)
                {
                    errors.reject(ERROR_MSG, "Missing name for file: " + file.getName());
                    return null;
                }

                if (o.get("libraryId") == null)
                {
                    errors.reject(ERROR_MSG, "Missing genome Id for file: " + file.getName());
                    return null;
                }

                try
                {
                    Container target = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
                    Container genomeFolder = ReferenceGenomeImpl.getFolderForGenome(o.getInt("libraryId"));
                    if (!genomeFolder.hasPermission(getUser(), UpdatePermission.class))
                    {
                        throw new UnauthorizedException("You do not have permission to update genome " + o.get("libraryId") + ", which is saved in the folder: " + genomeFolder.getPath());
                    }

                    ImportGenomeTrackPipelineJob job = new ImportGenomeTrackPipelineJob(target, getUser(), null, root, o.getInt("libraryId"), o.getString("name"), file, file.getName(), o.getString("description"), form.getDoChrTranslation() == null ? true : form.getDoChrTranslation());
                    toCreate.add(job);
                }
                catch (IOException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    _log.error(e);

                    return null;
                }
            }

            try
            {
                for (PipelineJob job : toCreate)
                {
                    PipelineService.get().queueJob(job);
                }
            }
            catch (PipelineValidationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                _log.error(e);

                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetSequenceImportDefaultsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Map<String, Object> resultProperties = new HashMap<>();
            Container target = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
            resultProperties.putAll(PropertyManager.getProperties(target, CONFIG_PROPERTY_DOMAIN_IMPORT));

            return new ApiSimpleResponse(resultProperties);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SetSequenceImportDefaultsAction extends MutatingApiAction<SetSequenceImportDefaultsForm>
    {
        public static final String INPUT_FILE_TREATMENT = "inputFileTreatment";

        @Override
        public ApiResponse execute(SetSequenceImportDefaultsForm form, BindException errors) throws Exception
        {
            Container target = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
            PropertyManager.PropertyMap configMap = PropertyManager.getWritableProperties(target, CONFIG_PROPERTY_DOMAIN_IMPORT, true);
            configMap.put(INPUT_FILE_TREATMENT, form.getInputFileTreatment());

            configMap.save();

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class SetSequenceImportDefaultsForm
    {
        private String _inputFileTreatment;

        public String getInputFileTreatment()
        {
            return _inputFileTreatment;
        }

        public void setInputFileTreatment(String inputFileTreatment)
        {
            _inputFileTreatment = inputFileTreatment;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetSamplesFromVcfAction extends ReadOnlyApiAction<OutputFilesForm>
    {
        @Override
        public ApiResponse execute(OutputFilesForm form, BindException errors) throws Exception
        {
            Map<String, Object> resp = new HashMap<>();

            Map<Integer, JSONObject> fileMap = new HashMap<>();
            for (Integer rowId : form.getOutputFileIds())
            {
                SequenceOutputFile f = SequenceOutputFile.getForId(rowId);
                if (f != null)
                {
                    fileMap.put(f.getRowid(), f.toJSON());

                    try
                    {
                        List<String> samples = getSamplesForVcf(f.getFile());
                        resp.put(f.getRowid().toString(), samples);
                    }
                    catch (TribbleException e)
                    {
                        _log.error(e.getMessage(), e);
                        errors.reject(ERROR_MSG, e.getMessage());
                    }
                }
                else
                {
                    errors.reject(ERROR_MSG, "Unable to find output file with ID: " + rowId);
                    return null;
                }
            }

            Map<String, Object> ret = new HashMap<>();
            ret.put("success", true);
            ret.put("samples", resp);
            ret.put("outputFileMap", fileMap);

            return new ApiSimpleResponse(ret);
        }

        public List<String> getSamplesForVcf(File vcf)
        {
            try (VCFFileReader reader = new VCFFileReader(vcf))
            {
                VCFHeader header = reader.getFileHeader();

                return header.getGenotypeSamples();
            }
        }
    }

    public static class OutputFilesForm
    {
        Integer[] _outputFileIds;

        public Integer[] getOutputFileIds()
        {
            return _outputFileIds;
        }

        public void setOutputFileIds(Integer[] outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }
    }

    @RequiresSiteAdmin
    public class UpdateExpDataPathAction extends ConfirmAction<UpdateExpDataPathForm>
    {
        @Override
        public void validateCommand(UpdateExpDataPathForm form, Errors errors)
        {

        }

        @Override
        public URLHelper getSuccessURL(UpdateExpDataPathForm form)
        {
            return getContainer().getStartURL(getUser());
        }

        @Override
        public ModelAndView getConfirmView(UpdateExpDataPathForm form, BindException errors) throws Exception
        {
            return new HtmlView(HtmlString.unsafe("This will update the DataFileUrl on the selected ExpData to the path provided. This should be a full URI, such as file:///my/path/myFile.txt." +
                    "<br><br>" +
                    "<label>ExpData ID: </label><input name=\"rowId\" value = \"" + HtmlString.of(form.getRowId() > 0 ? form.getRowId() : "") + "\"><br>" +
                    "<label>DataFileUrl </label><input name=\"dataFileUrl\" value = \"" + HtmlString.of(form.getDataFileUrl()) + "\"><br>"));
        }

        @Override
        public boolean handlePost(UpdateExpDataPathForm form, BindException errors) throws Exception
        {
            int rowId = form.getRowId();
            ExpData d = ExperimentService.get().getExpData(rowId);
            if (d == null)
            {
                errors.reject(ERROR_MSG, "Invalid ExpData: " + form.getRowId());
                return false;
            }

            URI newUri = URI.create(form.getDataFileUrl());
            File f = new File(newUri);
            if (!f.exists())
            {
                throw new PipelineJobException("Missing file: " + form.getDataFileUrl());
            }

            d.setDataFileURI(newUri);
            d.save(getUser());

            return true;
        }
    }

    public static class UpdateExpDataPathForm
    {
        private int _rowId;

        private String _dataFileUrl;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getDataFileUrl()
        {
            return _dataFileUrl;
        }

        public void setDataFileUrl(String dataFileUrl)
        {
            _dataFileUrl = dataFileUrl;
        }
    }
}