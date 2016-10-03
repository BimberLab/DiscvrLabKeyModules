require(plyr)
require(reshape)

args <- commandArgs(TRUE)
inputFile <- args[1]
method <- args[2]
outputFile <- args[3]

# Read input data table
D<-read.table(inputFile,header=FALSE)
names(D)<-c("Chr", "Pos", "Group", "Value")

wx <- function(d){
    w <- wilcox.test(
        subset(d, Group == "Group1", select = Value )[,1], # 1 = pval, 3 = stat
        subset(d, Group == "Group2", select = Value )[,1],
        paired=FALSE,
        exact=FALSE
    )

    return(w$p.value)
}

if (method == "wilcox"){
    DW<-ddply(D, .(Chr, Pos), .fun = wx  )
    names(DW)<-c("Chr","Pos","Wilcox_Pval")
} else {
    stop(paste0("Unknown statistical method: ", method))
}

write.table(DW,file=outputFile, row.names = FALSE, col.names = FALSE, sep= "\t")



