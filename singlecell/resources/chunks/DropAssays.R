for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (assayName in assayNames) {
        if (assayName %in% names(seuratObj@assays)) {
            print(paste0('Dropping assay: ', assayName))
            updateAssay <- assayName == Seurat::DefaultAssay(seuratObj)
            seuratObj@assays[[assayName]] <- NULL
            if (updateAssay) {
                print(paste0('Changing default assay to: ', names(seuratObj@assays)[1]))
                Seurat::DefaultAssay(seuratObj) <- names(seuratObj@assays)[1]
            }
        } else {
            print(paste0('Assay not found, skipping: ', assayName))
        }
    }

    saveData(seuratObj, datasetId)
}