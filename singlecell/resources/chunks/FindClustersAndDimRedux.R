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

for (datasetId in names(seuratObjects)) {
    printName(datasetId)
    seuratObj <- readSeuratRDS(seuratObjects[[datasetId]])

    seuratObj <- CellMembrane::FindClustersAndDimRedux(seuratObj, minDimsToUse = minDimsToUse, useLeiden = useLeiden)

    saveData(seuratObj, datasetId)

    # Cleanup
    rm(seuratObj)
    gc()
}