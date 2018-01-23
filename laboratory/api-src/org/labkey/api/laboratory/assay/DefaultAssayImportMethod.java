/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.api.laboratory.assay;

import org.apache.poi.ss.usermodel.Workbook;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 9/15/12
 * Time: 7:29 AM
 */
public class DefaultAssayImportMethod implements AssayImportMethod
{
    public static final String NAME = "Default Excel";
    protected static final String EMPTY_WELL_NAME = "empty";
    protected String _providerName;
    protected AssayProvider _ap;

    public DefaultAssayImportMethod(String providerName)
    {
        _providerName = providerName;
    }

    @Override
    public AssayParser getFileParser(Container c, User u, int assayId)
    {
        return new DefaultAssayParser(this, c, u, assayId);
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getLabel()
    {
        return "Default Excel Import";
    }

    @Override
    public String getProviderName()
    {
        return _providerName;
    }

    @Override
    public boolean hideTemplateDownload()
    {
        return false;
    }

    @Override
    public String getTooltip()
    {
        return "Choose this option to upload data using the basic, non-instrument specific excel template";
    }

    @Override
    public boolean doEnterResultsInGrid()
    {
        return false;
    }

    @Override
    public String getExampleDataUrl(ViewContext ctx)
    {
        return null;
    }

    @Override
    public String getTemplateInstructions()
    {
        return null;
    }

    @Override
    public JSONObject getMetadata(ViewContext ctx, ExpProtocol protocol)
    {
        JSONObject meta = new JSONObject();

        JSONObject batchMeta = new JSONObject();
        JSONObject importMethod = new JSONObject();
        importMethod.put("getInitialValue", "function(panel){if(panel.selectedMethod) {return panel.selectedMethod.name;}}");
        importMethod.put("hidden", true);
        batchMeta.put("importMethod", importMethod);
        meta.put("Batch", batchMeta);

        JSONObject runMeta = new JSONObject();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        runMeta.put("Name", new JSONObject().put("defaultValue", ctx.getUser().getDisplayName(ctx.getUser()) + "_" + format.format(new Date())));

        JSONObject runDate = new JSONObject();
        runDate.put("getInitialValue", "function(){return new Date();}");
        runDate.put("extFormat", "Y-m-d");
        runMeta.put("performedBy", new JSONObject().put("defaultValue", ctx.getUser().getDisplayName(ctx.getUser())));
        runMeta.put("runDate", runDate);
        runMeta.put("comments", new JSONObject().put("height", 100));
        meta.put("Run", runMeta);

        JSONObject resultsMeta = new JSONObject();
        resultsMeta.put("sampleId", new JSONObject().put("lookups", false));
        resultsMeta.put("subjectId", new JSONObject().put("lookups", false));
        meta.put("Results", resultsMeta);

        return meta;
    }

    protected AssayProvider getAssayProvider()
    {
        if (_ap == null)
            _ap = AssayService.get().getProvider(_providerName);

        return _ap;
    }

    protected JSONObject getJsonObject(JSONObject parent, String key)
    {
        return parent.containsKey(key) ? parent.getJSONObject(key): new JSONObject();
    }

    @Override
    public String getPreviewPanelClass()
    {
        return "Laboratory.ext.AssayPreviewPanel";
    }

    @Override
    public boolean supportsRunTemplates()
    {
        return false;
    }

    @Override
    public JSONObject toJson(ViewContext ctx, ExpProtocol protocol)
    {
        JSONObject json = new JSONObject();
        json.put("name", getName());
        json.put("label", getLabel());
        json.put("supportsTemplates", supportsRunTemplates());

        json.put("hideTemplateDownload", hideTemplateDownload());
        json.put("tooltip", getTooltip());
        json.put("enterResultsInGrid", doEnterResultsInGrid());

        json.put("exampleDataUrl", getExampleDataUrl(ctx));
        json.put("templateInstructions", getTemplateInstructions());
        json.put("previewPanelClass", getPreviewPanelClass());
        json.put("metadata", getMetadata(ctx, protocol));

        return json;
    }

    @Override
    public void generateTemplate(ViewContext ctx, ExpProtocol protocol, @Nullable Integer templateId, String title, JSONObject json) throws BatchValidationException
    {
        if (!supportsRunTemplates())
        {
            throw new UnsupportedOperationException("This import method does not support templates");
        }

        BatchValidationException errors = new BatchValidationException();
        validateTemplate(ctx.getUser(), ctx.getContainer(), protocol, templateId, title, json, errors);

        if (errors.hasErrors())
            throw errors;

        doGenerateTemplate(json, ctx.getRequest(), ctx.getResponse());
    }

    @Override
    public void validateTemplate(User u, Container c, ExpProtocol protocol, @Nullable Integer templateId, String title, JSONObject json, BatchValidationException errors) throws BatchValidationException
    {
        //NOTE: consider checking required fields; however, we need to differentiate which field we expect now, and which we expect later
    }

    /**
     * The typical idea of run templates is that the user supplies a subset of fields upfront (often sample information/metadata), and these
     * are saved ahead of time.  The instrument run is performed and that input is imported.  Based on those saved values, the ImportMethod typically generates
     * some input for the instrument (for example, the format that instrument natively expects).  This input will often contain some key that can be used
     * to connect samples from the template information saved in LabKey to the data output by the instrument.
     *
     * In the case of DefaultAssayImportMethod, there is no instrument expected.  Therefore there's often little reason to bother with this step (downloading the regular
     * import excel file ahead of time accomplishes the same thing).  This code below is implemented primarily to help subclasses that fully implement a run template
     * for their type of data.
     */
    public void doGenerateTemplate(JSONObject json, HttpServletRequest request, HttpServletResponse response) throws BatchValidationException
    {
        try
        {
            String filename = json.getString("templateName") + ".xlsx";
            ExcelWriter.ExcelDocumentType docType = ExcelWriter.ExcelDocumentType.xlsx;

            JSONObject resultDefaults = json.optJSONObject("Results");
            JSONArray results = json.getJSONArray("ResultRows");

            //append global results
            for (JSONObject row : results.toJSONObjectArray())
            {
                for (String prop : resultDefaults.keySet())
                {
                    if (row.get(prop) == null)
                    {
                        row.put(prop, resultDefaults.get(prop));
                    }
                }
            }

            //add header row:
            JSONArray rowsForExcel = new JSONArray();
            List<String> headerCols = getTemplateDownloadColumns();
            if (headerCols != null)
            {
                rowsForExcel.put(new JSONArray(headerCols));
            }

            for (JSONObject row : results.toJSONObjectArray())
            {
                JSONArray toAdd = new JSONArray();
                if (headerCols != null)
                {
                    for (String colName : headerCols)
                    {
                        toAdd.put(row.get(colName));
                    }
                }
                else
                {
                    for (String key : row.keySet())
                    {
                        toAdd.put(row.get(key));
                    }
                }

                rowsForExcel.put(toAdd);
            }

            JSONObject sheet = new JSONObject();
            sheet.put("name", "Data");
            sheet.put("data", rowsForExcel);

            JSONArray sheetsArray = new JSONArray();
            sheetsArray.put(sheet);
            Workbook workbook =  ExcelFactory.createFromArray(sheetsArray, docType);

            response.setContentType(docType.getMimeType());
            response.setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");
            response.setHeader("Pragma", "private");
            response.setHeader("Cache-Control", "private");

            workbook.write(response.getOutputStream());
        }
        catch (IOException e)
        {
            BatchValidationException bve = new BatchValidationException();
            bve.addRowError(new ValidationException(e.getMessage()));
            throw bve;
        }
    }

    /**
     * Override this method to define a list of columns for the download.
     * If null, no header will be appended
     *
     */
    protected List<String> getTemplateDownloadColumns()
    {
        return null;
    }

    protected Map<Object, Object> getWellMap96(final String keyProperty, final String valueProperty)
    {
        TableInfo ti = DbSchema.get("laboratory").getTable("well_layout");
        TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("plate"), 1), null);

        final Map<Object, Object> wellMap = new HashMap<>();
        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                wellMap.put(object.getObject(keyProperty), object.getObject(valueProperty));
            }
        });

        return wellMap;
    }

    protected enum QUAL_RESULT
    {
        POS(),
        NEG(),
        OUTLIER(),
        ND();

        QUAL_RESULT()
        {

        }

        public Integer getRowId()
        {
            TableInfo ti = DbSchema.get("laboratory").getTable("qual_results");
            TableSelector ts = new TableSelector(ti, Collections.singleton("rowid"), new SimpleFilter(FieldKey.fromString("meaning"), name()), null);
            if (ts.getRowCount() == 0)
                return null;

            Integer[] rowIds = ts.getArray(Integer.class);
            return rowIds[0];
        }
    }

    @Override
    public List<String> getImportColumns(ViewContext ctx, ExpProtocol protocol)
    {
        List<String> columns = new ArrayList<>();
        Domain resultDomain = getAssayProvider().getResultsDomain(protocol);
        JSONObject json = getMetadata(ctx, protocol).getJSONObject("Results");
        for (DomainProperty dp : resultDomain.getProperties())
        {
            JSONObject meta = json.containsKey(dp.getName()) ? json.getJSONObject(dp.getName()) : null;
            if (meta != null && meta.containsKey("setGlobally") && meta.getBoolean("setGlobally"))
                continue;
            else
                columns.add(dp.getLabel() == null ? dp.getName() : dp.getLabel());
        }

        return columns;
    }

    @Override
    public JSONObject getSupplementalTemplateMetadata()
    {
        return null;
    }

    public static enum SAMPLE_CATEGORY
    {
        Blank("Blank"),
        Control("Control"),
        NegControl("Neg Control"),
        PosControl("Pos Control"),
        Standard("Standard"),
        Unknown("Unknown");

        private String _label;

        SAMPLE_CATEGORY(String label)
        {
            _label = label;

        }

        public static SAMPLE_CATEGORY getEnum(String text)
        {
            if (text == null)
                return null;

            text = text.replaceAll(" ", "");
            return SAMPLE_CATEGORY.valueOf(text);
        }

        public String getLabel()
        {
            return _label;
        }
    }
}
