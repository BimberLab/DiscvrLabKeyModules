for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if ('ADT' %in% names(seuratObj@assays)) {
        seuratObj@assays$ADT <- NULL
    } else {
        print('ADT assay not found, skipping')
    }

    saveData(seuratObj, datasetId)
}