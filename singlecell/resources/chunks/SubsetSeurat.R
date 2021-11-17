totalPassed <- 0
for (datasetId in names(seuratObjects)) {
	seuratObj <- readRDS(seuratObjects[[datasetId]])

	#TODO: this is a stopgap for a former bug in RunCellHashing. Retain until all existing seurat objects lacking this field are removed
	if (!'HTO.Classification' %in% names(seuratObj@meta.data) && 'consensuscall.global' %in% names(seuratObj@meta.data)) {
		seuratObj$HTO.Classification <- seuratObj$consensuscall.global
	}

	print(paste0('Initial cells for dataset: ', datasetId, ' ', ncol(seuratObj)))
	<SUBSET_CODE>

	if (!is.null(seuratObj)) {
		saveData(seuratObj, datasetId)
		totalPassed <- totalPassed + 1
	}

	# Cleanup
	rm(seuratObj)
	gc()
}

if (totalPassed == 0) {
	stop('No cells remained in any seurat objects after subsetting')
}