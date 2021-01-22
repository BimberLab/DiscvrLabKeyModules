savedFiles <- data.frame(datasetId = character(), datasetName = character(), filename = character())
for (datasetId in names(newSeuratObjects)) {
    seuratObj <- newSeuratObjects[[datasetId]]

    saveRDS(seuratObj, file = paste0(outputPrefix, '.', datasetId, '.seurat.rds'))

    datasetName <- datasetIdToName[[datasetId]]
    savedFiles <- rbind(savedFiles, data.frame(datasetId = datasetId, datasetName = datasetName, filename = file))

    CellMembrane::WriteCellBarcodes(seuratObj, file = paste0(outputPrefix, '.', datasetId, '.cellBarcodes.csv'))
}

write.table(savedFiles, file = 'savedSeuratObjects.txt', quote = FALSE, delim = '\t', row.names = FALSE, col.names = FALSE)

if (length(intermediateFiles) > 0) {
    write.table(data.frame(file = intermediateFiles), file = 'intermediateFiles.txt', quote = FALSE, delim = '\t', row.names = FALSE, col.names = FALSE)
}