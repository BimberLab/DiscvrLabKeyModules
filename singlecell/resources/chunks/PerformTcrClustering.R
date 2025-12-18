for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    print(paste0('Calculating distances for: ', datasetId))
    seuratObj <- tcrClustR::CalculateTcrDistances(
      inputData = seuratObj,
      chains = c('TRA', 'TRB', 'TRG', 'TRD'),
      organism = organism,
      minimumCloneSize = 2,
      calculateChainPairs = TRUE
    )

    print('Performing TCR Clustering')
    seuratObj <- tcrClustR::RunTcrClustering(
      seuratObj_TCR = seuratObj,
      dianaHeight = 20,
      clusterSizeThreshold = 1
    )

    print(paste0('Summary of distances: '))
    if (!'TCR_Distances' %in% names(seuratObj@misc)) {
      warning('No TCR_Distances were found, this could indicate a problem with processing')
    } else {
      for (an in names(seuratObj@misc$TCR_Distances)) {
        ad <- seuratObj@misc$TCR_Distances[[an]]
        fn <- length(unique(seuratObj[[paste0(an, '_ClusterIdx')]]))
        print(paste0('Assay: ', an, ', total clones: ', nrow(ad), '. Distinct families: ', fn))
      }
    }

    VisualizeTcrDistances(seuratObj)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}