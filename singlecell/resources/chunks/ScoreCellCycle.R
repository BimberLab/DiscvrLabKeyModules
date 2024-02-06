for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::ScoreCellCycle(seuratObj, useAlternateG2M = useAlternateG2M)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}