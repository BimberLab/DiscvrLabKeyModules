#!/usr/bin/env Rscript

# Example input:
# Chr     Pos     Depth   Methylation Rate        Total Methylated        Total NonMethylated     Total Methylated Plus Strand    Total Methylated Minus Strand   Total NonMethylated Plus Strand Total NonMethylated Minus Strand
# chr19   10000159        2       1.00    2                       2
# chr19   10000167        2       1.00    2                       2
# chr19   10000171        2       1.00    2                       2

# Input must be a tab-delim file of 10 fields, where fields 3 and 4 are the 
# depth and rate respectively

# Arguments
args <- commandArgs(TRUE)
file_name = args[1]            			   # File name
filter_depth_cutoff = as.integer(args[2])  # Ignore rate data below this cutoff
plot_depth_cutoff = as.integer(args[3])    # Depths above this are binned, for nice plotting.     

# Load necessary libraries.
require(plyr)
require(ggplot2)
require(grid)
require(perm)
require(reshape)
require(gridExtra)
require(data.table)

# Read input.
D=read.table(file_name,header=T,sep="\t") 

# Stop if no data.
stopifnot(nrow(D) > 0)

# rename our header (default names for _Me_rates.txt files are bad news in R)
names(D) = c("chr","pos","depth","rate","TR","BR","TM","TUM","BM","BUM")
D <- D[,c("depth", "rate")]
gc()

# make a data table of depths
dt<-data.table(D$depth)
# Remove any depth values of 0	
dt<-dt[V1 != 0]

# put everything above a certain cutoff in a max bin
mdepth = as.integer(plot_depth_cutoff) + 1
dt[V1>plot_depth_cutoff,V1:=mdepth]

# get a data table of rates that are above our filter_depth_cutoff
rt<-data.table(D$depth,D$rate)
rt<-rt[V1 >= filter_depth_cutoff]

totC=nrow(dt)
rtC=nrow(rt)

# Histogram of coverage depth	
HistD <-  ggplot(dt,aes(x=dt$V1)) +
		  ggtitle("Depth Distribution") +
		  xlab("Coverage Depth") +
		  ylab("CpGs") +
		  theme( 
      	  plot.margin = unit(c(0.3,0.25,0.25,0.25),"in"),
      	  plot.title = element_text(vjust=2),
      	  axis.title.x = element_text(vjust=-1),
      	  axis.title.y = element_text(vjust=2)) +
          geom_histogram(binwidth=1)

# Histogram of methylation rate
HistR <-  ggplot(rt,aes(x=rt$V2)) +
		  ggtitle("Rate Distribution") +
		  xlab("Methylation Rate") +
		  ylab("CpGs") +
		  theme( 
      	  plot.margin = unit(c(0.3,0.25,0.25,0.25),"in"),
      	  plot.title = element_text(vjust=2),
      	  axis.title.x = element_text(vjust=-1),
      	  axis.title.y = element_text(vjust=2)) +
          geom_histogram(binwidth=0.05)

# Open a graphics device.
png(paste0(file_name,".png"),
	height=10,
	width=8,
	units="in",
	res=300
	)
	
grid.newpage()
vp0<-viewport(width=1,height=1,x=0.5,y=0.5)
vp1<-viewport(width=0.48,height=0.7,x=0.25,y=0.6)
vp2<-viewport(width=0.48,height=0.7,x=0.70,y=0.6)
vp3<-viewport(width=1,height=0.15,x=0.50,y=0.15)
grid.text(file_name,x=0.5,y=.97) 
print(HistD,vp=vp1)
print(HistR,vp=vp2)
td<-matrix(c("Rate calculation filter depth",10,
             "CpGs with any coverage",totC,
             "CpGs above filter depth",rtC,
             "Depth plot binned above depth",plot_depth_cutoff),ncol=2,byrow="True")
T<-tableGrob(td)
pushViewport(vp3)
grid.draw(T)


dev.off()


