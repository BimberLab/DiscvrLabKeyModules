if (length(intermediateFiles) > 0) {
    write.table(data.frame(file = intermediateFiles), file = 'intermediateFiles.txt', quote = FALSE, delim = '\t', row.names = FALSE, col.names = FALSE)
}

if (length(errorMessages) > 0) {
    print('There were errors:')
    for (msg in errorMessages) {
        print(msg)
    }

    write(errorMessages, file = 'seuratErrors.txt')
}