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

package org.labkey.omerointegration;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class OmeroIntegrationManager
{
    private static final OmeroIntegrationManager _instance = new OmeroIntegrationManager();

    public final static String CONFIG_PROPERTY_DOMAIN = "org.labkey.omerointegration.settings";
    public final static String OMERO_URL = "omeroUrl";
    public final static String OMERO_USERNAME = "omeroUserName";
    public final static String OMERO_PASSWORD = "omeroPassword";

    private OmeroIntegrationManager()
    {
        // prevent external construction with a private default constructor
    }

    public static OmeroIntegrationManager get()
    {
        return _instance;
    }

    public void saveSettings(Container c, Map<String, String> props) throws IllegalArgumentException
    {
        PropertyManager.PropertyMap configMap = PropertyManager.getEncryptedStore().getWritableProperties(c, CONFIG_PROPERTY_DOMAIN, true);

        String omeroUrl = StringUtils.trimToNull(props.get(OMERO_URL));
        if (omeroUrl != null)
        {
            try
            {
                URL url = new URL(omeroUrl);
            }
            catch (MalformedURLException e)
            {
                throw new IllegalArgumentException("Invalid URL: " + e.getMessage());
            }
        }
        configMap.put(OMERO_URL, omeroUrl);

        configMap.put(OMERO_USERNAME, StringUtils.trimToNull(props.get(OMERO_USERNAME)));
        configMap.put(OMERO_PASSWORD, StringUtils.trimToNull(props.get(OMERO_PASSWORD)));

        configMap.save();
    }
}