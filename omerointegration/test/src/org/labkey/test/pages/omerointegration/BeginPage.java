package org.labkey.test.pages.omerointegration;

import org.labkey.test.BaseWebDriverTest;
import org.labkey.test.Locator;
import org.labkey.test.WebTestHelper;
import org.labkey.test.components.ComponentElements;
import org.labkey.test.pages.LabKeyPage;
import org.openqa.selenium.SearchContext;

public class BeginPage extends LabKeyPage
{
    Elements _elements;

    public BeginPage(BaseWebDriverTest test)
    {
        super(test);
    }

    protected static String getController()
    {
        return "omerointegration";
    }

    protected static String getAction()
    {
        return "begin";
    }

    public static BeginPage beginAt(BaseWebDriverTest test, String containerPath)
    {
        test.beginAt(WebTestHelper.buildURL(getController(), containerPath, getAction()));
        return new BeginPage(test);
    }

    public Elements elements()
    {
        if (_elements == null)
            _elements = new Elements();
        return _elements;
    }

    private class Elements extends ComponentElements
    {
        @Override
        protected SearchContext getContext()
        {
            return getDriver();
        }
    }
}
