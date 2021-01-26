# Binds arguments from the environment to the target function
bindArgs <- function(fun, seuratObj) {
    boundArgs <- list()
    boundArgs[['seuratObj']] <- seuratObj

    for (name in names(formals(fun))) {
        if (exists(name)) {
            boundArgs[[name]] <- get(name)
        }
    }

    print(paste0('Binding arguments: ', paste0(names(boundArgs), collapse = ',')))
    formals(fun)[names(boundArgs)] <- boundArgs

    fun
}

intermediateFiles <- c()
addIntermediateFile <- function(f) { intermediateFiles <<- c(intermediateFiles, f) }

# This will store any modified/transformed Seurat objects:
newSeuratObjects <- list()

print('Updating future.globals.maxSize')
options(future.globals.maxSize = Inf)

print(paste0('R memory: ', memory.limit()))