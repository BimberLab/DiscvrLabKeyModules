plotList <- list()
for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    outFile = paste0(outputPrefix, '.', datasetId, '.markers.txt')
    datasetName <- datasetIdToName[[datasetId]]

    dt <- bindArgs(CellMembrane::Find_Markers, seuratObj)()
    plotList[[datasetId]] <- dt

    # Cleanup
    rm(seuratObj)
    gc()
}

htmltools::tagList(setNames(plotList, NULL))