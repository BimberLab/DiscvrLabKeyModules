for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    ret <- Seurat::AverageExpression(seuratObj, assays = NULL, features = rownames(seuratObj), group.by = groupField, slot = "counts", verbose = FALSE)
    cellsPerGroup <- t(as.matrix(table(seuratObj[[assay]][[groupField]])))
    rownames(cellsPerGroup) <- 'TotalCells'
    ret[['CellsPerGroup']] <- cellsPerGroup

    saveFile <- paste0(outputPrefix, '.', datasetId, '.avg.', groupField, '.rds')
    saveRDS(ret, file = saveFile)

    # Cleanup
    rm(seuratObj)
    gc()
}