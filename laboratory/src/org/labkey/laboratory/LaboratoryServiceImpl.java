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
package org.labkey.laboratory;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.AssayDomainType;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.laboratory.assay.AssayDataProvider;
import org.labkey.api.laboratory.assay.SimpleAssayDataProvider;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.laboratory.assay.AssayHelper;
import org.labkey.laboratory.query.LaboratoryTableCustomizer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 9/15/12
 * Time: 6:27 AM
 */
public class LaboratoryServiceImpl extends LaboratoryService
{
    private Set<Module> _registeredModules = new HashSet<Module>();
    private Map<Module, List<ClientDependency>> _clientDependencies = new HashMap<Module, List<ClientDependency>>();
    private Map<String, Map<String, List<ButtonConfigFactory>>> _queryButtons = new CaseInsensitiveHashMap<Map<String, List<ButtonConfigFactory>>>();
    private Map<String, Map<String, List<ButtonConfigFactory>>> _assayButtons = new CaseInsensitiveHashMap<Map<String, List<ButtonConfigFactory>>>();
    private Map<String, DataProvider> _dataProviders = new HashMap<String, DataProvider>();
    private final Logger _log = Logger.getLogger(LaboratoryServiceImpl.class);

    public static final String DEMOGRAPHICS_PROPERTY_CATEGORY = "laboratory.demographicsSource";
    public static final String DATASOURCE_PROPERTY_CATEGORY = "laboratory.additionalDataSource";
    public static final String URL_DATASOURCE_PROPERTY_CATEGORY = "laboratory.urlDataSource";

    public LaboratoryServiceImpl()
    {

    }

    public void registerModule(Module module)
    {
        _registeredModules.add(module);
    }

    public Set<Module> getRegisteredModules()
    {
        return _registeredModules;
    }

    public void registerDataProvider(DataProvider dp)
    {
        if (_dataProviders.containsKey(dp.getKey())){
            _log.error("A DataProvider with the name: " + dp.getName() + " has already been registered");
        }

        _dataProviders.put(dp.getKey(), dp);
    }

    public synchronized Set<DataProvider> getDataProviders()
    {
        Set<DataProvider> providers = new HashSet<DataProvider>();
        providers.addAll(_dataProviders.values());

        Set<AssayProvider> registeredProviders = new HashSet<AssayProvider>();
        for (DataProvider dp : _dataProviders.values())
        {
            if (dp instanceof AssayDataProvider)
            registeredProviders.add(((AssayDataProvider) dp).getAssayProvider());
        }

        // also append any assays not explicitly registered with LaboratoryService
        // this first time we encounter this assayProvider, register it
        for (AssayProvider ap : AssayService.get().getAssayProviders())
        {
            if (!registeredProviders.contains(ap))
            {
                DataProvider provider = new SimpleAssayDataProvider(ap.getName());
                if (!_dataProviders.containsKey(provider.getKey()))
                {
                    registerDataProvider(provider);
                    providers.add(provider);
                }
                else
                    providers.add(_dataProviders.get(provider.getKey()));
            }
        }

        return providers;
    }

    public Set<AssayDataProvider> getRegisteredAssayProviders()
    {
        Set<AssayDataProvider> providers = new HashSet<AssayDataProvider>();
        for (DataProvider dp : _dataProviders.values())
        {
            if (dp instanceof AssayDataProvider)
            {
                providers.add((AssayDataProvider)dp);
            }
        }
        return providers;
    }

    public AssayDataProvider getDataProviderForAssay(int protocolId)
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
        if (protocol == null)
            return null;

        AssayProvider ap = AssayService.get().getProvider(protocol);
        return getDataProviderForAssay(ap);
    }

    public AssayDataProvider getDataProviderForAssay(AssayProvider ap)
    {
        for (AssayDataProvider dp : getRegisteredAssayProviders())
        {
            if (dp.getAssayProvider().equals(ap))
                return dp;
        }
        return new SimpleAssayDataProvider(ap.getName());
    }

    public Pair<ExpExperiment, ExpRun> saveAssayBatch(List<Map<String, Object>> results, JSONObject json, File file, String fileName, ViewContext ctx, AssayProvider provider, ExpProtocol protocol) throws ValidationException
    {
        try
        {
            return AssayHelper.get().saveAssayBatch(results, json, file, fileName, ctx, provider, protocol);
        }
        catch (ExperimentException e)
        {
            throw new ValidationException(e.getMessage());
        }
    }

    public List<NavItem> getSettingsItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        for (DataProvider dp : getDataProviders())
        {
            items.addAll(dp.getSettingsItems(c, u));
        }
        sortNavItems(items);
        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getSampleItems(Container c, User u)
    {
        List<NavItem> navItems = new ArrayList<NavItem>();
        for (DataProvider dp : getDataProviders()){
            navItems.addAll(dp.getSampleNavItems(c, u));
        }
        sortNavItems(navItems);
        return Collections.unmodifiableList(navItems);
    }

    public List<NavItem> getMiscItems(Container c, User u)
    {
        List<NavItem> navItems = new ArrayList<NavItem>();
        for (DataProvider dp : getDataProviders()){
            navItems.addAll(dp.getMiscItems(c, u));
        }
        sortNavItems(navItems);
        return Collections.unmodifiableList(navItems);
    }

    public List<NavItem> getReportItems(Container c, User u)
    {
        List<NavItem> navItems = new ArrayList<NavItem>();
        for (DataProvider dp : getDataProviders()){
            navItems.addAll(dp.getReportItems(c, u));
        }
        sortNavItems(navItems);

        return Collections.unmodifiableList(navItems);
    }

    public List<NavItem> getDataItems(Container c, User u)
    {
        List<NavItem> navItems = new ArrayList<NavItem>();
        for (DataProvider dp : LaboratoryService.get().getDataProviders())
        {
            navItems.addAll(dp.getDataNavItems(c, u));
        }

        sortNavItems(navItems);
        return Collections.unmodifiableList(navItems);
    }

    public DataProvider getDataProvider(String name)
    {
        return _dataProviders.get(name);
    }

    public void ensureAssayColumns(User u, String providerName) throws ChangePropertyDescriptorException
    {
        AssayHelper.ensureAssayFields(u, providerName);
    }

    public void sortNavItems(List<NavItem> navItems)
    {
        Collections.sort(navItems, new Comparator<NavItem>()
        {
            @Override
            public int compare(NavItem o1, NavItem o2)
            {
            return o1.getLabel().compareTo(o2.getLabel());
            }
        });
    }

    public void registerClientDependency(ClientDependency cd, Module owner)
    {
        List<ClientDependency> list = _clientDependencies.get(owner);
        if (list == null)
            list = new ArrayList<ClientDependency>();

        list.add(cd);

        _clientDependencies.put(owner, list);
    }

    public Set<ClientDependency> getRegisteredClientDependencies(Container c, User u)
    {
        Set<ClientDependency> set = new HashSet<ClientDependency>();
        for (Module m : _clientDependencies.keySet())
        {
            if (c.getActiveModules().contains(m))
            {
                set.addAll(_clientDependencies.get(m));
            }
        }

        return Collections.unmodifiableSet(set);
    }

    public void registerQueryButton(ButtonConfigFactory btn, String schema, String query)
    {
        Map<String, List<ButtonConfigFactory>> schemaMap = _queryButtons.get(schema);
        if (schemaMap == null)
            schemaMap = new CaseInsensitiveHashMap<List<ButtonConfigFactory>>();

        List<ButtonConfigFactory> list = schemaMap.get(query);
        if (list == null)
            list = new ArrayList<ButtonConfigFactory>();

        list.add(btn);

        schemaMap.put(query, list);
        _queryButtons.put(schema, schemaMap);
    }

    public List<ButtonConfigFactory> getQueryButtons(TableInfo ti)
    {
        List<ButtonConfigFactory> buttons = new ArrayList<ButtonConfigFactory>();

        Map<String, List<ButtonConfigFactory>> factories = _queryButtons.get(ti.getPublicSchemaName());
        if (factories == null)
            return buttons;

        List<ButtonConfigFactory> list = factories.get(ti.getPublicName());
        if (list == null)
            return  buttons;

        for (ButtonConfigFactory fact : list)
        {
            if (fact.isAvailable(ti))
                buttons.add(fact);
        }

        return Collections.unmodifiableList(buttons);
    }

    public void registerAssayButton(ButtonConfigFactory btn, String providerName, String domain)
    {
        Map<String, List<ButtonConfigFactory>> schemaMap = _assayButtons.get(providerName);
        if (schemaMap == null)
            schemaMap = new CaseInsensitiveHashMap<List<ButtonConfigFactory>>();

        List<ButtonConfigFactory> list = schemaMap.get(domain);
        if (list == null)
            list = new ArrayList<ButtonConfigFactory>();

        list.add(btn);

        schemaMap.put(domain, list);
        _assayButtons.put(providerName, schemaMap);
    }

    public List<ButtonConfigFactory> getAssayButtons(TableInfo ti, String providerName, String domain)
    {
        List<ButtonConfigFactory> buttons = new ArrayList<ButtonConfigFactory>();

        Map<String, List<ButtonConfigFactory>> factories = _assayButtons.get(providerName);
        if (factories == null)
            return buttons;

        List<ButtonConfigFactory> list = factories.get(domain);
        if (list == null)
            return  buttons;

        for (ButtonConfigFactory fact : list)
        {
            if (fact.isAvailable(ti))
                buttons.add(fact);
        }

        return Collections.unmodifiableList(buttons);
    }

    public TableCustomizer getLaboratoryTableCustomizer()
    {
        return new LaboratoryTableCustomizer();
    }

    public Set<DemographicsSource> getDemographicsSources(Container c, User u) throws IllegalArgumentException
    {
        Set<DemographicsSource> qds = new HashSet<DemographicsSource>();

        Container target = c.isWorkbookOrTab() ? c.getParent() : c;
        Map<String, String> properties = PropertyManager.getProperties(target, DEMOGRAPHICS_PROPERTY_CATEGORY);
        for (String key : properties.keySet())
        {
            try
            {
                DemographicsSource source = DemographicsSource.getFromPropertyManager(target, u, key, properties.get(key));
                if (source != null)
                    qds.add(source);
            }
            catch (IllegalArgumentException e)
            {
                _log.error("Invalid stored demographics source from container: " + c.getPath(), e);
            }
        }

        return qds;
    }

    public void setDemographicsSources(Container c, User u, Set<DemographicsSource> sources) throws IllegalArgumentException
    {
        Container target = c.isWorkbookOrTab() ? c.getParent() : c;
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(target, DEMOGRAPHICS_PROPERTY_CATEGORY, true);
        props.clear();

        Set<String> labels = new HashSet<String>();
        for (DemographicsSource qd : sources)
        {
            String name = ColumnInfo.legalNameFromName(qd.getLabel());
            if (labels.contains(name))
                throw new IllegalArgumentException("All demographics sources must have unique names.  Duplicate was: " + name);


            labels.add(name);

            props.put(qd.getPropertyManagerKey(), qd.getPropertyManagerValue());
        }
        PropertyManager.saveProperties(props);
    }

    //enforce read permission silently.  expect the action to limit this to admins
    public Map<Container, Set<AdditionalDataSource>> getAllAdditionalDataSources(User u) throws IllegalArgumentException
    {
        Map<Container, Set<AdditionalDataSource>> map = new HashMap<Container, Set<AdditionalDataSource>>();
        PropertyManager.PropertyEntry[] entries = PropertyManager.findPropertyEntries(null, null, DATASOURCE_PROPERTY_CATEGORY, null);
        for (PropertyManager.PropertyEntry entry : entries)
        {
            Container c = ContainerManager.getForId(entry.getObjectId());
            if (c == null || !c.hasPermission(u, ReadPermission.class))
                continue;

            Set<AdditionalDataSource> set = map.get(c);
            if (set == null)
                set = new HashSet<AdditionalDataSource>();

            AdditionalDataSource source = AdditionalDataSource.getFromPropertyManager(c, u, entry.getKey(), entry.getValue());
            if (source != null)
                set.add(source);

            if (set.size() > 0)
                map.put(c, set);
        }

        return Collections.unmodifiableMap(map);
    }

    //enforce read permission silently.  expect the action to limit this to admins
    public Map<Container, Set<DemographicsSource>> getAllDemographicsSources(User u) throws IllegalArgumentException
    {
        Map<Container, Set<DemographicsSource>> map = new HashMap<Container, Set<DemographicsSource>>();
        PropertyManager.PropertyEntry[] entries = PropertyManager.findPropertyEntries(null, null, DEMOGRAPHICS_PROPERTY_CATEGORY, null);
        for (PropertyManager.PropertyEntry entry : entries)
        {
            Container c = ContainerManager.getForId(entry.getObjectId());
            if (c == null || !c.hasPermission(u, ReadPermission.class))
                continue;

            Set<DemographicsSource> set = map.get(c);
            if (set == null)
                set = new HashSet<DemographicsSource>();

            DemographicsSource source = DemographicsSource.getFromPropertyManager(c, u, entry.getKey(), entry.getValue());
            if (source != null)
                set.add(source);

            if (set.size() > 0)
                map.put(c, set);
        }

        return Collections.unmodifiableMap(map);
    }

    public Set<URLDataSource> getURLDataSources(Container c, User u) throws IllegalArgumentException
    {
        Set<URLDataSource> qds = new HashSet<URLDataSource>();

        Container target = c.isWorkbookOrTab() ? c.getParent() : c;
        Map<String, String> properties = PropertyManager.getProperties(target, URL_DATASOURCE_PROPERTY_CATEGORY);
        for (String key : properties.keySet())
        {
            try
            {
                URLDataSource source = URLDataSource.getFromPropertyManager(c, key, properties.get(key));
                if (source != null)
                    qds.add(source);
            }
            catch (IllegalArgumentException e)
            {
                _log.error("Invalid stored URL data source from container: " + c.getPath(), e);
            }
        }

        return qds;
    }

    public Set<AdditionalDataSource> getAdditionalDataSources(Container c, User u) throws IllegalArgumentException
    {
        Set<AdditionalDataSource> qds = new HashSet<AdditionalDataSource>();

        Container target = c.isWorkbookOrTab() ? c.getParent() : c;
        Map<String, String> properties = PropertyManager.getProperties(target, DATASOURCE_PROPERTY_CATEGORY);
        for (String key : properties.keySet())
        {
            try
            {
                AdditionalDataSource source = AdditionalDataSource.getFromPropertyManager(target, u, key, properties.get(key));
                if (source != null)
                    qds.add(source);
            }
            catch (IllegalArgumentException e)
            {
                _log.error("Invalid stored data source from container: " + c.getPath(), e);
            }
        }

        return qds;
    }

    public void setURLDataSources(Container c, User u, Set<URLDataSource> sources)
    {
        Container cc = c.isWorkbookOrTab() ? c.getParent() : c;
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(cc, URL_DATASOURCE_PROPERTY_CATEGORY, true);
        props.clear();

        for (URLDataSource qd : sources)
        {
            props.put(qd.getPropertyManagerKey(), qd.getPropertyManagerValue());
        }
        PropertyManager.saveProperties(props);
    }

    public void setAdditionalDataSources(Container c, User u, Set<AdditionalDataSource> sources)
    {
        Container cc = c.isWorkbookOrTab() ? c.getParent() : c;
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(cc, DATASOURCE_PROPERTY_CATEGORY, true);
        props.clear();

        for (AdditionalDataSource qd : sources)
        {
            props.put(qd.getPropertyManagerKey(), qd.getPropertyManagerValue());
        }
        PropertyManager.saveProperties(props);
    }

    public Set<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        Set<TabbedReportItem> items = new HashSet<TabbedReportItem>();
        for (DataProvider dp : getDataProviders())
        {
            for (TabbedReportItem item : dp.getTabbedReportItems(c, u))
            {
                items.add(item);
            }
        }
        return items;
    }
}
