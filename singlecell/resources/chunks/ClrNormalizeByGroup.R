for (datasetId in names(seuratObjects)) {
    printName(datasetId)

    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])
    if (!('ADT' %in% names(seuratObj@assays))) {
        print('ADT assay not present, skipping')
    } else {
        seuratObj <- CellMembrane::ClrNormalizeByGroup(seuratObj, groupingVar = groupingVar, assayName = assayName, targetAssayName = targetAssayName, margin = margin, minCellsPerGroup = minCellsPerGroup, calculatePerFeatureUCell = calculatePerFeatureUCell, featureInclusionList = featureWhitelist, featureExclusionList = featureExclusionList, doAsinhTransform = doAsinhTransform)
    }

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}