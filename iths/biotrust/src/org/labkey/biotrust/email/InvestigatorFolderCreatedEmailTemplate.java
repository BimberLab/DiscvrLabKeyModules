/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.biotrust.email;

import org.labkey.api.data.Container;

/**
 * User: klum
 * Date: 8/25/13
 */
public class InvestigatorFolderCreatedEmailTemplate extends BioTrustEmailTemplate
{
    public static final String NAME = "NWBioTrust Investigator Folder Created";
    public static final String SUBJECT = "A NWBioTrust Investigator folder has been created";
    public static final String DESCRIPTION = "Sent when a investigator successfully creates their folder.";
    public static final String BODY = "A NWBioTrust investigator folder has been created by user : ^investigatorEmail^ at location : ^folderName^.\n\n";

    private String _investigatorEmail;
    private String _folderName;

    public InvestigatorFolderCreatedEmailTemplate()
    {
        super(NAME, SUBJECT, BODY, DESCRIPTION);

        _replacements.add(new ReplacementParam<String>("investigatorEmail", String.class, "The investigator that created the new folder."){
            @Override
            public String getValue(Container c)
            {
                return null == _investigatorEmail ? "" : _investigatorEmail;
            }
        });
        _replacements.add(new ReplacementParam<String>("folderName", String.class, "The name of the folder that was created."){
            @Override
            public String getValue(Container c)
            {
                return null == _folderName ? "" : _folderName;
            }
        });
    }

    public String getInvestigatorEmail()
    {
        return _investigatorEmail;
    }

    public void setInvestigatorEmail(String investigatorEmail)
    {
        _investigatorEmail = investigatorEmail;
    }

    public String getFolderName()
    {
        return _folderName;
    }

    public void setFolderName(String folderName)
    {
        _folderName = folderName;
    }
}
