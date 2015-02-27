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
    'colHeader', '-c', 1, "character",
    'outputFile', '-o', 1, "character",
    'inputFile', '-i', 1, "character",
    'plotTitle', '-t', 1, "character",
    'vertical', '-v', 1, "integer",
    'yLabel', NA, 1, "character"
), ncol=4, byrow=TRUE);
opts = getopt(spec, commandArgs(trailingOnly = TRUE));
if ( is.null(opts$vertical ) ) { opts$vertical = 0 }

df <- read.table(opts$inputFile, quote="\"", header = TRUE, fill = TRUE, na.strings = "NA");
df$SequenceName <- factor(df$SequenceName, levels = naturalsort(unique(df$SequenceName)));

df <- df[(!is.na(df[opts$colHeader])),];
#str(df)

plotHeight <- 680;

if (opts$vertical == 1){
    facet1 <- 'SequenceName';
    facet2 <- '.';
    fac_formula <- as.formula(paste(facet1,"~",facet2))
    facetLine <- facet_grid(fac_formula, scales="free", space="fixed")

    plotHeight <- (500 * nlevels(df$SequenceName));
} else {
    facet1 <- '.';
    facet2 <- 'SequenceName';
    fac_formula <- as.formula(paste(facet1,"~",facet2))
    facetLine <- facet_grid(fac_formula, scales="free", space="free")
}

# make all chroms plot
P<-ggplot(df,aes_string(x="Start",y=opts$colHeader)) +
                ggtitle(opts$plotTitle) +
                #scale_y_continuous(limits=c(0,2),labels=c("0.0","0.5","1.0","1.5","2.0")) +
                geom_point(stat='identity') +
                theme(axis.text.x = element_blank(),
                                plot.margin = unit(c(0.3,0.3,0.3,0.3),"in"),
                                plot.title = element_text(size=14,vjust=2),
                                axis.title.x = element_text(size=14,vjust=-1),
                                axis.title.y = element_text(size=14,vjust=2),
                                strip.text.x = element_text(size = 12,face="bold")) +
                xlab("Position") +
                ylab(opts$yLabel) +
                facetLine


png(opts$outputFile,width=880, height=plotHeight)
print(P);
dev.off();