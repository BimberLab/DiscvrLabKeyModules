for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    rawCountDir <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]

    previouslyFilteredMatrix <- NULL
    if (useCellBender) {
        # This is the 10x project:
        print('Will use cellbender-adjusted counts instead of raw counts:')
        previouslyFilteredMatrix <- paste0(rawCountDir, '/raw_feature_bc_matrix.cellbender_filtered.h5')
        if (!file.exists(previouslyFilteredMatrix)) {
            stop(paste0('Unable to find file: ', previouslyFilteredMatrix, '. You can re-run cellbender to fix this.'))
        }
    }

    seuratObj <- CellMembrane::ReadAndFilter10xData(dataDir = rawCountDir, datasetId = datasetId, datasetName = datasetName, emptyDropsLower = emptyDropsLower, emptyDropsFdrThreshold = emptyDropsFdrThreshold, useEmptyDropsCellRanger = useEmptyDropsCellRanger, nExpectedCells = nExpectedCells, useSoupX = useSoupX, previouslyFilteredMatrix = previouslyFilteredMatrix)

    if (!is.null(maxAllowableCells) && ncol(seuratObj) > maxAllowableCells) {
        addErrorMessage(paste0('The seurat object has ', ncol(seuratObj), ' cells, which is more than the max allowable cells (', maxAllowableCells, '). Please review emptyDrops results as this probably means thresholds were suboptimal.'))
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}