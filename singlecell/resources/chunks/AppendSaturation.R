for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    if (!'DatasetId' %in% names(seuratObj@meta.data)) {
        stop('Seurat object lacks a DatasetId field!')
    }

    seuratObj <- CellMembrane::AppendPerCellSaturationInBulk(seuratObj, molInfoList = molInfoFiles)
    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}