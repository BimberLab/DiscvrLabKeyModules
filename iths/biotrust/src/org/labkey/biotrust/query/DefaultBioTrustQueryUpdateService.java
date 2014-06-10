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
package org.labkey.biotrust.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.security.User;
import org.labkey.biotrust.BioTrustSampleManager;
import org.labkey.biotrust.model.SampleRequest;
import org.labkey.biotrust.security.EditRequestsPermission;

import java.util.Collections;
import java.util.Set;

/**
 * User: cnathe
 * Date: 3/15/13
 */
public class DefaultBioTrustQueryUpdateService extends DefaultQueryUpdateService
{
    public DefaultBioTrustQueryUpdateService(TableInfo queryTable, TableInfo dbTable)
    {
        super(queryTable, dbTable);
    }

    protected void checkSampleRequest(Container c, User user, int sampleId) throws QueryUpdateServiceException
    {
        checkSampleRequest(c, user, sampleId, Collections.<String>emptySet());
    }

    protected void checkSampleRequest(Container c, User user, int sampleId, Set<String> rowUpdateKeys) throws QueryUpdateServiceException
    {
        SampleRequest sampleRequest = BioTrustSampleManager.get().getSampleRequest(c, user, sampleId);
        if (sampleRequest != null)
        {
            boolean submitted = BioTrustSampleManager.get().isSampleRequestSubmitted(c, user, sampleRequest);
            boolean locked = BioTrustSampleManager.get().isSampleRequestLocked(c, user, sampleRequest);

            // don't allow inserts to updates to sample requests that are locked (i.e. submitted and status of any tissue record is Approved, Closed, etc.)
            if (submitted && locked)
            {
                // CBR Issue 191: allow editing of select properties for locked requests
                if (!rowUpdateKeys.isEmpty())
                {
                    for (String key : rowUpdateKeys)
                    {
                        if (!"RowId".equalsIgnoreCase(key) && !BioTrustSampleManager.get().getAlwaysEditablePropertyNames().contains(key))
                            throw new QueryUpdateServiceException("Updates are not allowed for sample requests in a locked state.");
                    }
                }
                else
                {
                    throw new QueryUpdateServiceException("Updates are not allowed for sample requests in a locked state.");
                }
            }

            // restrict inserts/updating of pre-approved submitted sample requests
            if (submitted && !hasPermission(user, EditRequestsPermission.class))
            {
                throw new QueryUpdateServiceException("You do not have permissions to update a submitted sample request.");
            }
        }
    }
}