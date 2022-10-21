for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])
    gc()

    seuratObj <- CellMembrane::ClrNormalizeByGroup(seuratObj, groupingVar = groupingVar, assayName = assayName, targetAssayName = targetAssayName, margin = margin, minCellsPerGroup = minCellsPerGroup, calculatePerCellUCell = calculatePerCellUCell)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}