for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    if (!(datasetId %in% names(featureData))) {
        stop(paste0('No hashing information found for datasetId: ', datasetId))
    }

    callFile <- featureData[[datasetId]]
    if (!is.null(callFile)) {
        seuratObj <- cellhashR::AppendCellHashing(seuratObj, barcodeCallFile = callFile, barcodePrefix = datasetId)
    } else {
        # Add empty columns to keep objects consistent
        seuratObj$HTO <- c('NotUsed')
        seuratObj$consensuscall.global <- c('NotUsed')
    }

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}