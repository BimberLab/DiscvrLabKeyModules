totalPassed <- 0
for (datasetId in names(seuratObjects)) {
	printName(datasetId)
	seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])
	origCells <- ncol(seuratObj)

	print(paste0('Initial cells for dataset ', datasetId, ': ', ncol(seuratObj)))

	if (!is.null(saturation.RNA.min) && !is.null(seuratObj)) {
		if (!'Saturation.RNA' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.RNA')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.RNA')
		cells <- which(x = expr > saturation.RNA.min)
		if (length(cells) > 0){
			seuratObj <- seuratObj[, cells]
			print(paste0('After saturation.RNA.min filter: ', length(colnames(x = seuratObj))))
			if (ncol(seuratObj) == 0) {
				seuratObj <- NULL
				next
			}
		} else {
			print(paste0('No cells passing saturation.RNA.min filter'))
			seuratObj <- NULL
			next
		}
	}

	if (!is.null(saturation.RNA.max) && !is.null(seuratObj)) {
		if (!'Saturation.RNA' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.RNA')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.RNA')
		cells <- which(x = expr < saturation.RNA.max)
		if (length(cells) > 0){
			seuratObj <- seuratObj[, cells]
			print(paste0('After saturation.RNA.max filter: ', length(colnames(x = seuratObj))))
			if (ncol(seuratObj) == 0) {
				seuratObj <- NULL
				next
			}
		} else {
			print(paste0('No cells passing saturation.RNA.max filter'))
			seuratObj <- NULL
			next
		}
	}

	if (!is.null(saturation.ADT.min) && !is.null(seuratObj)) {
		if (!'Saturation.ADT' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.ADT')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.ADT')
		cells <- which(x = expr >= saturation.ADT.min)
		if (length(cells) > 0){
			seuratObj <- seuratObj[, cells]
			print(paste0('After saturation.ADT.min filter: ', length(colnames(x = seuratObj))))
			if (ncol(seuratObj) == 0) {
				seuratObj <- NULL
				next
			}
		} else {
			print(paste0('No cells passing saturation.ADT.min filter'))
			seuratObj <- NULL
			next
		}
	}

	if (!is.null(saturation.ADT.max) && !is.null(seuratObj)) {
		if (!'Saturation.ADT' %in% names(seuratObj@meta.data)) {
			stop('Missing field: Saturation.ADT')
		}

		expr <- Seurat::FetchData(object = seuratObj, vars = 'Saturation.ADT')
		cells <- which(x = expr <= saturation.ADT.max)
		if (length(cells) > 0){
			seuratObj <- seuratObj[, cells]
			print(paste0('After saturation.ADT.max filter: ', length(colnames(x = seuratObj))))
			if (ncol(seuratObj) == 0) {
				seuratObj <- NULL
				next
			}
		} else {
			print(paste0('No cells passing saturation.ADT.max filter'))
			seuratObj <- NULL
			next
		}
	}

	if (dropHashingFail && !is.null(seuratObj)) {
		if (!'HTO.Classification' %in% names(seuratObj@meta.data)) {
			stop('Missing field: HTO.Classification')
		}

		# NOTE: this is added to fix an edge case that could occur if hashing not properly set
		if (all(is.na(seuratObj$HTO.Classification))) {
			print('HTO.Classification was NA, setting to NotUsed')
			seuratObj$HTO.Classification <- 'NotUsed'
		}

		tryCatch({
			cells <- Seurat::WhichCells(seuratObj, expression = HTO.Classification!='ND' & HTO.Classification!='Discordant' & HTO.Classification!='Doublet' & HTO.Classification!='Low Counts')
			if (length(cells) == 0) {
				print(paste0('There were no cells remaining after dropping cells without hashing'))
				seuratObj <- NULL
			} else {
				seuratObj <- subset(seuratObj, cells = cells)
				print(paste0('After removing cells without hashing: ', ncol(seuratObj)))
			}
		}, error = function(e){
			if (!is.null(e) && e$message == 'Cannot find cells provided') {
				print(paste0('There were no cells remaining after dropping cells without hashing'))
			}
		})
	}

	if (dropDoubletFinder && !is.null(seuratObj)) {
		if (!'scDblFinder.class' %in% names(seuratObj@meta.data)) {
			stop('Missing field: scDblFinder.class')
		}

		tryCatch({
			cells <- Seurat::WhichCells(seuratObj, expression = scDblFinder.class=='singlet')
			if (length(cells) == 0) {
				print(paste0('There were no cells remaining after dropping scDblFinder doublets'))
				seuratObj <- NULL
			} else {
				seuratObj <- subset(seuratObj, cells = cells)
				print(paste0('After removing scDblFinder doublets: ', ncol(seuratObj)))
			}
		}, error = function(e){
			if (!is.null(e) && e$message == 'Cannot find cells provided') {
				print(paste0('There were no cells remaining after dropping scDblFinder doublets'))
			}
		})
	}

	if (dropHashingNegatives && !is.null(seuratObj)) {
		if (!'HTO.Classification' %in% names(seuratObj@meta.data)) {
			stop('Missing field: HTO.Classification')
		}

		tryCatch({
			negativeCells <- Seurat::WhichCells(seuratObj, expression = HTO.Classification=='Negative')
			if (length(negativeCells) == 0) {
				print('All cells have hashing data')
			} else if (length(negativeCells) == ncol(seuratObj)) {
				print(paste0('There were no cells remaining after dropping cells where hashing is negative'))
				seuratObj <- NULL
			} else {
				expectedCells <- ncol(seuratObj) - length(negativeCells)
				seuratObj <- subset(seuratObj, cells = negativeCells, invert = TRUE)
				print(paste0('After removing cells with negative hashing calls: ', ncol(seuratObj)))
				if (ncol(seuratObj) != expectedCells) {
					stop(paste0('The subset for negative hashing cells did not work as expected. Expected cells: ', expectedCells, ', actual: ', ncol(seuratObj)))
				}
			}
		}, error = function(e){
			if (!is.null(e) && e$message == 'Cannot find cells provided') {
				print(paste0('There were no cells remaining after dropping cells with negative hashing calls'))
			}
		})
	}

	if (dropNullScGateConsensus && !is.null(seuratObj)) {
		if (!'scGateConsensus' %in% names(seuratObj@meta.data)) {
			stop('Missing field: scGateConsensus')
		}

		toDrop <- is.na(seuratObj@meta.data$scGateConsensus)
		if (sum(toDrop) > 0) {
			cells <- colnames(seuratObj)[!is.na(seuratObj@meta.data$scGateConsensus)]
			seuratObj <- subset(seuratObj, cells = cells)
			print(paste0('After dropping cells without scGateConsensus: ', length(colnames(x = seuratObj))))
		} else {
			print('All cells have a values for scGateConsensus, nothing to do')
		}
	}

	if (!is.null(dropThreshold) && !is.null(seuratObj)) {
		if (ncol(seuratObj) < dropThreshold) {
			print(paste0('Dropping object since remaining cells were below threshold: ', ncol(seuratObj)))
			seuratObj <- NULL
		}
	}

	if (!is.null(seuratObj)) {
		saveData(seuratObj, datasetId)
		totalPassed <- totalPassed + 1

		print(paste0('Final cells: ', ncol(seuratObj), ' of ', origCells, ' (', round((ncol(seuratObj)/origCells) * 100, 2), '%)'))
	}
}

if (totalPassed == 0) {
	addErrorMessage('No cells remained in any seurat objects after subsetting')
}