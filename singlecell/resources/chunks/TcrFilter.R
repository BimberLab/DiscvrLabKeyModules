totalPassed <- 0
for (datasetId in names(seuratObjects)) {
  printName(datasetId)
  seuratObj <- readRDS(seuratObjects[[datasetId]])

  cellsToKeep <- NULL
  for (locus in c('A', 'B', 'D', 'G')) {
    fieldName <- paste0('TR', locus)

    cdr3ForLocus <- cdr3s[grepl(cdr3s, pattern = paste0(fieldName, ':'))]
    if (length(cdr3ForLocus) == 0) {
      next
    }

    if (!(fieldName %in% names(seuratObj@meta.data))) {
      stop(paste0('Missing field: ', fieldName))
    }

    cdr3ForLocus <- gsub(cdr3ForLocus, pattern = paste0(fieldName, ':'), replacement = '')
    matchingCells <- sapply(seuratObj@meta.data[[fieldName]], function(x){
      if (is.na(x)) {
        return(FALSE)
      }

      values <- unlist(strsplit(as.character(x), split = ','))
      return(length(intersect(values, cdr3ForLocus)) != 0)
    })

    if (sum(matchingCells) == 0) {
      next
    }

    matchingCells <- colnames(seuratObj)[matchingCells]
    if (all(is.null(cellsToKeep))) {
      cellsToKeep <- matchingCells
    } else {
      cellsToKeep <- unique(c(cellsToKeep, matchingCells))
    }
  }

  if (all(is.null(cellsToKeep))) {
    print('There were no matching cells')
  } else {
    seuratObj <- subset(seuratObj, cells = cellsToKeep)
    #saveData(seuratObj, datasetId)
    totalPassed <- totalPassed + 1
  }

  # Cleanup
  rm(seuratObj)
  gc()
}

if (totalPassed == 0) {
  addErrorMessage('No cells remained in any seurat objects after subsetting')
}