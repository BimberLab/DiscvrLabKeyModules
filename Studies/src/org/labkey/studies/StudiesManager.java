package org.labkey.studies;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.discvrcore.test.AbstractIntegrationTest;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.studies.study.StudyDefinition;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

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
        // TODO: implement this. The goal is to have one entrypoint that takes the JSON and ensures DB records are accurate.
        // This should handle situations where a cohort or timepoint is removed.
        // Note that the idea of the JSONObject is to allow redundant nested values (like the studyId key on child records) to inherit from the parent.

        // return the modified StudyDefinition, which should include setting RowIds, if this is an import
        return sd;
    }

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

            Resource r = ModuleLoader.getInstance().getModule(StudiesModule.NAME).getModuleResource("study/DemoStudy.json");
            if (r instanceof FileResource fr)
            {
                try (InputStream is = new FileInputStream(fr.getFile()))
                {
                    String jsonTxt = IOUtils.toString(is, StringUtilsLabKey.DEFAULT_CHARSET);
                    JSONObject json = new JSONObject(jsonTxt);

                    StudyDefinition sd = StudyDefinition.fromJson(json);
                    sd = StudiesManager.get().insertOrUpdateStudyDefinition(sd, c, TestContext.get().getUser());

                    // TODO: ensure the sd now contains rowIds
                    // Make updates to values, repeat insertOrUpdateStudyDefinition.
                    // Verify values persisted

                    // Perform more updates, including deleting cohorts or timepoints, verify

                    // Finally export to JSON, verify
                }
            }
            else
            {
                throw new IllegalStateException("Expected a FileResource");
            }
        }
    }
}