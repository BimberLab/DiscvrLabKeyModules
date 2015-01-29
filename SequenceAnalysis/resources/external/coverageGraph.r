require(plyr)
require(ggplot2)
require(grid)
require(perm)
require(reshape)
require(gridExtra)
require(naturalsort)

library(getopt);
library(Matrix);

spec <- matrix(c(
    'colHeader', '-c', 1, "character",
    'outputFile', '-o', 1, "character",
    'inputFile', '-i', 1, "character",
    'plotTitle', '-t', 1, "character",
    'yLabel', '--yLabel', 1, "character"
), ncol=4, byrow=TRUE);
opts = getopt(spec, commandArgs(trailingOnly = TRUE));

df <- read.table(opts$inputFile, quote="\"", header = TRUE);
colIdx <- match(opts$colHeader, names(df))
#df$SequenceName <- factor(df$SequenceName, levels = naturalsort(unique(df$SequenceName)))
names(df)[colIdx] <- 'YVal'
#str(df)

# make all chroms plot
P<-ggplot(df,aes(x=Start,y=YVal)) +
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
                facet_grid(SequenceName ~ ., scales="free" ,space="free")

png(opts$outputFile,width=880, height=680)
print(P)
dev.off()