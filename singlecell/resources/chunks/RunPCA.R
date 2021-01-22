for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    seuratObj <- CellMembrane::RunPcaSteps(seuratObj, variableGenesWhitelist = variableGenesWhitelist, variableGenesBlacklist = variableGenesBlacklist, npcs = npcs)

    newSeuratObjects[[datasetId]] <- seuratObj
}