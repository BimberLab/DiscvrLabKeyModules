for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    if (! 'RIRA_Immune_v2.cellclass.recovered' %in% names(seuratObj@meta.data)) {
      seuratObj <- RIRA::RecoverUnassignedCells(seuratObj, groupField = groupField, minClusterProp = minClusterProp)
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}