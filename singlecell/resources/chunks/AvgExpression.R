GenerateAveragedData <- function(seuratObj, groupFields, addMetadata) {
    if (addMetadata && !'cDNA_ID' %in% names(seuratObj@meta.data)) {
        stop('A field names cDNA_ID must exist when addMetadata=TRUE')
    }

    if (addMetadata && !'cDNA_ID' %in% groupFields) {
        stop('When addMetadata=TRUE, cDNA_ID must be part of groupFields')
    }

    meta <- unique(seuratObj@meta.data[,groupFields, drop = F])
    rownames(meta) <- apply(meta, 1, function(y){
        return(paste0(y, collapse = '_'))
    })

    Seurat::Idents(seuratObj) <- rownames(meta)

    for (assayName in names(seuratObj@assays)) {
        if (!(!identical(seuratObj@assays[[assayName]]@counts, seuratObj@assays[[assayName]]@data))){
            print(paste0('Seurat assay', assayName, ' does not appear to be normalized, running now:'))
            seuratObj <- Seurat::NormalizeData(seuratObj, verbose = FALSE, assay = assayName)
        }
    }

    a <- Seurat::AverageExpression(seuratObj, return.seurat = T, verbose = F)
    a <- Seurat::AddMetaData(a, meta)

    totals <- seuratObj@meta.data %>% group_by_at(groupFields) %>% summarise(TotalCells = n())
    a$TotalCells <- totals$TotalCells

    if (addMetadata) {
        a <- Rdiscvr::QueryAndApplyMetadataUsingCDNA(a)
    }

    return(a)
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readRDS(seuratObjects[[datasetId]])

    seuratObj <- GenerateAveragedData(seuratObj, groupFields = groupFields, addMetadata = addMetadata)
    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}