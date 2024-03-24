for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if (ncol(seuratObj) == 1) {
        print('Object has a single cell, skipping')
        rm(seuratObj)
        next
    }

    for (sc in savedComponent) {
        logger::log_info(paste0('Processing ', datasetId, ' for ', sc))
        seuratObj <- RIRA::ScoreUsingSavedComponent(seuratObj, componentOrName = sc, fieldName = sc)
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}