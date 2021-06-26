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
import org.junit.experimental.categories.Category;
import org.labkey.serverapi.reader.Readers;
import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.Locators;
import org.labkey.test.TestFileUtils;
import org.labkey.test.TestTimeoutException;
import org.labkey.test.categories.External;
import org.labkey.test.categories.LabModule;
import org.labkey.test.components.ext4.Window;
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

import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.interactions.Actions;

/**
 * Created by bimber on 1/20/2015.
 */

//disabled until we have a solution to install the jbrowse scripts on team city
@Category({External.class, LabModule.class})
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
        testDemoNoSession();
        testDemoUi();
        //testOutputFileProcessing();
    }

    private void testDemoNoSession()
    {
        beginAt("/home/jbrowse-jbrowse.view?");
        waitForElement(Locator.tagWithText("p", "Error - no session provided."));
    }

    private void testDemoUi()
    {
        beginAt("/home/jbrowse-jbrowse.view?session=demo");

        waitAndClick(Locator.xpath("//*[text() = 'Open track selector']/.."));
        waitAndClick(Locator.xpath("//*[text() = 'ClinVar variants (NCBI)']")); // Display the relevant variants
        Locator.css("body").findElement(getDriver()).sendKeys(Keys.ESCAPE); // exit out of our modal
        while (isTextPresent("Loading")){ // wait for loading to finish up
            sleep(10);
        }
        waitAndClick(Locator.xpath("//span[text()='ClinVar variants (NCBI)']/../button[2]")); // three dots
        waitAndClick(Locator.xpath("//span[text()='WidgetDisplay']")); // WidgetDisplay option
        Actions actions = new Actions(getDriver());
        WebElement toClick = getDriver().findElement(By.xpath("//*[name()='text' and contains(text(), '294665')]/..")); // 294665 is a visible element given minimalSession's location
        actions.click(toClick).perform();
        assertTextPresent("Hello"); // Check our modal displayed correctly

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

        //create genome and add resources
        /*Integer genomeId = SequenceTest.createReferenceGenome(this, _completedPipelineJobs);
        _completedPipelineJobs++;  //keep track of pipeline jobs

        _completedPipelineJobs = SequenceTest.addReferenceGenomeTracks(this, getProjectName(), SequenceTest.TEST_GENOME_NAME, genomeId, _completedPipelineJobs);
    */
    }

    @Override
    public List<String> getAssociatedModules()
    {
        return Arrays.asList("jbrowse");
    }

//    private void testOutputFileProcessing() throws Exception
//    {
//        goToProjectHome();
//
//        //import BAM, VCF, BED, GFF
//        File dataDir = TestFileUtils.getSampleData("sequenceAnalysis/genomeAnnotations");
//        for (File f : dataDir.listFiles())
//        {
//            SequenceTest.addOutputFile(this, f, SequenceTest.TEST_GENOME_NAME, f.getName(), "Gene Annotations", "This is an output file", false);
//        }
//
//        File testBam = new File(SequenceTest._sampleData, "test.bam");
//        SequenceTest.addOutputFile(this, testBam, SequenceTest.TEST_GENOME_NAME, "TestBAM", "BAM File", "This is an output file", false);
//
//        //create session w/ some of these, verify
//        log("creating initial jbrowse session");
//        beginAt("/sequenceanalysis/" + getContainerId() + "/begin.view");
//        waitAndClickAndWait(LabModuleHelper.getNavPanelItem("Output Files:", null));
//
//        DataRegionTable dr = new DataRegionTable("query", this);
//        dr.uncheckAll();
//        dr.checkCheckbox(0);
//        dr.clickHeaderMenu("More Actions", false, "View In JBrowse");
//
//        String sessionName = "TestSession1";
//        new Window.WindowFinder(getDriver()).withTitle("Create/Modify JBrowse Session").waitFor();
//        Ext4FieldRef.getForLabel(this, "Name").setValue(sessionName);
//        Ext4FieldRef.getForLabel(this, "Description").setValue("This is the first session");
//        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
//        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
//        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
//        _completedPipelineJobs++;
//        waitForPipelineJobsToComplete(_completedPipelineJobs, "Create New Session", false);
//
//        //add additional file to session, verify
//        //TODO: first cache timestamps on JSON resources.  we expect these to be untouched
//
//        beginAt("/sequenceanalysis/" + getContainerId() + "/begin.view");
//        waitAndClickAndWait(LabModuleHelper.getNavPanelItem("Output Files:", null));
//
//        dr = new DataRegionTable("query", this);
//        dr.uncheckAll();
//        dr.checkCheckbox(1);
//        dr.checkCheckbox(2);
//        dr.clickHeaderMenu("More Actions", false, "View In JBrowse");
//        new Window.WindowFinder(getDriver()).withTitle("Create/Modify JBrowse Session").waitFor();
//        Ext4FieldRef.getForBoxLabel(this, "Add To Existing Session").setChecked(true);
//        Ext4FieldRef.waitForField(this, "Session");
//        Ext4ComboRef.getForLabel(this, "Session").setComboByDisplayValue(sessionName);
//        waitAndClick(Ext4Helper.Locators.ext4ButtonEnabled("Submit"));
//        new Window.WindowFinder(getDriver()).withTitle("Success").waitFor();
//        waitAndClickAndWait(Ext4Helper.Locators.ext4ButtonEnabled("OK"));
//        _completedPipelineJobs++;
//        waitForPipelineJobsToComplete(_completedPipelineJobs, "Add To Existing Session", false);
//
//        //TODO: reprocess one of these JSONFiles.  make sure original files are deleted + session reprocessed
//        beginAt("/sequenceanalysis/" + getContainerId() + "/begin.view");
//    }


}
