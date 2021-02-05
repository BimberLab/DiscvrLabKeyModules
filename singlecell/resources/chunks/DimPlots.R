for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    for (field in fieldNames) {
        if (!(field %in% names(seuratObj@meta.data))) {
            next
        }

        P1 <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'tsne')
        P2 <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'umap')

        P1 <- P1 | P2
        P1 <- P1 + patchwork::plot_annotation(title = field)
    }

    # Cleanup
    rm(seuratObj)
    gc()
}