for (datasetId in names(seuratObjects)) {
    seuratObj <- readRDS(seuratObjects[[datasetId]])

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
        tryCatch({
            seuratObj <- CellMembrane::AppendCiteSeq(seuratObj, unfilteredMatrixDir = matrixDir, normalizeMethod = normalizeMethod, datasetId = datasetId, featureMetadata = featureMetadata, adtWhitelist = adtWhitelist, runCellBender = runCellBender)
        }, error = function(e){
            print(paste0('Error running AppendCiteSeq for: ', datasetId))
            print(conditionMessage(e))
            traceback()
        })
    } else {
        print('matrixDir was NULL, skipping CITE-seq')
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}