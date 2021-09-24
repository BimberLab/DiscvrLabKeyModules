# Binds arguments from the environment to the target function
bindArgs <- function(fun, seuratObj, allowableArgNames = NULL, disallowedArgNames = NULL) {
    boundArgs <- list()
    boundArgs[['seuratObj']] <- seuratObj

    for (name in names(formals(fun))) {
        if (!is.null(disallowedArgNames) && (name %in% disallowedArgNames)) {
            next
        }
        else if (!is.null(allowableArgNames)) {
            if ((name %in% allowableArgNames) && exists(name)) {
                print(paste0('Binding argument: ', name, ': ', get(name)))
                boundArgs[[name]] <- get(name)
            }
        }
        else if (exists(name)) {
            print(paste0('Binding argument: ', name, ': ', get(name)))
            boundArgs[[name]] <- get(name)
        }
    }

    formals(fun)[names(boundArgs)] <- boundArgs

    fun
}

intermediateFiles <- c()
addIntermediateFile <- function(f) { intermediateFiles <<- c(intermediateFiles, f) }

# This will store any modified/transformed Seurat objects:
newSeuratObjects <- list()

print('Updating future.globals.maxSize')
options(future.globals.maxSize = Inf)

options('Seurat.memsafe' = TRUE)