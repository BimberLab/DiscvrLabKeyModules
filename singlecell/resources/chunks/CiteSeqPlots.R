for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    if (!(assayName %in% names(seuratObj@assays))) {
        print(paste0('Assay: ', assayName, ' not present, skipping'))
    } else {
        for (feat in rownames(seuratObj[[assayName]])) {
            tryCatch({
                CellMembrane::FeaturePlotAcrossReductions(seuratObj, features = paste0(tolower(assayName), '_', feat))
            }, error = function(e){
                warning(conditionMessage(e))
                traceback()
                message(paste0('Error running toLower for: ', feat, '. features present:'))
                message(paste0(sort(rownames(seuratObj@assays[[assayName]])), collapse = ', '))
                message(paste0('assay key: ', seuratObj@assays[[assayName]]@key))
                stop(paste0('Error running FeaturePlotAcrossReductions for: ', datasetId))
            })
        }
    }

    # Cleanup
    rm(seuratObj)
    gc()
}