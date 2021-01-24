savedFiles <- data.frame(datasetId = character(), datasetName = character(), filename = character())
for (datasetId in names(newSeuratObjects)) {
    seuratObj <- newSeuratObjects[[datasetId]]

    fn <- paste0(outputPrefix, '.', datasetId, '.seurat.rds')
    barcodeFile <- paste0(outputPrefix, '.', datasetId, '.cellBarcodes.csv')

    saveRDS(seuratObj, file = fn)

    datasetName <- ifelse(datasetId %in% names(datasetIdToName), yes = datasetIdToName[[datasetId]], no = datasetId)
    savedFiles <- rbind(savedFiles, data.frame(datasetId = datasetId, datasetName = datasetName, filename = fn))

    CellMembrane::WriteCellBarcodes(seuratObj, file = barcodeFile)
}

write.table(savedFiles, file = 'savedSeuratObjects.txt', quote = FALSE, sep = '\t', row.names = FALSE, col.names = FALSE)

if (length(intermediateFiles) > 0) {
    write.table(data.frame(file = intermediateFiles), file = 'intermediateFiles.txt', quote = FALSE, delim = '\t', row.names = FALSE, col.names = FALSE)
}