for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    for (field in fieldNames) {
        if (ncol(Seurat::FetchData(seuratObj, vars = c('ident', field))) != 2) {
            next
        }

        P1 <- Seurat::FeaturePlot(seuratObj, features = c(field), reduction = 'tsne')
        P2 <- Seurat::FeaturePlot(seuratObj, features = c(field), reduction = 'umap')

        if ('wnn.umap' %in% names(seuratObj@reductions)) {
            P3 <- Seurat::FeaturePlot(seuratObj, features = c(field), reduction = 'wnn.umap')
            P1 <- P1 | P2 | P3
        } else {
            P1 <- P1 | P2
        }

        P1 <- P1 + patchwork::plot_annotation(title = field) + patchwork::plot_layout(guides = "collect")
    }

    # Cleanup
    rm(seuratObj)
    gc()
}