for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    for (assayName in assayNames) {
        print(paste0('Processing assay: ', assayName))
        featureNames <- rownames(seuratObj@assays[assayName])
        if (length(featureNames) > 100) {
            warning(paste0('There are too many features in this assay, skipping: ', assayName))
            next
        }

        for (featureName in featureNames) {
            if (ncol(Seurat::FetchData(seuratObj, vars = c('ident', featureName))) != 2) {
                next
            }

            tryCatch({
                P1 <- Seurat::FeaturePlot(seuratObj, features = c(featureName), reduction = 'tsne', min.cutoff = 'q05', max.cutoff = 'q95')
                P2 <- Seurat::FeaturePlot(seuratObj, features = c(featureName), reduction = 'umap', min.cutoff = 'q05', max.cutoff = 'q95')
                P1 <- P1 | P2

                P1 <- P1 + patchwork::plot_annotation(title = featureName) + patchwork::plot_layout(guides = "collect")

                print(P1)
            }, error = function(e){
                print(paste0('Error running NimbleFeaturePlots for: ', datasetId))
                print(conditionMessage(e))
                traceback()
            })
        }
    }

    # Cleanup
    rm(seuratObj)
    gc()
}