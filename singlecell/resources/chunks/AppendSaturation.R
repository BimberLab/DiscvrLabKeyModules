for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL
    molInfoFile <- molInfoFiles[[datasetId]]
    if (is.null(molInfoFile)) {
        stop(paste0('Unable to find molInfo file for: ', datasetId))
    }

    seuratObj <- CellMembrane::AppendPerCellSaturation(seuratObj, molInfoFile = molInfoFile)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}