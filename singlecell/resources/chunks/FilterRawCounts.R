for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]

    #TODO: smarter bind
    seuratObj <- CellMembrane::FilterRawCounts(seuratObj,
        nCount_RNA.high = nCount_RNA.high,
        nCount_RNA.low = nCount_RNA.low,
        nFeature.high = nFeature.high,
        nFeature.low = nFeature.low,
        pMito.high = pMito.high,
        pMito.low = pMito.low
    )

    newSeuratObjects[[datasetId]] <- seuratObj
}