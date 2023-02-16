/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.sequenceanalysis;

import java.io.File;

/**
 * Created by bimber on 7/28/2014.
 */
public interface ReferenceLibraryHelper
{
    File getReferenceFasta();

    File getIdKeyFile();

    File getFastaIndexFile(boolean createIfDoesntExist);

    File getSequenceDictionaryFile(boolean createIfDoesntExist);

    Integer resolveSequenceId(String refName);

    String resolveAccession(String refName);
}
