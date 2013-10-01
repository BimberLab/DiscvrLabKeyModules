/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.exp.ExperimentRunType;
import org.labkey.api.exp.ExperimentRunTypeSource;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.WebPartFactory;
import org.labkey.sequenceanalysis.analysis.BamIterator;
import org.labkey.sequenceanalysis.util.Barcoder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class SequenceAnalysisModule extends ExtendedSimpleModule
{
    public static final String NAME = "SequenceAnalysis";
    public static final String CONTROLLER_NAME = "sequenceanalysis";
    public static final String PROTOCOL = "Sequence Analysis";
    public static final ExperimentRunType EXP_RUN_TYPE = new SequenceAnalysisExperimentRunType();

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 12.271;
    }

    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    protected void init()
    {
        addController("sequenceanalysis", SequenceAnalysisController.class);
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        SequenceAnalysisManager sm = SequenceAnalysisManager.get();
        Collection<String> list = new LinkedList<>();

        int runCount = sm.getRunCount(c);
        int readsetCount = sm.getReadsetCount(c);

        if (readsetCount > 0)
            list.add(readsetCount + " Sequence Readsets");

        if (runCount > 0)
            list.add(runCount + " Sequence Analysis Runs");

        return list;
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set("sequenceanalysis");
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(SequenceAnalysisSchema.getInstance().getSchema());
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {
        LaboratoryService.get().registerDataProvider(new SequenceDataProvider(this));

        ExperimentService.get().registerExperimentRunTypeSource(new ExperimentRunTypeSource()
        {
            public Set<ExperimentRunType> getExperimentRunTypes(Container container)
            {
            if (container.getActiveModules().contains(SequenceAnalysisModule.this))
            {
                return Collections.singleton(EXP_RUN_TYPE);
            }
            return Collections.emptySet();
            }
        });

        DetailsURL details = DetailsURL.fromString("/sequenceAnalysis/siteAdmin.view");
        details.setContainerContext(ContainerManager.getRoot());
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Management, "sequence analysis module admin", details.getActionURL());
    }

    @Override
    protected void registerContainerListeners()
    {
        // add a container listener so we'll know when our container is deleted.  override the default to ensure correct order
        ContainerManager.addContainerListener(new SequenceAnalysisContainerListener());
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        @SuppressWarnings({"unchecked"})
        Set<Class> testClasses = new HashSet<Class>(Arrays.asList(
            Barcoder.TestCase.class,
            BamIterator.TestCase.class,
            TestHelper.SequenceImportPipelineTestCase.class,
            TestHelper.SequenceAnalysisPipelineTestCase.class
        ));

        return testClasses;
    }
}