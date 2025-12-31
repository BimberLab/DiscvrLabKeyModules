for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::RunDecoupleR(seuratObj)
    if (!all(is.na(heatmapGroupingVars))) {
      for (heatmapGroupingVar in heatmapGroupingVars) {
        PlotTfData(seuratObj, groupField = heatmapGroupingVar)
      }
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}