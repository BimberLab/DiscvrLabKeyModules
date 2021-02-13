for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    df <- CellMembrane::AvgExpression(seuratObj, groupField = groupField)
    write.table(df, file = paste0(outputPrefix, '.', datasetId, '.avg.', groupField, '.txt'), sep = '\t', row.names = FALSE, quote = FALSE)
    rm(df)

    # Cleanup
    rm(seuratObj)
    gc()
}