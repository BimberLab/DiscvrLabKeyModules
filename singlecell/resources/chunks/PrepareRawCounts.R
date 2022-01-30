for (datasetId in names(seuratObjects)) {
    rawCountDir <- seuratObjects[[datasetId]]

    datasetName <- datasetIdToName[[datasetId]]
    seuratObj <- CellMembrane::ReadAndFilter10xData(dataDir = rawCountDir, datasetId = datasetId, datasetName = datasetName, emptyDropsLower = emptyDropsLower, emptyDropsFdrThreshold = emptyDropsFdrThreshold, useEmptyDropsCellRanger = useEmptyDropsCellRanger, nExpectedCells = nExpectedCells)

    if (!is.null(maxAllowableCells) && ncol(seuratObj) > maxAllowableCells) {
        addErrorMessage(paste0('The seurat object has ', ncol(seuratObj), ' cells, which is more than the max allowable cells (', maxAllowableCells, '). Please review emptyDrops results as this probably means thresholds were suboptimal.'))
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}