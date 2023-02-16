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
package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;

/**
 * Created by bimber on 9/15/2014.
 */
public interface ReferenceGenome extends Serializable
{
    /**
     * @return The FASTA file intended to be used during the pipeline, which is usually a copied version of an original FASTA or
     * a file created de novo by querying the sequence DB
     */
    //public @NotNull File getWorkingFastaFile();

    //public void setWorkingFasta(File workingFasta);

    /**
     * @return This is the original FASTA file usually created prior to the pipeline job.  This file may be copied to a working location
     * during the run.  The original FASTA may also have other resources cached in that directory, such as aligner indexes.
     * If this FASTA was created de novo during this run, it will exist on the webserver analysis directory for this job
     */
    @NotNull File getSourceFastaFile();

    /**
     * @return This is the file that should typically be used by callers.  The pipeline code usually copies this file to the local working directory.
     * If this has occurred, that file will preferentially be used.  Otherwise, the source FASTA file will be returned.
     */
    @NotNull File getWorkingFastaFile();

    /**
     * @return This is the file that should typically be used by callers.  The pipeline code usually copies this file to the local working directory.
     * If this has occurred, that file will preferentially be used.  Otherwise, the source FASTA file will be returned.
     */
    @NotNull File getWorkingFastaFileGzipped();

    void setWorkingFasta(File workingFasta);

    /**
     * @return The FASTA index file associated with the working FASTA file
     */
    File getFastaIndex();

    /**
     * @return The name assigned to this genome
     */
    String getName();

    /**
     * @return The rowId of the corresponding record
     */
    Integer getGenomeId();

    /**
     * @return The rowId of the ExpData record matching the final version of the FASTA file.  If this is a saved reference,
     * this will correspond to the permanent FASTA, as opposed to the copy used in this job.  If this FASTA was created specifically for
     * this job then the FASTA will be in the analysis directory.
     */
    Integer getFastaExpDataId();

    /**
     * @param name The name used by the aligner to identify its cached directory
     * @return The folder expected containing the cached index, which is not guarenteed to exist.  See AlignerIndexUtil for related methods.
     */
    File getAlignerIndexDir(String name);

    /**
     * @return The path of the .dict file expected to be associated with this genome.  Will be based on the workingFasta file
     */
    File getSequenceDictionary();

    /**
     * @return True if this is a genome not defined in the main database, such as a job using an ad hoc FASTA file or genome based on querying the NT records
     */
    boolean isTemporaryGenome();
}
