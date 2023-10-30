for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (field in fieldNames) {
        if (ncol(Seurat::FetchData(seuratObj, vars = c('ident', field))) != 2) {
            next
        }

        tryCatch({
            P1 <- Seurat::FeaturePlot(seuratObj, features = c(field), reduction = 'tsne', min.cutoff = 'q05', max.cutoff = 'q95')
            P2 <- Seurat::FeaturePlot(seuratObj, features = c(field), reduction = 'umap', min.cutoff = 'q05', max.cutoff = 'q95')

            if ('wnn.umap' %in% names(seuratObj@reductions)) {
                P3 <- Seurat::FeaturePlot(seuratObj, features = c(field), reduction = 'wnn.umap', min.cutoff = 'q05', max.cutoff = 'q95')
                P1 <- P1 | P2 | P3
            } else {
                P1 <- P1 | P2
            }

            P1 <- P1 + patchwork::plot_annotation(title = field) + patchwork::plot_layout(guides = "collect")

            print(P1)
        }, error = function(e){
            print(paste0('Error running FeaturePlots for: ', datasetId))
            print(conditionMessage(e))
            traceback()
        })
    }

    # Cleanup
    rm(seuratObj)
    gc()
}