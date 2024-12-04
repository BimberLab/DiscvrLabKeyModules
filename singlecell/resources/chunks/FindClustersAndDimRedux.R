if (!reticulate::py_module_available(module = 'leidenalg')) {
    logger::log_warn('python leidenalg not found!')
    logger::log_warn(paste0('Python available: ', reticulate::py_available()))
    logger::log_warn(reticulate::py_config())
    logger::log_warn(paste0('installed packages: ', paste0(reticulate::py_list_packages()$package, collapse = ', ')))
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