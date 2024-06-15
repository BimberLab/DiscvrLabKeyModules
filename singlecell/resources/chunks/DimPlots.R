for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (field in fieldNames) {
        if (!(field %in% names(seuratObj@meta.data))) {
            print(paste0('Field missing, skipping: ', field))
            next
        }

        if (length(unique(na.omit(seuratObj@meta.data[[field]]))) == 0) {
            print(paste0('Object has no non-NA values, skipping: ', field))
            next
        }

        plotList <- list()
        if ('tsne' %in% names(seuratObj@reductions)) {
            plotList[['tsne']] <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'tsne')
        }

        if ('umap' %in% names(seuratObj@reductions)) {
            plotList[['umap']] <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'umap')
        }

        if ('wnn.umap' %in% names(seuratObj@reductions)) {
            plotList[['wnn.umap']] <- Seurat::DimPlot(seuratObj, group.by = field, reduction = 'wnn.umap')
        }

        P1 <- patchwork::wrap_plots(plotList) + patchwork::plot_layout(ncol = length(plotList)) + patchwork::plot_annotation(title = field) + patchwork::plot_layout(guides = "collect")

        print(P1)
    }

    # Cleanup
    rm(seuratObj)
    gc()
}