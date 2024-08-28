for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (field in fieldNames) {
        if (ncol(Seurat::FetchData(seuratObj, vars = c('ident', field))) != 2) {
            next
        }

        tryCatch({
            reductions <- intersect(c('tsne', 'umap', 'wnn.umap'), names(seuratObj@reductions))
            if (length(reductions) == 0) {
                stop('No reductions found to plot')
            }

            plotList <- list()
            i <- 0
            for (reduction in reductions) {
                i <- i + 1
                plotList[[i]] <- Seurat::FeaturePlot(seuratObj, features = c(field), reduction = reduction, min.cutoff = 'q05', max.cutoff = 'q95')
            }

            print(patchwork::wrap_plots(plotList, ncol = length(reductions)) + patchwork::plot_annotation(title = field) + patchwork::plot_layout(guides = "collect"))
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