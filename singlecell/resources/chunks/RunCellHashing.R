for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    for (id in unique(seuratObj$DatasetId)) {
        if (!(id %in% names(featureData))) {
            stop(paste0('No hashing information found for datasetId: ', id))
        }

        callFile <- featureData[[id]]
        if (!is.null(callFile)) {
            seuratObj <- cellhashR::AppendCellHashing(seuratObj, barcodeCallFile = callFile, barcodePrefix = id)
        } else {
            # Add empty columns to keep objects consistent
            if (!'HTO' %in% names(seuratObj@meta.data)) {
                seuratObj$HTO <- c('NotUsed')
            } else {
                seuratObj$HTO <- as.character(seuratObj$HTO)
                seuratObj$HTO[seuratObj$DatasetId == id] <- 'NotUsed'
                seuratObj$HTO <- naturalsort::naturalfactor(seuratObj$HTO)
            }
            
            if (!'HTO.Classification' %in% names(seuratObj@meta.data)) {
                seuratObj$HTO.Classification <- c('NotUsed')
            } else {
                seuratObj$HTO.Classification <- as.character(seuratObj$HTO.Classification)
                seuratObj$HTO.Classification[seuratObj$DatasetId == id] <- 'NotUsed'
                seuratObj$HTO.Classification <- naturalsort::naturalfactor(seuratObj$HTO.Classification)
            }
        }
    }

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}