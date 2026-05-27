package org.labkey.studies;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.discvrcore.test.AbstractIntegrationTest;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.studies.study.StudyDefinition;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.IntegerUtils.asInteger;

@RunWith(Enclosed.class)
public class StudiesManager
{
    private static final StudiesManager _instance = new StudiesManager();

    private StudiesManager()
    {

    }

    public static StudiesManager get()
    {
        return _instance;
    }

    public StudyDefinition insertOrUpdateStudyDefinition(StudyDefinition sd, Container c, User u)
    {
        StudiesSchema ss = StudiesSchema.getInstance();
        DbSchema schema = ss.getSchema();
        DbScope scope   = schema.getScope();

        UserSchema us = QueryService.get().getUserSchema(u, c, StudiesSchema.NAME);

        TableInfo tblStudies       = us.getTable(StudiesSchema.TABLE_STUDIES);
        TableInfo tblCohorts       = us.getTable(StudiesSchema.TABLE_COHORTS);
        TableInfo tblAnchorEvents  = us.getTable(StudiesSchema.TABLE_ANCHOR_EVENTS);
        TableInfo tblTimepoints    = us.getTable(StudiesSchema.TABLE_EXPECTED_TIMEPOINTS);

        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            sd.setContainer(c.getEntityId().toString());
            sd = upsertStudy(sd, tblStudies, c, u);

            upsertChildRecords(
                    sd.getRowId(),
                    sd.getCohorts(),
                    tblCohorts,
                    c,
                    u,
                    this::cohortToMap,
                    StudyDefinition.StudyCohort::getRowId,
                    StudyDefinition.StudyCohort::setRowId
            );

            upsertChildRecords(
                    sd.getRowId(),
                    sd.getAnchorEvents(),
                    tblAnchorEvents,
                    c,
                    u,
                    this::anchorToMap,
                    StudyDefinition.AnchorEvent::getRowId,
                    StudyDefinition.AnchorEvent::setRowId
            );

            upsertChildRecords(
                    sd.getRowId(),
                    sd.getTimepoints(),
                    tblTimepoints,
                    c,
                    u,
                    this::timepointToMap,
                    StudyDefinition.Timepoint::getRowId,
                    StudyDefinition.Timepoint::setRowId
            );

            tx.commit();
        }
        catch (Exception x)
        {
            throw new RuntimeException("Failed to up‑sert StudyDefinition", x);
        }

        return sd;
    }

    private StudyDefinition upsertStudy(StudyDefinition sd,
                                        TableInfo tbl,
                                        Container c,
                                        User u) throws Exception
    {
        Map<String,Object> map  = studyToMap(sd);
        BatchValidationException bve = new BatchValidationException();
        QueryUpdateService qus  = tbl.getUpdateService();

        List<Map<String,Object>> rows  = List.of(map);
        List<Map<String,Object>> ret;

        if (sd.getRowId() == null)
            ret = qus.insertRows(u, c, rows, bve, null, null);
        else
        {
            ret = qus.updateRows(u, c, rows, null, bve, null, null);
        }

        if (bve.hasErrors())
            throw bve;

        sd.setRowId(asInteger(ret.get(0).get("rowId")));
        return sd;
    }


    private <T> void upsertChildRecords(int studyRowId,
                                        List<T> incoming,
                                        TableInfo tbl,
                                        Container c,
                                        User u,
                                        Mapper<T> mapper,
                                        RowIdGetter<T> getRowId,
                                        RowIdSetter<T> setRowId) throws Exception
    {
        QueryUpdateService qus = tbl.getUpdateService();

        Set<Integer> existing = new HashSet<>(
                new TableSelector(tbl, PageFlowUtil.set("rowId"),
                        new SimpleFilter(FieldKey.fromString("studyId"), studyRowId),
                        null).getCollection(Integer.class)
        );

        List<Map<String,Object>> inserts = new ArrayList<>();
        List<T>                 insertBeans = new ArrayList<>();
        List<Map<String,Object>> updates = new ArrayList<>();

        for (T bean : incoming)
        {
            Map<String,Object> row = mapper.apply(bean);
            row.put("studyId", studyRowId);

            Integer rk = getRowId.get(bean);
            if (rk == null)
            {
                inserts.add(row);
                insertBeans.add(bean);
            }
            else
            {
                updates.add(row);
                existing.remove(rk);
            }
        }

        if (!existing.isEmpty())
        {
            List<Map<String,Object>> keys = existing.stream()
                    .map(rid -> Map.<String,Object>of("rowid", (Object) rid))
                    .toList();

            qus.deleteRows(u, c, keys, null, null);
        }

        BatchValidationException bve = new BatchValidationException();

        if (!inserts.isEmpty())
        {
            List<Map<String,Object>> ret = qus.insertRows(u, c, inserts, bve, null, null);
            for (int i = 0; i < ret.size(); i++)
                setRowId.set(insertBeans.get(i), asInteger(ret.get(i).get("rowId")));
        }

        if (!updates.isEmpty())
        {
            qus.updateRows(u, c, updates, null, bve, null, null);
        }

        if (bve.hasErrors())
            throw bve;
    }

    private Map<String,Object> studyToMap(StudyDefinition s)
    {
        Map<String,Object> m = new HashMap<>();
        if (s.getRowId() != null)
            m.put("rowId", s.getRowId());
        m.put("studyName",        s.getStudyName());
        m.put("label",       s.getLabel());
        m.put("category",    s.getCategory());
        m.put("description", s.getDescription());
        m.put("container",   s.getContainer());
        return m;
    }

    private Map<String,Object> cohortToMap(StudyDefinition.StudyCohort c)
    {
        Map<String,Object> m = new HashMap<>();
        if (c.getRowId() != null)
            m.put("rowId", c.getRowId());
        m.put("cohortName",    c.getCohortName());
        m.put("label",         c.getLabel());
        m.put("category",      c.getCategory());
        m.put("description",   c.getDescription());
        m.put("isControlGroup",c.getIsControlGroup());
        m.put("sortOrder",     c.getSortOrder());
        m.put("container",     c.getContainer());
        return m;
    }

    private Map<String,Object> anchorToMap(StudyDefinition.AnchorEvent a)
    {
        Map<String,Object> m = new HashMap<>();
        if (a.getRowId() != null)
            m.put("rowId", a.getRowId());
        m.put("label",            a.getLabel());
        m.put("description",      a.getDescription());
        m.put("eventProviderName",a.getEventProviderName());
        m.put("container",        a.getContainer());
        return m;
    }

    private Map<String,Object> timepointToMap(StudyDefinition.Timepoint t)
    {
        Map<String,Object> m = new HashMap<>();
        if (t.getRowId() != null)
            m.put("rowId", t.getRowId());
        m.put("cohortId",    t.getCohortId());
        m.put("cohortName",  t.getCohortName());
        m.put("label",       t.getLabel());
        m.put("labelShort",  t.getLabelShort());
        m.put("description", t.getDescription());
        m.put("anchorEvent", t.getAnchorEvent());
        m.put("rangeMin",    t.getRangeMin());
        m.put("rangeMax",    t.getRangeMax());
        m.put("container",   t.getContainer());
        return m;
    }

    @FunctionalInterface private interface Mapper<T>         { Map<String,Object> apply(T t); }
    @FunctionalInterface private interface RowIdGetter<T>    { Integer get(T t); }
    @FunctionalInterface private interface RowIdSetter<T>    { void    set(T t, Integer id); }

    public static class TestCase extends AbstractIntegrationTest
    {
        public static final String PROJECT_NAME = "StudiesIntegrationTestFolder";

        @BeforeClass
        public static void setup() throws Exception
        {
            doInitialSetUp(PROJECT_NAME);

            Container project = ContainerManager.getForPath(PROJECT_NAME);
            Set<Module> active = new HashSet<>(project.getActiveModules());
            active.add(ModuleLoader.getInstance().getModule(StudiesModule.NAME));
            project.setActiveModules(active);
        }

        @AfterClass
        public static void cleanup()
        {
            doCleanup(PROJECT_NAME);
        }

        @Test
        public void testStudyInsert() throws Exception
        {
            Container c = ContainerManager.getForPath(PROJECT_NAME);
            Resource  r = ModuleLoader.getInstance()
                    .getModule(StudiesModule.NAME)
                    .getModuleResource("study/DemoStudy.json");

            if (!(r instanceof FileResource fr))
                throw new IllegalStateException("Expected a FileResource; got " + r);

            StudyDefinition sd;
            try (InputStream is = new FileInputStream(fr.getFile()))
            {
                String jsonTxt = IOUtils.toString(is, StringUtilsLabKey.DEFAULT_CHARSET);
                sd = StudyDefinition.fromJson(new JSONObject(jsonTxt));
            }

            sd = StudiesManager.get().insertOrUpdateStudyDefinition(sd, c, TestContext.get().getUser());

            // 1. Verify insert
            assertNotNull( "study rowId null after insert", sd.getRowId());
            sd.getCohorts().forEach(co -> assertNotNull("cohort rowId null", co.getRowId()));
            sd.getAnchorEvents().forEach(ev -> assertNotNull("anchor rowId null", ev.getRowId()));
            sd.getTimepoints().forEach(tp -> assertNotNull("timepoint rowId null", tp.getRowId()));

            StudiesSchema ss         = StudiesSchema.getInstance();
            DbSchema      schema     = ss.getSchema();
            TableInfo     tblStudies = schema.getTable(StudiesSchema.TABLE_STUDIES);
            TableInfo     tblCohorts = schema.getTable(StudiesSchema.TABLE_COHORTS);
            TableInfo     tblTP      = schema.getTable(StudiesSchema.TABLE_EXPECTED_TIMEPOINTS);

            assertEquals(1,
                    new TableSelector(tblStudies,
                            PageFlowUtil.set("rowId"),
                            new SimpleFilter(FieldKey.fromString("rowId"), sd.getRowId()),
                            null).getRowCount());

            int cohortCount    = sd.getCohorts().size();
            int timepointCount = sd.getTimepoints().size();

            assertEquals(cohortCount,
                    new TableSelector(tblCohorts,
                            PageFlowUtil.set("rowId"),
                            new SimpleFilter(FieldKey.fromString("studyId"), sd.getRowId()),
                            null).getRowCount());

            assertEquals(timepointCount,
                    new TableSelector(tblTP,
                            PageFlowUtil.set("rowId"),
                            new SimpleFilter(FieldKey.fromString("studyId"), sd.getRowId()),
                            null).getRowCount());

            // 2. Update some values, add a cohort, delete a timepoint
            sd.setLabel(sd.getLabel() + " (updated)");

            StudyDefinition.StudyCohort firstCohort = sd.getCohorts().get(0);
            firstCohort.setLabel(firstCohort.getLabel() + "-updated");

            StudyDefinition.StudyCohort newCohort = new StudyDefinition.StudyCohort();
            newCohort.setCohortName("NEW");
            newCohort.setLabel("Brand-new cohort");
            sd.getCohorts().add(newCohort);

            StudyDefinition.Timepoint removedTp = sd.getTimepoints().remove(0);

            sd = StudiesManager.get().insertOrUpdateStudyDefinition(sd, c, TestContext.get().getUser());

            assertNotNull("new cohort did not receive rowId", newCohort.getRowId());

            assertEquals(cohortCount + 1,
                    new TableSelector(tblCohorts,
                            PageFlowUtil.set("rowId"),
                            new SimpleFilter(FieldKey.fromString("studyId"), sd.getRowId()),
                            null).getRowCount());

            assertEquals(timepointCount - 1,
                    new TableSelector(tblTP,
                            PageFlowUtil.set("rowId"),
                            new SimpleFilter(FieldKey.fromString("studyId"), sd.getRowId()),
                            null).getRowCount());

            String dbLabel = new TableSelector(tblStudies,
                    PageFlowUtil.set("label"),
                    new SimpleFilter(FieldKey.fromString("rowId"), sd.getRowId()),
                    null).getObject(String.class);
            assertEquals(sd.getLabel(), dbLabel);

            String dbCohortLabel = new TableSelector(tblCohorts,
                    PageFlowUtil.set("label"),
                    new SimpleFilter(FieldKey.fromString("rowId"), firstCohort.getRowId()),
                    null).getObject(String.class);
            assertEquals(firstCohort.getLabel(), dbCohortLabel);

            // 3. Delete the new cohort
            sd.getCohorts().remove(newCohort);
            sd = StudiesManager.get().insertOrUpdateStudyDefinition(sd, c, TestContext.get().getUser());

            assertEquals(cohortCount,
                    new TableSelector(tblCohorts,
                            PageFlowUtil.set("rowId"),
                            new SimpleFilter(FieldKey.fromString("studyId"), sd.getRowId()),
                            null).getRowCount());

            // 4. Round-trip JSON export
            JSONObject roundTrip = new JSONObject(sd.toJson());
            assertEquals(sd.getLabel(), roundTrip.getString("label"));
            assertEquals(sd.getCohorts().size(), roundTrip.getJSONArray("cohorts").length());
            assertEquals(sd.getTimepoints().size(), roundTrip.getJSONArray("timepoints").length());
        }
    }
}