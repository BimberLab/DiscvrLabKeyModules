for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    if (!(datasetId %in% names(featureData))) {
        stop(paste0('No CITE-seq information found for datasetId: ', datasetId))
    }

    adtWhitelist <- NULL
    featureMetadata <- NULL
    if (datasetId %in% names(featureMetadataFiles) && !is.null(featureMetadataFiles[[datasetId]])) {
        featureMetadata <- read.table(featureMetadataFiles[[datasetId]], sep = '\t', header = T, fill = TRUE)
        featureMetadata$rowname <- featureMetadata$tagname
        adtWhitelist <- featureMetadata$rowname
    }

    matrixDir <- featureData[[datasetId]]
    if (!is.null(matrixDir)) {
        seuratObj <- CellMembrane::AppendCiteSeq(seuratObj, unfilteredMatrixDir = matrixDir, normalizeMethod = normalizeMethod, datasetId = datasetId, featureMetadata = featureMetadata, adtWhitelist = adtWhitelist)
    } else {
        print('matrixDir was NULL, skipping CITE-seq')
    }

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}