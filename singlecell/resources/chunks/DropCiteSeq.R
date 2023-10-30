for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if ('ADT' %in% names(seuratObj@assays)) {
        seuratObj@assays$ADT <- NULL
    } else {
        print('ADT assay not found, skipping')
    }

    saveData(seuratObj, datasetId)
}