for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])
    gc()

    seuratObj <- bindArgs(CellMembrane::NormalizeAndScale, seuratObj)()

    saveData(seuratObj, datasetId)

    write.table(data.frame(Feature = Seurat::VariableFeatures(seuratObj), DatasetId = datasetId), file = paste0(outputPrefix, '.', makeLegalFileName(datasetId), '.seurat.vf.txt'), quote = F, row.names = F, sep = '\t', col.names = F)

    # Cleanup
    rm(seuratObj)
    gc()
}