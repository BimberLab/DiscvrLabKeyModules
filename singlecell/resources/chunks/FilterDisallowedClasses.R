for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- FilterDisallowedClasses(seuratObj, sourceField = sourceField, outputFieldName = outputFieldName, ucellCutoff = ucellCutoff)

    if (dropFilteredCells) {
        toDrop <- !is.na(seuratObj@meta.data[[outputFieldName]])
        print(paste0('Dropping filtered cells: ', sum(toDrop)))
        if (length(toDrop) > 0) {
            toKeep <- colnames(seuratObj)[is.na(seuratObj@meta.data[[outputFieldName]])]
            seuratObj <- subset(seuratObj, cells = toKeep)
        }
    }

    if (updateInputColumn) {
        toUpdate <- !is.na(seuratObj@meta.data[[outputFieldName]])
        seuratObj@meta.data[[sourceField]] <- as.character(seuratObj@meta.data[[sourceField]])
        seuratObj@meta.data[[sourceField]][toUpdate] <- 'Contaminant'
        seuratObj@meta.data[[sourceField]] <- naturalsort::naturalfactor(seuratObj@meta.data[[sourceField]])
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}