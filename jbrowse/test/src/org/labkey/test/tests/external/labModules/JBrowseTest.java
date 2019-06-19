/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.test.tests.external.labModules;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.labkey.serverapi.reader.Readers;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.util.DataRegionTable;
import org.labkey.test.util.Ext4Helper;
import org.labkey.test.util.ext4cmp.Ext4ComboRef;
import org.labkey.test.util.ext4cmp.Ext4FieldRef;
import org.labkey.test.util.external.labModules.LabModuleHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Created by bimber on 1/20/2015.
 */

//disabled until we have a solution to install the jbrowse scripts on team city
//@Category({External.class, LabModule.class})
public class JBrowseTest extends BaseWebDriverTest
{
    protected LabModuleHelper _helper = new LabModuleHelper(this);
    private int _completedPipelineJobs = 0;

    @Override
    protected String getProjectName()
    {
        return "JBrowseVerifyProject";
    }

    @Test
    public void testSteps() throws Exception
    {
        setUpTest();

        testOutputFileProcessing();
    }

    @Override
    protected void doCleanup(boolean afterTest) throws TestTimeoutException
    {
        super.doCleanup(afterTest);
    }

    protected void setUpTest() throws Exception
    {
        _containerHelper.createProject(getProjectName(), "Sequence Analysis");
        _containerHelper.enableModule("JBrowse");

        //TODO
        //download/setup jbrowse scripts
//        File scriptDir = Files.createTempDir();
//        downloadInstallScripts(scriptDir);
//
//        goToAdminConsole();
//        waitAndClickAndWait(Locator.linkContainingText("jbrowse admin"));
//        Ext4FieldRef.waitForField(this, "Script Directory");
//        Ext4FieldRef.getForLabel(this, "Script Directory").setValue(scriptDir.toString());
//        click(Ext4Helper.Locators.ext4ButtonEnabled("Save Settings"));
//        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));

        //create genome and add resources
        Integer genomeId = SequenceTest.createReferenceGenome(this, _completedPipelineJobs);
        _completedPipelineJobs++;  //keep track of pipeline jobs

        _completedPipelineJobs = SequenceTest.addReferenceGenomeTracks(this, getProjectName(), SequenceTest.TEST_GENOME_NAME, genomeId, _completedPipelineJobs);
    }

    private void downloadInstallScripts(File scriptDir) throws Exception
    {
        URL url = new URL("http://jbrowse.org/wordpress/wp-content/plugins/download-monitor/download.php?id=98");
        File zip = new File(scriptDir, "jbrowse.zip");

        try (InputStream is = url.openStream();BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(zip)))
        {
            IOUtils.copy(is, out);
        }

        unzipToDirectory(zip, scriptDir);
        zip.delete();

        //now attempt to run/install script:
        File unzipDir = new File(scriptDir, "JBrowse-1.11.5");
        if (SystemUtils.IS_OS_WINDOWS)
        {
            execute(Arrays.asList("perl", "bin\\cpanm", "--notest", "-l", "extlib\\", "--installdeps", "."), unzipDir);
        }
        else
        {
            //just run the shell script
            execute(Arrays.asList("setup.sh"), unzipDir);
        }
    }

    private void unzipToDirectory(File sourceZip, File unzipDir) throws IOException
    {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceZip));BufferedInputStream is = new BufferedInputStream(zis))
        {
            ZipEntry entry;
            while (null != (entry = zis.getNextEntry()))
            {
                if (entry.isDirectory())
                {
                    File newDir = new File(unzipDir, entry.getName());
                    newDir.mkdir();
                    continue;
                }

                //log("Expanding " + entry.getName());

                File destFile = new File(unzipDir, entry.getName());
                destFile.getParentFile().mkdirs();
                destFile.createNewFile();

                try (BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(destFile)))
                {
                    IOUtils.copy(is, os);
                    zis.closeEntry();
                }
            }
        }
    }

    public String execute(List<String> params, File workingDir) throws Exception
    {
        StringBuffer output = new StringBuffer();
        log("\t" + StringUtils.join(params, " "));

        ProcessBuilder pb = new ProcessBuilder(params);
        pb.environment().put("PATH", System.getenv("PATH"));
        pb.directory(workingDir);
        log("using working dir: " + workingDir.getPath());
        pb.redirectErrorStream(true);

        Process p = null;
        try
        {
            p = pb.start();

            try (BufferedReader procReader = Readers.getReader(p.getInputStream()))
            {
                String line;
                while ((line = procReader.readLine()) != null)
                {
                    output.append(line);
                    output.append(System.getProperty("line.separator"));

                    log(line);
                }
            }

            int returnCode = p.waitFor();
            if (returnCode != 0)
            {
                throw new Exception("process exited with non-zero value: " + returnCode);
            }
        }
        finally
        {
            if (p != null)
            {
                p.destroy();
            }
        }

        return output.toString();
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("jbrowse");
    }

    private void testOutputFileProcessing() throws Exception
    {
        goToProjectHome();

        //import BAM, VCF, BED, GFF
        File dataDir = TestFileUtils.getSampleData("sequenceAnalysis/genomeAnnotations");
        for (File f : dataDir.listFiles())
        {
            SequenceTest.addOutputFile(this, f, SequenceTest.TEST_GENOME_NAME, f.getName(), "Gene Annotations", "This is an output file", false);
        }

        File testBam = new File(TestFileUtils.getLabKeyRoot(), "/externalModules/labModules/SequenceAnalysis/resources/sampleData/test.bam");
        SequenceTest.addOutputFile(this, testBam, SequenceTest.TEST_GENOME_NAME, "TestBAM", "BAM File", "This is an output file", false);

        //create session w/ some of these, verify
        log("creating initial jbrowse session");
        beginAt("/sequenceanalysis/" + getContainerId() + "/begin.view");
        waitAndClickAndWait(LabModuleHelper.getNavPanelItem("Output Files:", null));

        DataRegionTable dr = new DataRegionTable("query", this);
        dr.uncheckAll();
        dr.checkCheckbox(0);
        dr.clickHeaderMenu("More Actions", false, "View In JBrowse");

        String sessionName = "TestSession1";
        waitForElement(Ext4Helper.Locators.window("Create/Modify JBrowse Session"));
        Ext4FieldRef.getForLabel(this, "Name").setValue(sessionName);
        Ext4FieldRef.getForLabel(this, "Description").setValue("This is the first session");
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        waitForElement(Ext4Helper.Locators.window("Success"));
        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        _completedPipelineJobs++;
        waitForPipelineJobsToComplete(_completedPipelineJobs, "Create New Session", false);

        //add additional file to session, verify
        //TODO: first cache timestamps on JSON resources.  we expect these to be untouched

        beginAt("/sequenceanalysis/" + getContainerId() + "/begin.view");
        waitAndClickAndWait(LabModuleHelper.getNavPanelItem("Output Files:", null));

        dr = new DataRegionTable("query", this);
        dr.uncheckAll();
        dr.checkCheckbox(1);
        dr.checkCheckbox(2);
        dr.clickHeaderMenu("More Actions", false, "View In JBrowse");
        waitForElement(Ext4Helper.Locators.window("Create/Modify JBrowse Session"));
        Ext4FieldRef.getForBoxLabel(this, "Add To Existing Session").setChecked(true);
        Ext4FieldRef.waitForField(this, "Session");
        Ext4ComboRef.getForLabel(this, "Session").setComboByDisplayValue(sessionName);
        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
        waitForElement(Ext4Helper.Locators.window("Success"));
        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
        _completedPipelineJobs++;
        waitForPipelineJobsToComplete(_completedPipelineJobs, "Add To Existing Session", false);

        //TODO: reprocess one of these JSONFiles.  make sure original files are deleted + session reprocessed
        beginAt("/sequenceanalysis/" + getContainerId() + "/begin.view");
    }
}
