if (length(seuratObjects) == 1) {
    print('There is only one seurat object, no need to merge')
    datasetId <- names(seuratObjects)[[1]]
    saveData(seuratObjects[[datasetId]], datasetId)
} else {
    toMerge <- list()
    for (datasetId in seuratObjects) {
        doDiet <- exists('doDiet') && doDiet
        if (exists('doDiet') && doDiet) {
            print('Running DietSeurat on inputs')
            toMerge[[datasetId]] <- Seurat::DietSeurat(readRDS(seuratObjects[[datasetId]]))
            gc()
        } else {
            toMerge[[datasetId]] <- readRDS(seuratObjects[[datasetId]])
        }
    }

    seuratObj <- CellMembrane::MergeSeuratObjs(toMerge, projectName = projectName, doGC = doDiet, errorOnBarcodeSuffix = errorOnBarcodeSuffix)
    rm(toMerge)
    saveData(seuratObj, projectName)
}

# Cleanup
rm(seuratObjects)
gc()
