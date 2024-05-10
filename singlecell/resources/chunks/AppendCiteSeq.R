for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

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
        aggregateBarcodeFile <- NULL
        if (dropAggregateBarcodes) {
            aggregateBarcodeFile <- paste0(matrixDir, ".aggregateCounts.csv")
            if (!file.exists(aggregateBarcodeFile)) {
                stop(paste0('Unable to find file: ', aggregateBarcodeFile))
            }
        }

        seuratObj <- CellMembrane::AppendCiteSeq(seuratObj, unfilteredMatrixDir = matrixDir, normalizeMethod = normalizeMethod, datasetId = datasetId, featureMetadata = featureMetadata, adtWhitelist = adtWhitelist, aggregateBarcodeFile = aggregateBarcodeFile)
    } else {
        print('matrixDir was NULL, skipping CITE-seq')
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}