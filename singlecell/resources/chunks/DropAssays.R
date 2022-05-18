for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    for (assayName in assayNames) {
        if (assayName %in% names(seuratObj@assays)) {
            seuratObj@assays[[assayName]] <- NULL
        } else {
            print(paste0('Assay not found, skipping: ', assayName))
        }
    }

    saveData(seuratObj, datasetId)
}