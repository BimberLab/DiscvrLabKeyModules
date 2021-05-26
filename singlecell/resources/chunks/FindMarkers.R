plotlist <- list()
for (datasetId in names(seuratObjects)) {
    seuratObj <- seuratObjects[[datasetId]]
    seuratObjects[[datasetId]] <- NULL

    outFile = paste0(outputPrefix, '.', datasetId, '.markers.txt')
    dt <- bindArgs(CellMembrane::Find_Markers, seuratObj)()
    dt$x$caption <- paste0('<caption>Top DE Genes: ', datasetId, '</caption>')
    plotList[[datasetId]] <- dt

    # Cleanup
    rm(seuratObj)
    gc()
}

htmltools::tagList(setNames(plotlist, NULL))