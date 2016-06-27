/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.laboratory.assay;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.AssayBatchDomainKind;
import org.labkey.api.exp.property.AssayResultDomainKind;
import org.labkey.api.exp.property.AssayRunDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.assay.AssayDataProvider;
import org.labkey.api.laboratory.assay.AssayImportMethod;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunCreator;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.laboratory.LaboratoryController;
import org.labkey.laboratory.LaboratorySchema;

import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 9/26/12
 * Time: 12:56 PM
 */
public class AssayHelper
{
    private static final AssayHelper _instance = new AssayHelper();
    private static final Logger _log = Logger.getLogger(AssayHelper.class);

    private AssayHelper()
    {

    }

    public static AssayHelper get()
    {
        return _instance;
    }

    public Map<String, File> saveResultsFile(List<Map<String, Object>> results, JSONObject json, File file, AssayProvider provider, ExpProtocol protocol) throws ExperimentException, ValidationException
    {
        Map<String, File> files = new HashMap<String, File>();
        //TODO: consider adding as input??
        //files.put("RawInput", file);

        File newFile = getUniqueOutputName(file, "txt");
        Domain resultsDomain = provider.getResultsDomain(protocol);
        List<String> headers = new ArrayList();
        for (DomainProperty dp : resultsDomain.getProperties())
        {
            headers.add(dp.getName());
        }

        try (TSVMapWriter tsv = new TSVMapWriter(headers, results))
        {
            tsv.write(newFile);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e.getMessage());
        }

        files.put(AssayDataCollector.PRIMARY_FILE, newFile);

        return files;
    }

    public Map<String, Object> saveTemplate(User u, Container c, ExpProtocol protocol, @Nullable Integer templateId, String title, String importMethod, JSONObject json) throws BatchValidationException, ValidationException
    {
        BatchValidationException errors = new BatchValidationException();
        try
        {
            validateTemplate(u, c, protocol, templateId, title, importMethod, json);

            TableInfo ti = LaboratorySchema.getInstance().getSchema().getTable(LaboratorySchema.TABLE_ASSAY_RUN_TEMPLATES);
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("assayId", protocol.getRowId());
            row.put("title", title);
            row.put("importMethod", importMethod);
            row.put("json", json.toString());
            row.put("container", c.getId());

            if (templateId == null)
            {
                row = Table.insert(u, ti, row);
                templateId = (Integer)row.get("rowid");
            }
            else
            {
                row.put("rowid", templateId);
                row = Table.update(u, ti, row, templateId);
            }

            return row;
        }
        catch (RuntimeSQLException e)
        {
            _log.error(e.getMessage(), e);
            errors.addRowError(new ValidationException(e.getMessage()));
            throw errors;
        }
    }

    public void validateTemplate(User u, Container c, ExpProtocol protocol, Integer templateId, String title, String importMethod, JSONObject json) throws BatchValidationException
    {
        BatchValidationException errors = new BatchValidationException();

        AssayProvider ap = AssayService.get().getProvider(protocol);
        if (ap == null)
        {
            errors.addRowError(new ValidationException("Unable to find assay provider for protocol: " + protocol.getName()));
            throw errors;
        }

        AssayDataProvider dp = LaboratoryService.get().getDataProviderForAssay(protocol.getRowId());
        if (dp == null)
        {
            errors.addRowError(new ValidationException("Unable to find data provider for assay: " + protocol.getName()));
            throw errors;
        }

        AssayImportMethod method = dp.getImportMethodByName(importMethod);
        if (method == null)
        {
            errors.addRowError(new ValidationException("Unable to find import method with name: " + method));
            throw errors;
        }

        method.validateTemplate(u, c, protocol, templateId, title, json, errors);

        if (errors.hasErrors())
            throw errors;
    }

    public Pair<ExpExperiment, ExpRun> saveAssayBatch(List<Map<String, Object>> results, JSONObject json, File file, ViewContext ctx, AssayProvider provider, ExpProtocol protocol) throws ValidationException, ExperimentException
    {
        AssayRunCreator creator = provider.getRunCreator();
        Map<String, String> runProperties = new CaseInsensitiveHashMap(json.optJSONObject("Run"));
        String name = runProperties.get(ExperimentJSONConverter.NAME);
        String comments = runProperties.get("comments");

        Map<String, String> batchProperties = (Map)json.optJSONObject("Batch");
        if (batchProperties == null)
        {
            batchProperties = new HashMap<>();
        }

        if (!batchProperties.containsKey("Name"))
            batchProperties.put("Name", name);

        Map<String, File> uploadedFiles = saveResultsFile(results, json, file, provider, protocol);

        //TODO: see AssayRunAsyncContext
        AssayRunUploadContext uploadContext = new RunUploadContext(protocol, provider, name, comments, runProperties, batchProperties, ctx, uploadedFiles);
        Pair<ExpExperiment, ExpRun> resultRows = creator.saveExperimentRun(uploadContext, null);
        return resultRows;
    }

    public String getCannonicalName(String field, Domain domain)
    {
        for (DomainProperty dp : domain.getProperties())
        {
            if (field.equalsIgnoreCase(dp.getName()))
            {
                return dp.getName();
            }
        }

        for (DomainProperty dp : domain.getProperties())
        {
            if (field.equalsIgnoreCase(dp.getLabel()))
            {
                return dp.getName();
            }
        }

        return null;
    }

    private File getUniqueOutputName(File input, String extension)
    {
        File parent = input.getParentFile();
        String basename = FileUtil.getBaseName(input);
        int suffix = 1;
        File newFile = new File(parent,  basename + "." + extension);
        while (newFile.exists())
        {
            newFile = new File(parent,  basename + "-" + suffix + "." + extension);
            suffix++;
        }
        return newFile;
    }

    private static Domain saveDomain(Domain existing, AssayProvider ap, ExpProtocol p, User user) throws ChangePropertyDescriptorException
    {
        existing.save(user);

        return getExistingDomain(ap, p, user, existing.getDomainKind());
    }

    @Nullable
    private static Domain getExistingDomain(AssayProvider ap, ExpProtocol p, User u, DomainKind domainKind)
    {
        if (domainKind instanceof  AssayBatchDomainKind)
        {
            return ap.getBatchDomain(p);
        }
        else if (domainKind instanceof AssayRunDomainKind)
        {
            return ap.getRunDomain(p);
        }
        else if (domainKind instanceof AssayResultDomainKind)
        {
            return ap.getResultsDomain(p);
        }

        return null;
    }

    public static List<String> ensureAssayFields(User user, String providerName) throws ChangePropertyDescriptorException
    {
        return ensureAssayFields(user, providerName, false, false);
    }

    public static List<String> ensureAssayFields(User user, String providerName, boolean renameConflictingFields, boolean reportMessagesOnly) throws ChangePropertyDescriptorException
    {
        List<String> messages = new ArrayList<String>();

        if (!reportMessagesOnly)
            _log.info("Attempting to synchronize columns for all instances of assay: " + providerName);

        AssayProvider ap = AssayService.get().getProvider(providerName);
        if (ap == null)
        {
            messages.add("Unknown assay provider: " + providerName);
            return messages;
        }

        List<ExpProtocol> allProtocols = new ArrayList<ExpProtocol>();
        Integer[] protocolIds = new TableSelector(ExperimentService.get().getTinfoProtocol(), Collections.singleton("rowid"), null, null).getArray(Integer.class);
        for (Integer protocolId : protocolIds)
        {
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
            AssayProvider p = AssayService.get().getProvider(protocol);
            if (p != null && p.equals(ap))
            {
                allProtocols.add(protocol);
            }
        }

        try
        {
            boolean changed = false;
            for (ExpProtocol p : allProtocols)
            {
                List<Pair<Domain, Map<DomainProperty, Object>>> domains = ap.createDefaultDomains(p.getContainer(), user);
                for (Pair<Domain, Map<DomainProperty, Object>> pair : domains)
                {
                    Domain d = pair.first;
                    Domain existing = getExistingDomain(ap, p, user, d.getDomainKind());
                    for (DomainProperty dp : d.getProperties())
                    {
                        DomainProperty existingProp = existing.getPropertyByName(dp.getName());
                        if (existingProp == null)
                        {
                            String msg = "Adding property: " + dp.getName() + " to assay protocol: " + p.getRowId() + " from assay type " + ap.getName();
                            messages.add(msg);
                            if (!reportMessagesOnly)
                            {
                                _log.info(msg);
                                existing = addPropertyToDomain(existing, ap, p, dp, user);
                            }
                            changed = true;
                        }
                        else
                        {
                            //properties match, but not case
                            if (!dp.getName().equals(existingProp.getName()))
                            {
                                String msg = "Property has the case: " + existingProp.getName() + ", but expected " + dp.getName() + " for assay protocol: " + p.getRowId() + " from assay type " + ap.getName() + ".";
                                messages.add(msg);

                                changed = true;
                                if (!reportMessagesOnly)
                                {
                                    _log.info(msg);

                                    //we cant change case in place, so we rename first
                                    existingProp.setName(dp.getName() + "_new");
                                    String uri = existingProp.getPropertyURI().replaceAll(existingProp.getName() + "$", dp.getName());
                                    existingProp.setPropertyURI(uri);
                                    existing = saveDomain(existing, ap, p, user);

                                    existingProp.setName(dp.getName());
                                    existing = saveDomain(existing, ap, p, user);
                                }
                            }

                            if (!dp.getLabel().equals(existingProp.getLabel()))
                            {
                                String msg = "Property has the label: " + existingProp.getLabel() + ", but expected " + dp.getLabel() + " for assay protocol: " + p.getRowId() + " from assay type " + ap.getName() + ".";
                                messages.add(msg);
                                if (!reportMessagesOnly)
                                {
                                    _log.info(msg);
                                    changed = true;
                                    existingProp.setLabel(dp.getLabel());
                                    existing = saveDomain(existing, ap, p, user);
                                }
                            }

                            if (dp.getConceptURI() != null && !dp.getConceptURI().equals(existingProp.getConceptURI()))
                            {
                                String msg = "Property has the conceptURI: " + existingProp.getConceptURI() + ", but expected " + dp.getConceptURI() + " for assay protocol: " + p.getRowId() + " from assay type " + ap.getName() + ".";
                                messages.add(msg);
                                if (!reportMessagesOnly)
                                {
                                    _log.info(msg);
                                    changed = true;
                                    existingProp.setConceptURI(dp.getConceptURI());
                                    existing = saveDomain(existing, ap, p, user);
                                }
                            }

                            if (!dp.getRangeURI().equals(existingProp.getRangeURI()))
                            {
                                String msg = "Property has the wrong datatype: " + dp.getName() + " for assay protocol: " + p.getRowId() + " from assay type " + ap.getName() + ". Expected " + dp.getRangeURI() + ", but was " + existingProp.getRangeURI() + ".  ";
                                if (!renameConflictingFields)
                                {
                                    ActionURL url = new ActionURL(LaboratoryController.EnsureAssayFieldsAction.class, ContainerManager.getSharedContainer());
                                    url.addParameter("renameConflicts", true);
                                    url.addParameter("providerName", ap.getName());
                                    DetailsURL returnUrl = DetailsURL.fromString("/laboratory/synchronizeAssayFields.view");
                                    returnUrl.setContainerContext(ContainerManager.getSharedContainer());
                                    url.addParameter("returnUrl", returnUrl.toString());
                                    msg += "This will not be changed automatically.  If do you want to correct this, <a href=\"" + url.toString() + "\">CLICK HERE</a>.";
                                }
                                else
                                {
                                    msg += "Correcting this will be handled in 2 steps.  First, the existing field will be renamed by appending '_old' to the name.  Once complete, you will need to rerun this process in order to create a new field with the correct datatype.";
                                }

                                messages.add(msg);

                                if (!reportMessagesOnly && renameConflictingFields)
                                {
                                    _log.info(msg);

                                    changed = true;
                                    String suffix = "_old";
                                    int idx = 1;
                                    while (existing.getPropertyByName(existingProp.getName() + suffix) != null)
                                    {
                                        suffix = "_old" + idx;
                                        idx++;
                                    }

                                    existingProp.setName(existingProp.getName() + suffix);

                                    if (existingProp.getLabel() != null)
                                        existingProp.setLabel(existingProp.getLabel() + suffix);

                                    existing = saveDomain(existing, ap, p, user);
                                    existingProp = existing.getPropertyByName(existingProp.getName());

                                    existingProp.setPropertyURI(existingProp.getPropertyURI() + suffix);
                                    existing = saveDomain(existing, ap, p, user);

                                    //NOTE: for some reason when we do this in a single step, the new property ends up
                                    //showing up, but the old does not.  if we do this in 2 steps, everything is fine
                                    //_log.info("Creating field with the expected datatype");
                                    //existing = addPropertyToDomain(user, existing, dp);
                                    messages.add("NOTE: one or more fields had the wrong datatype, and the existing field was renamed.  You will need to re-run this page in order to create a new field with the correct datatype.");
                                }
                            }
                        }
                    }
                }
            }

            if (changed && !reportMessagesOnly)
            {
                _log.info("Purging all caches");
                Introspector.flushCaches();
                CacheManager.clearAllKnownCaches();
            }

            if (messages.size() == 0)
            {
                String msg = "No changes are necessary";
                messages.add(msg);
                if (!reportMessagesOnly)
                    _log.info(msg);
            }
        }
        catch (ChangePropertyDescriptorException e)
        {
            _log.error("Error modifying assay: " + e.getMessage());
            throw e;
        }

        return messages;
    }

    private static Domain addPropertyToDomain(Domain domain, AssayProvider ap, ExpProtocol p, DomainProperty dp, User user) throws ChangePropertyDescriptorException
    {
        //TODO: proper way to copy/save, assign propertyURI, etc?
        DomainProperty newProp = domain.addProperty();
        newProp.setName(dp.getName());
        newProp.setLabel(dp.getLabel());
        newProp.setRangeURI(dp.getRangeURI());
        newProp.setRequired(dp.isRequired());
        newProp.setPropertyURI(domain.getTypeURI() + "#" + dp.getName());
        newProp.setDescription(dp.getDescription());
        newProp.setConceptURI(dp.getConceptURI());
        newProp.setURL(dp.getURL());
        newProp.setLookup(dp.getLookup());
        newProp.setHidden(dp.isHidden());

        return saveDomain(domain, ap, p, user);
    }
}
