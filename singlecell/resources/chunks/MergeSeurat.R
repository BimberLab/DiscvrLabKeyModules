if (length(seuratObjects) == 1) {
    print('There is only one seurat object, no need to merge')
    newSeuratObjects <- seuratObjects
} else {
    if (exists('doDiet') && doDiet) {
        print('Running DietSeurat on inputs')
        for (datasetId in seuratObjects) {
            seuratObjects[[datasetId]] <- Seurat::DietSeurat(seuratObjects[[datasetId]])
        }

        gc()
    }

    newSeuratObjects[[projectName]] <- CellMembrane::MergeSeuratObjs(seuratObjects, projectName = projectName)
}

# Cleanup
rm(seuratObjects)
gc()
