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

package org.labkey.snprc_scheduler;

import org.labkey.api.data.Container;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

public class SNPRC_schedulerManager
{
    private static final SNPRC_schedulerManager _instance = new SNPRC_schedulerManager();

    private SNPRC_schedulerManager()
    {
        // prevent external construction with a private default constructor
    }

    public static UserSchema getSNPRC_schedulerUserSchema(Container c, User u)
    {
        return new SNPRC_schedulerUserSchema( u, c );
    }

    public static SNPRC_schedulerManager get()
    {
        return _instance;
    }


}