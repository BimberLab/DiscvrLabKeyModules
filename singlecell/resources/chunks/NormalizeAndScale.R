for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL
    gc()

    seuratObj <- bindArgs(CellMembrane::NormalizeAndScale, seuratObj)()

    newSeuratObjects[[datasetId]] <- seuratObj

    write.table(data.frame(Feature = Seurat::VariableFeatures(seuratObj), DatasetId = datasetId), file = paste0(outputPrefix, '.', datasetId, '.seurat.vf.txt'), quote = F, row.names = F, sep = '\t', col.names = F)

    # Cleanup
    rm(seuratObj)
    gc()
}