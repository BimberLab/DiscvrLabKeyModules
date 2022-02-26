totalPassed <- 0
for (datasetId in names(seuratObjects)) {
	printName(datasetId)
	seuratObj <- readRDS(seuratObjects[[datasetId]])

	print(paste0('Initial cells for dataset ', datasetId, ': ', ncol(seuratObj)))

	if (!is.null(saturation.RNA.min)) {
		if (!'Saturation.RNA' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.RNA')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.RNA')
		seuratObj <- seuratObj[, which(x = expr >= saturation.RNA.min)]
		print(paste0('After saturation.RNA.min filter: ', length(colnames(x = seuratObj))))
		if (ncol(seuratObj) == 0) {
			seuratObj <- NULL
			next
		}
	}

	if (!is.null(saturation.RNA.max)) {
		if (!'Saturation.RNA' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.RNA')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.RNA')
		seuratObj <- seuratObj[, which(x = expr <= saturation.RNA.max)]
		print(paste0('After saturation.RNA.max filter: ', length(colnames(x = seuratObj))))
		if (ncol(seuratObj) == 0) {
			seuratObj <- NULL
			next
		}
	}

	if (!is.null(saturation.ADT.min)) {
		if (!'Saturation.ADT' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.ADT')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.ADT')
		seuratObj <- seuratObj[, which(x = expr >= saturation.ADT.min)]
		print(paste0('After saturation.ADT.min filter: ', length(colnames(x = seuratObj))))
		if (ncol(seuratObj) == 0) {
			seuratObj <- NULL
			next
		}
	}

	if (!is.null(saturation.ADT.max)) {
		if (!'Saturation.ADT' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.ADT')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.ADT')
		seuratObj <- seuratObj[, which(x = expr <= saturation.ADT.max)]
		print(paste0('After saturation.ADT.max filter: ', length(colnames(x = seuratObj))))
		if (ncol(seuratObj) == 0) {
			seuratObj <- NULL
			next
		}
	}

	if (dropHashingFail) {
		if (!'HTO.Classification' %in% names(seuratObj@meta.data)) {
			stop('Missing field: HTO.Classification')
		}

		tryCatch({
			cells <- Seurat::WhichCells(seuratObj, expression = HTO.Classification!='ND' & HTO.Classification!='Discordant' & HTO.Classification!='Doublet')
			if (length(cells) == 0) {
				print(paste0('There were no cells remaining after dropping cells without hashing'))
				seuratObj <- NULL
			} else {
				seuratObj <- subset(seuratObj, cells = cells)
				print(paste0('Cells after subset: ', ncol(seuratObj)))
			}
		}, error = function(e){
			if (!is.null(e) && e$message == 'Cannot find cells provided') {
				print(paste0('There were no cells remaining after dropping cells without hashing'))
			}
		})
	}

	if (!is.null(seuratObj)) {
		saveData(seuratObj, datasetId)
		totalPassed <- totalPassed + 1

		print(paste0('Final cells: ', ncol(seuratObj)))
	}
}

if (totalPassed == 0) {
	addErrorMessage('No cells remained in any seurat objects after subsetting')
}