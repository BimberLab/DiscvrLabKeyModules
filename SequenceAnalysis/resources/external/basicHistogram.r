options(warn = -1)

library(reshape)
library(lattice)
library(plyr)
library(ggplot2)
library(grid)
library(perm)
library(gridExtra)
library(naturalsort)

library(getopt);
library(Matrix);

spec <- matrix(c(
    'inputFile', 'i', 1, "character",
    'outputFile', 'o', 1, "character",
    'plotTitle', 't', 2, "character",
    'xLabel', NA, 2, "character",
    'colIdx', 'c', 1, "integer",
    'binWidth', NA, 2, "integer",
    'maxValue', NA, 2, "integer",
    'hasHeaders', 'h', 2, "logical"
), ncol=4, byrow=TRUE);
opts = getopt(spec, commandArgs(trailingOnly = TRUE));

if ( is.null(opts$xLabel ) ) { opts$xLabel = "" }
if ( is.null(opts$plotTitle ) ) { opts$plotTitle = "" }
if ( is.null(opts$hasHeaders ) ) { opts$hasHeaders = TRUE }

df <- read.table(opts$inputFile, quote="\"", header = opts$hasHeaders);

#echo "Generating Histogram"

colName <-names(df)[opts$colIdx];

if ( !is.null(opts$maxValue)) {
    message(paste0("combining all values greater than: ", opts$maxValue));

    df[colName][df[colName] > opts$maxValue] <- opts$maxValue
}

P<-ggplot(df,aes_string(x=colName)) +
                ggtitle(opts$plotTitle) +
                #scale_y_continuous(limits=c(0,2),labels=c("0.0","0.5","1.0","1.5","2.0")) +
                geom_histogram(binwidth = opts$binWidth) +
                theme(
                                #axis.text.x = element_blank(),
                                plot.margin = unit(c(0.3,0.3,0.3,0.3),"in"),
                                plot.title = element_text(size=14,vjust=2),
                                axis.title.x = element_text(size=14,vjust=-1),
                                axis.title.y = element_text(size=14,vjust=2),
                                strip.text.x = element_text(size = 12,face="bold")) +
                xlab(opts$xLabel) +
                ylab("Count")


png(opts$outputFile,width=880, height=680)
print(P);
dev.off();