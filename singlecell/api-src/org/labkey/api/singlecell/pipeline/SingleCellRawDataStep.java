package org.labkey.api.singlecell.pipeline;

/**
 * NOTE: this is a special class designed to encompass the steps related to processing raw count data from the 10x/loupe matrix
 */
public interface SingleCellRawDataStep extends AbstractSingleCellStep
{
    String STEP_TYPE = "singleCellRawData";
}
