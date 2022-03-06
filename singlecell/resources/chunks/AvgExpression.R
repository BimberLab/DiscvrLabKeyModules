for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    df <- CellMembrane::AvgExpression(seuratObj, groupField = groupField)
    write.table(df, file = paste0(outputPrefix, '.', makeLegalFileName(datasetId), '.avg.', groupField, '.txt'), sep = '\t', row.names = FALSE, quote = FALSE)
    rm(df)

    # Cleanup
    rm(seuratObj)
    gc()
}