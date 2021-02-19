for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    seuratObj <- subset(seuratObj, subset = <EXPRESSION>)

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}