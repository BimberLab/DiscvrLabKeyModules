for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- RIRA::ScoreUsingSavedComponent(seuratObj, componentOrName = savedComponent, fieldName = savedComponent)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}