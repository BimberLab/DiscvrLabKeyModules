/*
 * Copyright (c) 2020 LabKey Corporation
 *
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

package org.labkey.singlecell;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.singlecell.run.CellRangerWrapper;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SingleCellController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SingleCellController.class);
    public static final String NAME = "singlecell";

    private static final Logger _log = LogManager.getLogger(SingleCellController.class);

    public SingleCellController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(InsertPermission.class)
    public static class DownloadLoupeDataAction extends ExportAction<OutputFilesForm>
    {
        @Override
        public void export(OutputFilesForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            Map<String, Set<File>> toExport = new HashMap<>();

            for (Integer rowId : form.getOutputFileIds())
            {
                SequenceOutputFile so = SequenceOutputFile.getForId(rowId);
                if (so != null)
                {
                    File loupe = so.getFile();
                    if (!loupe.exists())
                    {
                        errors.reject(ERROR_MSG, "Missing file: " + loupe.getPath());
                        return;
                    }

                    String name = FileUtil.makeLegalName(so.getName());
                    Set<File> toAdd = toExport.getOrDefault(name, new HashSet<>());
                    toAdd.addAll(CellRangerWrapper.getRawDataDirs(loupe.getParentFile(), false, true));

                    toExport.put(name, toAdd);
                }
                else
                {
                    errors.reject(ERROR_MSG, "Unable to find output file with ID: " + rowId);
                    return;
                }
            }

            PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), "LoupeData.zip", true);
            try (ZipOutputStream zOut = new ZipOutputStream(response.getOutputStream()))
            {
                for (String dir : toExport.keySet())
                {
                    String prefix = dir + "/";
                    for (File f : toExport.get(dir))
                    {
                        try
                        {
                            addToArchive(zOut, f, prefix);
                        }
                        catch (Exception e)
                        {
                            _log.error(e);
                            errors.reject(ERROR_MSG, e.getMessage());
                            break;
                        }
                    }
                }
            }
        }

        private void addToArchive(ZipOutputStream zOut, File srcFile, String prefix) throws IOException
        {
            if (srcFile.isDirectory())
            {
                File[] files = srcFile.listFiles();
                for (int i = 0; i < files.length; i++)
                {
                    addToArchive(zOut, files[i], prefix + srcFile.getName() + "/");
                }
            }
            else
            {
                try (FileInputStream in = new FileInputStream(srcFile))
                {
                    zOut.putNextEntry(new ZipEntry(prefix + srcFile.getName()));
                    IOUtils.copy(in, zOut);
                    zOut.closeEntry();
                }
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

    @RequiresPermission(InsertPermission.class)
    public static class ImportTenXAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        @Override
        public Object execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            List<Map<String, Object>> sampleRows = parseRows(form, "sampleRows", getContainer());
            List<Map<String, Object>> sortRows = parseRows(form, "sortRows", getContainer());
            List<Map<String, Object>> readsetRows = parseRows(form, "readsetRows", getContainer());
            List<Map<String, Object>> cDNARows = parseRows(form, "cDNARows", getContainer());

            UserSchema scSchema = QueryService.get().getUserSchema(getUser(), getContainer(), SingleCellSchema.NAME);
            UserSchema sequenceAnalysis = QueryService.get().getUserSchema(getUser(), getContainer(), "sequenceanalysis");

            try (DbScope.Transaction transaction = DbScope.getLabKeyScope().ensureTransaction())
            {
                BatchValidationException bve = new BatchValidationException();
                validateBarcodes(readsetRows, getContainer(), getUser());

                Map<String, Integer> sampleMap = new HashMap<>();
                final List<Map<String, Object>> sampleRowsToInsert = new ArrayList<>();
                sampleRows.forEach(r -> {
                    if (r.get("objectId") == null)
                    {
                        throw new ApiUsageException("Missing objectId for sample row");
                    }

                    if (r.get("rowId") != null && StringUtils.trimToNull(r.get("rowId").toString()) != null)
                    {
                        sampleMap.put((String) r.get("objectId"), (Integer) r.get("rowId"));
                    }
                    else
                    {
                        sampleRowsToInsert.add(r);
                    }
                });


                List<Map<String, Object>> insertedSampleRows = scSchema.getTable(SingleCellSchema.TABLE_SAMPLES, null).getUpdateService().insertRows(getUser(), getContainer(), sampleRowsToInsert, bve, null, new HashMap<>());
                if (bve.hasErrors())
                {
                    throw bve;
                }

                insertedSampleRows.forEach(r -> {
                    if (r.get("rowId") == null)
                    {
                        throw new ApiUsageException("Missing rowId for inserted sample row");
                    }

                    sampleMap.put((String) r.get("objectId"), (Integer) r.get("rowId"));
                });

                Map<String, Integer> sortMap = new HashMap<>();
                final List<Map<String, Object>> sortRowsToInsert = new ArrayList<>();
                sortRows.forEach(r -> {
                    if (sampleMap.get((String)r.get("sampleGUID")) == null)
                    {
                        throw new ApiUsageException("Unable to find sampleId for row");
                    }

                    if (r.get("rowId") != null && StringUtils.trimToNull(r.get("rowId").toString()) != null)
                    {
                        sortMap.put((String) r.get("objectId"), (Integer) r.get("rowId"));
                    }
                    else
                    {
                        r.put("sampleId", sampleMap.get((String)r.get("sampleGUID")));
                        sortRowsToInsert.add(r);
                    }
                });

                sortRows = scSchema.getTable(SingleCellSchema.TABLE_SORTS, null).getUpdateService().insertRows(getUser(), getContainer(), sortRowsToInsert, bve, null, new HashMap<>());
                if (bve.hasErrors())
                {
                    throw bve;
                }

                sortRows.forEach(r -> {
                    if (r.get("objectId") == null)
                    {
                        throw new ApiUsageException("Missing objectId for sort row");
                    }

                    sortMap.put((String) r.get("objectId"), (Integer) r.get("rowId"));
                });

                readsetRows = sequenceAnalysis.getTable("sequence_readsets", null).getUpdateService().insertRows(getUser(), getContainer(), readsetRows, bve, null, new HashMap<>());
                if (bve.hasErrors())
                {
                    throw bve;
                }

                Map<String, Integer> readsetMap = new HashMap<>();
                readsetRows.forEach(r -> {
                    if (r.get("objectId") == null)
                    {
                        throw new ApiUsageException("Missing objectId for readset row");
                    }

                    readsetMap.put((String)r.get("objectId"), (Integer)r.get("rowId"));
                });

                cDNARows.forEach(r -> {
                    if (sortMap.get((String)r.get("sortGUID")) == null)
                    {
                        throw new ApiUsageException("Unable to find sortId for row");
                    }
                    r.put("sortId", sortMap.get((String)r.get("sortGUID")));
                });
                cDNARows.forEach(r -> r.put("readsetId", readsetMap.get((String)r.get("readsetGUID"))));
                cDNARows.forEach(r -> r.put("hashingReadsetId", readsetMap.get((String)r.get("hashingReadsetGUID"))));
                cDNARows.forEach(r -> r.put("tcrReadsetId", readsetMap.get((String)r.get("tcrReadsetGUID"))));
                cDNARows.forEach(r -> r.put("citeseqReadsetId", readsetMap.get((String)r.get("citeseqReadsetGUID"))));
                scSchema.getTable(SingleCellSchema.TABLE_CDNAS, null).getUpdateService().insertRows(getUser(), getContainer(), cDNARows, bve, null, new HashMap<>());
                if (bve.hasErrors())
                {
                    throw bve;
                }

                transaction.commit();
            }
            catch (Exception e)
            {
                _log.error(e);

                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    private static void validateBarcodes(List<Map<String, Object>> readsetRows, Container c, User u)
    {
        Set<String> uniqueBarcodeNames = new HashSet<>();
        readsetRows.forEach(rs -> {
            if (rs.get("barcode5") != null) {
                uniqueBarcodeNames.add((String.valueOf(rs.get("barcode5"))));
            }

            if (rs.get("barcode3") != null) {
                uniqueBarcodeNames.add((String.valueOf(rs.get("barcode3"))));
            }
        });

        if (!uniqueBarcodeNames.isEmpty())
        {
            TableInfo barcodes = QueryService.get().getUserSchema(u, c, SingleCellSchema.SEQUENCE_SCHEMA_NAME).getTable("barcodes");
            Set<String> foundTags = new HashSet<>(new TableSelector(barcodes, PageFlowUtil.set("tag_name"), new SimpleFilter(FieldKey.fromString("tag_name"), uniqueBarcodeNames, CompareType.IN), null).getArrayList(String.class));
            if (foundTags.size() != uniqueBarcodeNames.size())
            {
                uniqueBarcodeNames.removeAll(foundTags);

                throw new ApiUsageException("The following barcodes were not found: " + StringUtils.join(uniqueBarcodeNames, ","));
            }
        }
    }

    private static List<Map<String, Object>> parseRows(SimpleApiJsonForm form, String propName, Container container) throws ApiUsageException
    {
        if (!form.getNewJsonObject().has(propName))
        {
            throw new ApiUsageException("Missing property: " + propName);
        }

        JSONArray arr = form.getNewJsonObject().getJSONArray(propName);

        List<Map<String, Object>> ret = new ArrayList<>();
        JsonUtil.toJSONObjectList(arr).forEach(m -> {
            Map<String, Object> map = new CaseInsensitiveHashMap<>();
            map.putAll(m.toMap());

            if (map.containsKey("workbook"))
            {
                Container parent = container.getContainerFor(ContainerType.DataType.folderManagement);
                Container workbook = ContainerManager.getForPath(parent.getPath() + "/" + map.get("workbook").toString());
                if (workbook == null)
                {
                    throw new IllegalArgumentException("Unable to identify matching workbook for: " + map.get("workbook"));
                }

                map.put("container", workbook.getId());
            }
            ret.add(map);
        });

        return ret;
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetMatchingSamplesAction extends ReadOnlyApiAction<SimpleApiJsonForm>
    {
        final List<String> FIELDS = Arrays.asList("subjectId", "sampledate", "subjectid", "celltype", "tissue", "assaytype", "stim");

        @Override
        public Object execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            List<Map<String, Object>> sampleRows = parseRows(form, "sampleRows", getContainer());

            UserSchema us = QueryService.get().getUserSchema(getUser(), getContainer(), SingleCellSchema.NAME);
            if (us == null)
            {
                throw new ApiUsageException("Unable to find schema: " + SingleCellSchema.NAME);
            }

            TableInfo ti = us.getTable(SingleCellSchema.TABLE_SAMPLES, null);
            TableInfo tiSort = us.getTable(SingleCellSchema.TABLE_SORTS, null);

            List<String> retErrors = new ArrayList<>();
            Map<Object, Integer> sampleRowMap = new HashMap<>();
            Map<Object, Integer> sortRowMap = new HashMap<>();
            sampleRows.forEach(r -> {
                List<Object> keys = new ArrayList<>();
                SimpleFilter filter = new SimpleFilter();
                FIELDS.forEach(f -> {
                    if (r.get(f) != null)
                    {
                        filter.addCondition(FieldKey.fromString(f), r.get(f), ("sampledate".equals(f) ? CompareType.DATE_EQUAL : CompareType.EQUAL));
                        keys.add(r.get(f));
                    }
                });

                if (!filter.isEmpty())
                {
                    TableSelector ts = new TableSelector(ti, PageFlowUtil.set("rowId"), filter, null);
                    long count = ts.getRowCount();
                    if (count == 1)
                    {
                        int rowId = ts.getObject(Integer.class);
                        sampleRowMap.put(r.get("objectId"), rowId);

                        if (r.get("population") != null)
                        {
                            SimpleFilter sortFilter = new SimpleFilter(FieldKey.fromString("sampleId"), rowId);
                            sortFilter.addCondition(FieldKey.fromString("population"), r.get("population"));
                            if (r.get("hto") == null)
                            {
                                sortFilter.addCondition(FieldKey.fromString("hto"), null, CompareType.ISBLANK);
                            }
                            else
                            {
                                sortFilter.addCondition(FieldKey.fromString("hto"), r.get("hto"));
                            }

                            TableSelector tsSort = new TableSelector(tiSort, PageFlowUtil.set("rowId"), sortFilter, null);
                            long countSort = tsSort.getRowCount();
                            if (countSort == 1)
                            {
                                int sortRowId = tsSort.getObject(Integer.class);
                                sortRowMap.put(r.get("objectId"), sortRowId);
                            }
                            else if (countSort > 1)
                            {
                                retErrors.add("More than one matching sort found: " + StringUtils.join(keys, "|") + "|" + r.get("population"));
                            }
                        }
                    }
                    else if (count > 1 && filter.getClauses().size() == FIELDS.size())
                    {
                        retErrors.add("More than one matching sample found: " + StringUtils.join(keys, "|"));
                    }
                }
            });

            resp.put("sampleMap", sampleRowMap);
            resp.put("sortMap", sortRowMap);
            resp.put("recordErrors", retErrors);

            return resp;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public static class SingleCellProcessingAction extends SimpleViewAction<Object>
    {
        public URLHelper getSuccessURL(Object form)
        {
            return getContainer().getStartURL(getUser());
        }

        @Override
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            LinkedHashSet<ClientDependency> cds = new LinkedHashSet<>();
            for (PipelineStepProvider<?> fact : SequencePipelineService.get().getAllProviders())
            {
                cds.addAll(fact.getClientDependencies());
            }

            Module module = ModuleLoader.getInstance().getModule(SingleCellModule.class);

            ModuleHtmlView view = ModuleHtmlView.get(module, Path.parse("views/singleCellProcessing.html"));
            assert view != null;
            view.addClientDependencies(cds);
            getPageConfig().setIncludePostParameters(true);

            return view;
        }

        @Override
        public void addNavTrail(NavTree tree)
        {
            tree.addChild("Process Single Cell Data");
        }
    }

}
