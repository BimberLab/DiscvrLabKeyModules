savedFiles <- data.frame(datasetId = character(), datasetName = character(), filename = character(), outputFileId = character())
for (datasetId in names(newSeuratObjects)) {
    seuratObj <- newSeuratObjects[[datasetId]]

    fn <- paste0(outputPrefix, '.', datasetId, '.seurat.rds')
    barcodeFile <- paste0(outputPrefix, '.', datasetId, '.cellBarcodes.csv')
    metaFile <- paste0(outputPrefix, '.', datasetId, '.seurat.meta.txt')

    saveRDS(seuratObj, file = fn)

    datasetName <- ifelse(datasetId %in% names(datasetIdToName), yes = datasetIdToName[[datasetId]], no = datasetId)

    # NOTE: this is the ID of the original loupe file. Needed for operations like appending cell hashing or CITE-seq
    outputFileId <- ifelse(datasetId %in% names(datasetIdTOutputFileId), yes = datasetIdTOutputFileId[[datasetId]], no = NA)

    savedFiles <- rbind(savedFiles, data.frame(datasetId = datasetId, datasetName = datasetName, filename = fn, outputFileId = outputFileId))

    # Write cell barcodes and metadata:
    write.table(seuratObj@meta.data, file = metaFile, quote = F, row.names = F, sep = ',', col.names = F)
    write.table(data.frame(CellBarcode = colnames(seuratObj)), file = barcodeFile, quote = F, row.names = F, sep = ',', col.names = F)
}

write.table(savedFiles, file = 'savedSeuratObjects.txt', quote = FALSE, sep = '\t', row.names = FALSE, col.names = FALSE)

if (length(intermediateFiles) > 0) {
    write.table(data.frame(file = intermediateFiles), file = 'intermediateFiles.txt', quote = FALSE, delim = '\t', row.names = FALSE, col.names = FALSE)
}