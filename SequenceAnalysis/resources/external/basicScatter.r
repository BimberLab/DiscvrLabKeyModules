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

options(scipen=1000000000)

spec <- matrix(c(
    'inputFile', 'i', 1, "character",
    'outputFile', 'o', 1, "character",
    'plotTitle', 't', 2, "character",
    'xLabel', NA, 2, "character",
    'xColIdx', 'x', 1, "integer",
    'yColIdx', 'y', 1, "integer",
    'hasHeaders', 'h', 2, "logical"
), ncol=4, byrow=TRUE);
opts = getopt(spec, commandArgs(trailingOnly = TRUE));

if ( is.null(opts$xLabel ) ) { opts$xLabel = "" }
if ( is.null(opts$plotTitle ) ) { opts$plotTitle = "" }
if ( is.null(opts$hasHeaders ) ) { opts$hasHeaders = TRUE }

df <- read.table(opts$inputFile, quote="\"", header = opts$hasHeaders);

#echo "Generating Plot"

xColName <-names(df)[opts$xColIdx];
yColName <-names(df)[opts$yColIdx];

P<-ggplot(df,aes_string(x=xColName, y=yColName)) +
                ggtitle(opts$plotTitle) +
                geom_point(shape=1) +
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