/*
 * Copyright (c) 2020 LabKey Corporation
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

package org.labkey.discvrcore;

public class DiscvrCoreManager
{
    private static final DiscvrCoreManager _instance = new DiscvrCoreManager();

    private DiscvrCoreManager()
    {
        // prevent external construction with a private default constructor
    }

    public static DiscvrCoreManager get()
    {
        return _instance;
    }
}