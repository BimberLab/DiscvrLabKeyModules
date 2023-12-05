for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::PerformIntegration(seuratObj, splitField = splitField, nVariableFeatures = nVariableFeatures, nIntegrationFeatures = nIntegrationFeatures, k.weight = k.weight, dimsToUse = dimsToUse, integrationFeaturesInclusionList = integrationFeaturesInclusionList, integrationFeaturesExclusionList = integrationFeaturesExclusionList)

    # Cleanup
    rm(seuratObj)
    gc()
}