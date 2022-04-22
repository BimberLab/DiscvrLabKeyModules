library(Seurat)

visionFiles <- data.frame(DatasetId = character(), FileName = character())

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    visionObj <- CellMembrane::RunVisionForMSigDB(seuratObj, metadataCols = metadataCols)
    fileName <- paste0('vision.', datasetId, '.rds')
    saveRDS(visionObj, file = fileName)

    visionFiles <- rbind(visionFiles, data.frame(DatasetId = datasetId, FileName = fileName))
}

write.table(visionFiles, file = 'visionFiles.txt', row.names = FALSE, col.names = FALSE, quote = FALSE, sep = '\t')