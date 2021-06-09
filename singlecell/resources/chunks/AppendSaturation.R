for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL
    molInfoFile <- molInfoFiles[[datasetId]]
    if (is.null(molInfoFile)) {
        stop(paste0('Unable to find molInfo file for: ', datasetId))
    }

    if (!'DatasetId' %in% names(seuratObj@meta.data)) {
        stop('Seurat object lacks a DatasetId field!')
    }

    datasetId <- unique(seuratObj$DatasetId)
    if (length(datasetId) != 1) {
        stop('Saturation can only be computed from single-dataset seurat objects!')
    }

    seuratObj <- CellMembrane::AppendPerCellSaturation(seuratObj, molInfoFile = molInfoFile, cellbarcodePrefix = paste0(datasetId, '_'))

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}