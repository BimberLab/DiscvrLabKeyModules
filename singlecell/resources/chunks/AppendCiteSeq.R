for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    if (!(datasetId %in% names(featureData))) {
        stop(paste0('No CITE-seq information found for datasetId: ', datasetId))
    }

    # callFile <- featureData[[datasetId]]
    # if (!is.null(callFile)) {
    #     seuratObj <- CellMembrane::AppendCiteSeq(seuratObj, barcodeCallFile = callFile, barcodePrefix = datasetId)
    # }

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    seuratObjects[[datasetId]] <- NULL
    gc()
}