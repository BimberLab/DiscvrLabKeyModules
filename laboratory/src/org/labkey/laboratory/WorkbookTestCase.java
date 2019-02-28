package org.labkey.laboratory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkbookTestCase extends WorkbookContainerType.AbstractTestCase
{
    private static final String PROJECT_NAME = "WorkbookIntegrationTest2";

    @Before
    public void setUp() throws Exception
    {
        _context = TestContext.get();
        doInitialSetUp(PROJECT_NAME);
    }

    @Test
    public void testCrossContainerBehaviorsForSimpleSchema() throws Exception
    {
        Module module = ModuleLoader.getInstance().getModule(LaboratoryModule.class);
        if (module != null)
        {
            Set<Module> active = new HashSet<>(_project.getActiveModules());
            active.add(module);
            _project.setActiveModules(active);

            //set values for required fields
            Map<String, Object> extraRowValues = new HashMap<>();
            extraRowValues.put("location", "DummyLocation");

            //the ContainerListener will populate default rows.  clear them:
            TableInfo ti = QueryService.get().getUserSchema(_context.getUser(), _project, LaboratoryModule.SCHEMA_NAME).getTable(LaboratorySchema.TABLE_SAMPLE_TYPE);
            List<Map<String, Object>> toDelete = new ArrayList<>();
            new TableSelector(ti, PageFlowUtil.set("rowId")).forEachResults(rs -> {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                row.put("rowId", rs.getInt(FieldKey.fromString("rowId")));
                toDelete.add(row);
            });
            ti.getUpdateService().deleteRows(_context.getUser(), _project, toDelete, Collections.emptyMap(), null);

            //set a mock request so QUS calls use our container instead of root
            try (ViewContext.StackResetter viewContextResetter = ViewContext.pushMockViewContext(_context.getUser(), _project, new ActionURL("dummy", "dummy", _project)))
            {
                testCrossContainerBehaviors(_project, _workbooks, LaboratoryModule.SCHEMA_NAME, LaboratorySchema.TABLE_SAMPLE_TYPE, LaboratorySchema.TABLE_SAMPLES, "samplename", "sampletype", Arrays.asList("Value1", "Value2", "Value3", "Value4"), extraRowValues);
            }
        }
    }

    @After
    public void onComplete()
    {
        doCleanup(PROJECT_NAME);
    }
}
