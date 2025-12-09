for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- tcrClustR::CalculateTcrDistances(
      inputData = seuratObj,
      chains = c('TRA', 'TRB', 'TRG', 'TRD'),
      organism = organism,
      minimumCloneSize = 2,
      calculateChainPairs = TRUE
    )

    seuratObj <- tcrClustR::RunTcrClustering(
      seuratObj_TCR = seuratObj,
      dianaHeight = 20,
      clusterSizeThreshold = 1
    )

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}