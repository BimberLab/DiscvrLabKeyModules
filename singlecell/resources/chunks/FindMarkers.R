plotList <- list()
for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    outFile <- paste0(outputPrefix, '.', datasetId, '.markers.txt')
    datasetName <- datasetIdToName[[datasetId]]

    dt <- bindArgs(CellMembrane::Find_Markers, seuratObj)()
    plotList[[datasetId]] <- dt

    # Cleanup
    rm(seuratObj)
    gc()
}

htmltools::tagList(setNames(plotList, NULL))