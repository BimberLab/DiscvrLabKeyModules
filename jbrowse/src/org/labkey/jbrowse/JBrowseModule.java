/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.jbrowse;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.jbrowse.JBrowseService;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.ldk.LDKService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.sequenceanalysis.SequenceAnalysisService;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavService;
import org.labkey.jbrowse.button.AddLibraryButton;
import org.labkey.jbrowse.button.AddTrackButton;
import org.labkey.jbrowse.button.ModifyTrackConfigButton;
import org.labkey.jbrowse.button.ReprocessResourcesButton;
import org.labkey.jbrowse.button.ReprocessSessionsButton;
import org.labkey.jbrowse.pipeline.JBrowseSessionPipelineProvider;
import org.labkey.jbrowse.query.JBrowseUserSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class JBrowseModule extends ExtendedSimpleModule
{
    public static final String NAME = "JBrowse";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Double getSchemaVersion()
    {
        return 13.39;
    }

    @Override
    protected void registerSchemas()
    {
        JBrowseUserSchema.register(this);
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController(JBrowseController.NAME, JBrowseController.class);
        JBrowseService.setInstance(JBrowseServiceImpl.get());
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        DetailsURL details = DetailsURL.fromString("/jbrowse/settings.view");
        details.setContainerContext(ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "jbrowse admin", details.getActionURL(), AdminOperationsPermission.class);

        LDKService.get().registerQueryButton(new AddTrackButton(), "sequenceanalysis", "reference_library_tracks");
        LDKService.get().registerQueryButton(new AddLibraryButton(), "sequenceanalysis", "reference_libraries");

        LDKService.get().registerQueryButton(new ReprocessResourcesButton(), JBrowseSchema.NAME, JBrowseSchema.TABLE_JSONFILES);
        LDKService.get().registerQueryButton(new ReprocessSessionsButton(), JBrowseSchema.NAME, JBrowseSchema.TABLE_DATABASES);
        LDKService.get().registerQueryButton(new ModifyTrackConfigButton(), JBrowseSchema.NAME, JBrowseSchema.TABLE_JSONFILES);

        LaboratoryService.get().registerDataProvider(new JBrowseDataProvider(this));
        SequenceAnalysisService.get().registerDataProvider(new JBrowseDataProvider(this));
        SystemMaintenance.addTask(new JBrowseMaintenanceTask());
        PipelineService.get().registerPipelineProvider(new JBrowseSessionPipelineProvider(this));

        SequenceAnalysisService.get().registerGenomeTrigger(new JBrowseGenomeTrigger());
        SequenceAnalysisService.get().registerFileHandler(new JBrowseSequenceOutputHandler());

        WebdavService.get().registerPreGzippedExtensions("jsonz");
        WebdavService.get().registerPreGzippedExtensions("txtz");
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(JBrowseSchema.NAME);
    }
}