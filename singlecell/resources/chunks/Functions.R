# Binds arguments from the environment to the target function
bindArgs <- function(fun, seuratObj, allowableArgNames = NULL, disallowedArgNames = NULL) {
    boundArgs <- list()
    boundArgs[['seuratObj']] <- seuratObj

    for (name in names(formals(fun))) {
        if (!is.null(disallowedArgNames) && (name %in% disallowedArgNames)) {
            next
        }
        else if (name %in% names(boundArgs)) {
            next
        }
        else if (exists(name)) {
            if (!is.null(allowableArgNames) && !(name %in% allowableArgNames)) {
                next
            }

            val <- get(name)
            displayVal <- val
            if (is.null(val)) {
                displayVal <- 'NULL'
            } else if (is.na(val)) {
                displayVal <- 'NA'
            } else if (is.object(val)) {
                displayVal <- '[object]'
            }

            print(paste0('Binding argument: ', name, ': ', displayVal))
            boundArgs[[name]] <- val
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