package org.labkey.studies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.ldk.ExtendedSimpleModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.studies.StudiesService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.studies.query.StudiesUserSchema;
import org.labkey.api.studies.security.StudiesDataAdminRole;
import org.labkey.studies.query.StudiesUserSchema;
import org.labkey.studies.study.StudiesFilterProvider;
import org.labkey.studies.study.StudyEnrollmentEventProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class StudiesModule extends ExtendedSimpleModule
{
    public static final String NAME = "Studies";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 23.005;
    }

    @Override
    protected void init()
    {
        addController(StudiesController.NAME, StudiesController.class);

        StudiesService.setInstance(StudiesServiceImpl.get());
        StudiesService.get().registerEventProvider(new StudyEnrollmentEventProvider());
        RoleManager.registerRole(new StudiesDataAdminRole());
        LaboratoryService.get().registerTabbedReportFilterProvider(new StudiesFilterProvider());
    }

    @Override
    public void doStartupAfterSpringConfig(ModuleContext moduleContext)
    {

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
        return Collections.singleton(StudiesSchema.NAME);
    }

    @Override
    public void registerSchemas()
    {
        DefaultSchema.registerProvider(StudiesSchema.NAME, new DefaultSchema.SchemaProvider(this)
        {
            @Override
            public QuerySchema createSchema(final DefaultSchema schema, Module module)
            {
                return new StudiesUserSchema(schema.getUser(), schema.getContainer(), StudiesSchema.getInstance().getSchema());
            }
        });
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return PageFlowUtil.set(
                StudiesManager.TestCase.class
        );
    }
}