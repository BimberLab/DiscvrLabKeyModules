if (!reticulate::py_module_available(module = 'leidenalg')) {
    logger::log_warn('python leidenalg not found!')
    logger::log_warn(paste0('Python available: ', reticulate::py_available()))
    logger::log_warn('Python config')
    pyConfig <- reticulate::py_config()
    for (pn in names(pyConfig)) {
        logger::log_warn(paste0(pn, ': ', paste0(pyConfig[[pn]]), collapse = ','))
    }

    logger::log_warn(paste0('pythonpath: ', reticulate::py_config()$pythonpath))

    logger::log_warn('Python packages:')
    for (pn in reticulate::py_list_packages()$package) {
        logger::log_warn(pn)
    }
}

if (all(is.null(clusterResolutions)) || clusterResolutions == '') {
    clusterResolutions <- c(0.2, 0.4, 0.6, 0.8, 1.2)
} else if (is.character(clusterResolutions)) {
    clusterResolutionsOrig <- clusterResolutions
    clusterResolutions <- gsub(clusterResolutions, pattern = ' ', replacement = '')
    clusterResolutions <- unlist(strsplit(clusterResolutions, split = ','))
    clusterResolutions <- as.numeric(clusterResolutions)
    if (any(is.na(clusterResolutions))) {
        stop(paste0('Some values for clusterResolutions were not numeric: ', clusterResolutionsOrig))
    }
} else if (is.numeric(clusterResolutions)) {
    # No action needed
} else {
    stop('Must provide a value for clusterResolutions')
}

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::FindClustersAndDimRedux(seuratObj, minDimsToUse = minDimsToUse, useLeiden = useLeiden, clusterResolutions = clusterResolutions)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}