package org.labkey.laboratory;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 4/3/13
 * Time: 6:41 PM
 */
public class URLDataSource
{
    private LaboratoryService.NavItemCategory _itemType;
    private String _label;
    private StringExpression _url;

    protected static final String DELIM = "<>";

    private static final Logger _log = Logger.getLogger(URLDataSource.class);

    private URLDataSource(LaboratoryService.NavItemCategory itemType, String label, StringExpression url)
    {
        _itemType = itemType;
        _label = label;
        _url = url;
    }

    public static URLDataSource getFromParts(String itemType, String label, String urlExpression)
    {
        LaboratoryService.NavItemCategory cat = LaboratoryService.NavItemCategory.valueOf(itemType);

        if (urlExpression == null)
            throw new IllegalArgumentException("Stored URL Source lacks a URL");

        String msg = StringExpressionFactory.validateURL(urlExpression);
        if (msg != null)
            throw new IllegalArgumentException("Invalid URL: " + msg);

        StringExpression url = StringExpressionFactory.createURL(urlExpression);

        return new URLDataSource(cat, label, url);
    }

    public LaboratoryService.NavItemCategory getItemType()
    {
        return _itemType;
    }

    public String getLabel()
    {
        return _label;
    }

    public StringExpression getUrl()
    {
        return _url;
    }

    public static URLDataSource getFromPropertyManager(Container c, String key, String value) throws IllegalArgumentException
    {
        if (value == null)
            return null;

        try
        {
            JSONObject json = new JSONObject(value);
            String itemType = json.getString("itemType");
            String label = json.getString("label");
            String url = json.getString("urlExpression");

            return URLDataSource.getFromParts(itemType, label, url);
        }
        catch (JSONException e)
        {
            _log.error("Malformed URL data source saved in " + c.getPath() + ": " + e.getMessage() + ".  was: " + value);
            return null;
        }
    }

    public String getURLString(Container c)
    {
        String url;
        if (_url instanceof DetailsURL)
            return  ((DetailsURL) _url).copy(c).getActionURL().toString();
        else
            return _url.toString();
    }

    public JSONObject toJSON(Container c)
    {
        JSONObject json = new JSONObject();
        json.put("itemType", _itemType.name());
        json.put("label", _label);
        json.put("urlExpression", _url);
        json.put("url", getURLString(c));
        return json;
    }

    public String getPropertyManagerKey()
    {
        return getForKey(_itemType.name()) + DELIM + getForKey(getLabel()) + DELIM + getForKey(_url.toString());
    }

    protected String getForKey(String input)
    {
        return input == null ? "" : input;
    }

    public String getPropertyManagerValue()
    {
        JSONObject json = new JSONObject();
        json.put("itemType", _itemType.name());
        json.put("label", _label);
        json.put("urlExpression", _url.toString());

        return json.toString();
    }
}
