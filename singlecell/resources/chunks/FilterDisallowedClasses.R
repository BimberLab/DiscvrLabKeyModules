for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- FilterDisallowedClasses(seuratObj, sourceField = sourceField, outputFieldName = outputFieldName, ucellCutoff = ucellCutoff)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}