for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    assayName <- 'ADT'
    if (!(assayName %in% names(seuratObj@assays))) {
        print(paste0('Assay: ', assayName, ' not present, skipping'))
    } else {
        for (feat in rownames(seuratObj[[assayName]])) {
            tryCatch({
                CellMembrane::FeaturePlotAcrossReductions(seuratObj, features = paste0(toLower(assayName), '_', feat))
            }, error = function(e){
                warning(conditionMessage(e))
                traceback()
                message('Features:')
                message(paste0(sort(rownames(seuratObj@assays[[assayName]])), collapse = ', '))
                stop(paste0('Error running FeaturePlotAcrossReductions for: ', datasetId))
            })
        }
    }

    # Cleanup
    rm(seuratObj)
    gc()
}