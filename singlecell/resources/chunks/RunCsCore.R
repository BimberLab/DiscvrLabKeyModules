for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    outFile <- paste0(outputPrefix, '.', makeLegalFileName(datasetId), '.markers.txt')
    module_list <- CellMembrane::RunCsCore(seuratObj, saveFile = paste0(outFile, '.cscore.rds'))
    saveRDS(module_list, paste0(outFile, '.cscore.wgcna.rds'))

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}