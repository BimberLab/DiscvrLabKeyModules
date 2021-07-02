for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    for (field in fieldNames) {
        if (!(field %in% names(seuratObj@meta.data))) {
            print(paste0('Field missing, skipping: ', field))
            next
        }

        if (length(unique(na.omit(seuratObj@meta.data[[field]]))) == 0) {
            print(paste0('Object has no non-NA values, skipping: ', field))
            next
        }

        P1 <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'tsne')
        P2 <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'umap')

        if ('wnn.umap' %in% names(seuratObj@reductions)) {
            P3 <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'wnn.umap')
            P1 <- P1 | P2 | P3
        } else {
            P1 <- P1 | P2
        }

        P1 <- P1 + patchwork::plot_annotation(title = field) + patchwork::plot_layout(guides = "collect")

        print(P1)
    }

    # Cleanup
    rm(seuratObj)
    gc()
}