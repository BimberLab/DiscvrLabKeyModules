if (length(seuratObjects) == 1) {
    print('There is only one seurat object, no need to merge')
    newSeuratObjects <- seuratObjects
} else {
    newSeuratObjects[[projectName]] <- CellMembrane::MergeSeuratObjs(seuratObjects, projectName = projectName)
}

# Cleanup
rm(seuratObjects)
gc()
