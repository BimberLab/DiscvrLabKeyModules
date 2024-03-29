plotList <- list()
for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    outFile <- paste0(outputPrefix, '.', makeLegalFileName(datasetId), '.markers.txt')
    datasetName <- datasetIdToName[[datasetId]]

    dt <- bindArgs(CellMembrane::Find_Markers, seuratObj)()
    plotList[[datasetId]] <- dt

    # Cleanup
    rm(seuratObj)
    gc()
}

htmltools::tagList(setNames(plotList, NULL))