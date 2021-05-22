for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    #TODO: this is a stopgap for a former bug in RunCellHashing. Retain until all existing seurat objects lacking this field are removed
    if (!'HTO.Classification' %in% names(seuratObj@meta.data) && 'consensuscall.global' %in% names(seuratObj@meta.data)) {
        seuratObj$HTO.Classification <- seuratObj$consensuscall.global
    }

    <SUBSETS>

    newSeuratObjects[[datasetId]] <- seuratObj

    # Cleanup
    rm(seuratObj)
    gc()
}