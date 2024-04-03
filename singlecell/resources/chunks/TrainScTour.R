for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::TrainSctourModel(seuratObj, modelFileName = 'scTourModel', featureExclusionList = featureExclusionList)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}