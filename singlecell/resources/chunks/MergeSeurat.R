if (length(seuratObjects) == 1) {
    print('There is only one seurat object, no need to merge')
} else {
    newSeuratObjects[[projectName]] <- CellMembrane::MergeSeuratObjs(seuratObjects, projectName = projectName)
}
