/*
 * Copyright (c) 2012 LabKey Corporation
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

package org.labkey.GeneticsCore;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.Queryable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeneticsCoreManager
{
    private static final GeneticsCoreManager _instance = new GeneticsCoreManager();

    @Queryable
    public static final String DNA_DRAW_COLLECTED = "DNA Bank Blood Draw Collected";
    @Queryable
    public static final String DNA_DRAW_NEEDED = "DNA Bank Blood Draw Needed";
    @Queryable
    public static final String PARENTAGE_DRAW_COLLECTED = "Parentage Blood Draw Collected";
    @Queryable
    public static final String PARENTAGE_DRAW_NEEDED = "Parentage Blood Draw Needed";
    @Queryable
    public static final String MHC_DRAW_COLLECTED = "MHC Blood Draw Collected";
    @Queryable
    public static final String MHC_DRAW_NEEDED = "MHC Blood Draw Needed";

    private GeneticsCoreManager()
    {
        // prevent external construction with a private default constructor
    }

    public static GeneticsCoreManager get()
    {
        return _instance;
    }
}
