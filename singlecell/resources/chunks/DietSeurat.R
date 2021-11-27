for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- Seurat::DietSeurat(seuratObj)
    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}