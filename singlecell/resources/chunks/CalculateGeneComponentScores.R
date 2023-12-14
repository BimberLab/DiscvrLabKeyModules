for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (sc in savedComponent) {
        seuratObj <- RIRA::ScoreUsingSavedComponent(seuratObj, componentOrName = sc, fieldName = sc)
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}