/*
 * Copyright (c) 2018 LabKey Corporation
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

package org.labkey.test.components.snprc_scheduler;

import org.labkey.test.Locator;
import org.labkey.test.components.BodyWebPart;
import org.labkey.test.components.html.Input;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.labkey.test.components.html.Input.Input;

/**
 * TODO: Component for a hypothetical webpart containing an input and a save button
 * Component classes should handle all timing and functionality for a component
 */
public class Snprc_schedulerWebPartZ extends BodyWebPart<Snprc_schedulerWebPartZ.ElementCache>
{
    public Snprc_schedulerWebPartZ(WebDriver driver)
    {
        this(driver, 0);
    }

    public Snprc_schedulerWebPartZ(WebDriver driver, int index)
    {
        super(driver, "Snprc_scheduler", index);
    }

    public Snprc_schedulerWebPartZ setInput(String value)
    {
        elementCache().input.set(value);
        // TODO: Methods that don't navigate should return this object
        return this;
    }

    public LabKeyPage clickSave()
    {
        getWrapper().clickAndWait(elementCache().button);
        // TODO: Methods that navigate should return an appropriate page object
        return new LabKeyPage(getDriver());
    }

    @Override
    protected ElementCache newElementCache()
    {
        return new ElementCache();
    }

    protected class ElementCache extends BodyWebPart.ElementCache
    {
        protected final WebElement button = Locator.tag("button").withText("Save").findWhenNeeded(this);
        protected final Input input = Input(Locator.tag("input"), getDriver()).findWhenNeeded(this);
    }
}