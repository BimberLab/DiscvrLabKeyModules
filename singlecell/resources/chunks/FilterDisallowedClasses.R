for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- FilterDisallowedClasses(seuratObj, sourceField = sourceField, outputFieldName = outputFieldName, ucellCutoff = ucellCutoff)

    if (dropFilteredCells) {
        toDrop <- !is.na(seuratObj@meta.data[[outputFieldName]])
        print(paste0('Dropping filtered cells: ', sum(toDrop)))
        if (length(toDrop) > 0) {
            toKeep <- colnames(seuratObj)[is.na(seuratObj@meta.data[[outputFieldName]])]
            seuratObj <- subset(seuratObj, cells = toKeep)
        }
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}