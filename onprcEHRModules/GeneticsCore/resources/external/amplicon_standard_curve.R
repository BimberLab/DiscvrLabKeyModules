#!/usr/bin/env Rscript

### LOAD REQUIRED LIBRARIES ####################################################
require(plyr)
require(ggplot2)
require(grid)
require(perm)
require(reshape)
require(gridExtra)

### HANDLE COMMAND LINE ARGUMENTS ##############################################
OurArgs <- list(
  files   = "",   # file inputs
  amps   = ""     # amplicon inputs
)
args <- commandArgs(TRUE)

# Replace default arguments with those supplied on command line
if(length(args)){
  for(possible_arg in names(OurArgs)){
    for(i in 1:length(args)){
      arg_in_use = as.character(args[i])
      if (regexpr("help",arg_in_use) > 0){
        print_help()
      }
      if(regexpr(paste0("^",possible_arg),arg_in_use) > 0){
        cv = (sub(paste0(possible_arg,"="),"",arg_in_use))
        OurArgs[possible_arg] = cv
      }
    }
  }
}

in_file <- OurArgs['files']
in_amps <- OurArgs['amps']

## Create a log file ##
log_file <- paste0(in_file,".std_crv.log.txt")
msg<-paste0("amplicon_standard_curve.R files=",in_file,", amps=",in_amps)
cat(msg,file=log_file,sep="\n")

## Check for valid input ##
## --> To do.


#### FUNCTIONS ################################################################

# Plot function: plot_std_crv
# DD is data frame of observed and expected values
# plot_file is file name for output
# title is title for plot
plot_std_crv = function(DD,plot_file,title){
  out <- tryCatch(
  {
  names(DD) <- c("expected","observed")
  DD$expected<-as.numeric(as.character(DD$expected))
  DD$observed<-as.numeric(DD$observed)

  
  DDp <- ddply(DD, c("expected"), summarise,
               N    = length(observed),
               mean = mean(observed),
               sd   = sd(observed),
               se   = sd / sqrt(N) )
  expected<-DDp$expected
  observed<-DDp$mean
  
  # Linear model
  linm <- lm(expected ~ observed)
  I <- round(coef(summary(linm))[,"Estimate"]["(Intercept)"],4)
  RSQ <- round(summary(linm)$adj.r.squared,4)
  
  # Hyperbolic
  y0 = 0
  y1 = 100
  hyp_fun = function(b,y0,y1,x) {((b*y1-y0)*x+(100*y0))/(b*x-x+100)}
  hyper <- nls(observed~hyp_fun(b,y0,y1,expected), 
               data=DDp,
               start=list(b=1),
               trace=TRUE)
  
  b <- round(coef(summary(hyper))[,"Estimate"],4)
  
  # Cubic Poly
  pol_fun = function(a,c,d,e,x) {(a*(x^3) + c*(x^2) + d*x + e) }
  mypoly <- nls(observed~pol_fun(a,c,d,e,expected), 
              data=DDp,
              start=list(a=1,c=1,d=1,e=0),
              trace=TRUE)
  a <- round(coef(summary(mypoly))[,"Estimate"]["a"],4)
  c <- round(coef(summary(mypoly))[,"Estimate"]["c"],4)
  d <- round(coef(summary(mypoly))[,"Estimate"]["d"],4)
  e <- round(coef(summary(mypoly))[,"Estimate"]["e"],4)
 

  
  # #Find x values for a given y (invert function)
  inverse = function (f, lower = -5, upper = 5) {
    function (y) uniroot((function (x) f(x) - y), 
                         lower = lower, upper = upper)$root
  }
  
  cp <- function(x) {
    # coef <- as.matrix(poly$coefficients)
    # y <- coef[1] + coef[2]*x + coef[3]*x^2 + coef[4]*x^3
    y <- a*x^3 + c*x^2 + d*x + e
    y[1]
  }
  
  hb <- function(x) {hyp_fun(b, 0, 100, x)}
  cp_inv <- inverse(cp)
  hb_inv <- inverse(hb)

  y_hb <- unlist(lapply(observed, hb_inv))
  y_cp <- unlist(lapply(observed, cp_inv))
  df_hb=data.frame(expected,y_hb)
  df_cp=data.frame(expected,y_cp)
  
  # get linear model for corrected values
  linmCP <- lm(df_cp$expected ~ df_cp$y_cp)
  RSQ_CP <- round(summary(linmCP)$adj.r.squared,4)
  linmHB <- lm(df_hb$expected ~ df_hb$y_hb)
  RSQ_HB <- round(summary(linmHB)$adj.r.squared,4)
  
  
  P1<-ggplot(DD,aes(x=expected,y=observed)) +
    geom_point(shape=1,size=4,fontface="bold") +
    ylim(0.0,1.0) +
    ylab("Observed") +
    scale_x_continuous(limits=c(0.0,1.0),breaks=c(unique(sort(expected)))) + 
    ggtitle(title) +
    geom_abline(method="lm",formula=as.formula(linm)) +
    stat_smooth(method = "lm",se=FALSE,colour="green",size=3) +
    stat_smooth(method = "lm", formula = y ~ pol_fun(a,c,d,e,x),se=FALSE,colour="blue",size=1) +
    stat_smooth(method = "lm", formula = y ~ hyp_fun(b,y0,y1,x),se=FALSE,colour="red",size=1) +
    theme(axis.text.x = element_text(size=12,vjust=2,colour="black"),
          axis.text.y = element_text(size=12,hjust=2,,colour="black"),
          plot.margin = unit(c(0.1,0.1,0.1,0.1),"in"),
          plot.title = element_text(size=14,vjust=1,face="bold"),
          axis.title.x = element_text(size=14,vjust=-1,colour="black"),
          axis.title.y = element_text(size=14,vjust=2,colour="black")) +
    annotate("text",x=.9,y=0.20,label="Linear",colour="green",face="bold") +
    annotate("text",x=.9,y=0.15,label="CubicPoly",colour="blue",face="bold") +
    annotate("text",x=.9,y=0.10,label="Hyperbolic",colour="red",face="bold") +
    annotate("text",x=.15,y=0.98,label=paste0("rsqd=",RSQ),colour="black",size=5) 
  
  ymax<-max(df_cp$y_cp)
  if(ymax > 1.0){
    ymax<-ymax + 0.05
  }else{
    ymax<-1.0
  }
  P2<-ggplot(df_cp,aes(x=expected,y=y_cp)) +
    geom_point(shape=19,size=4,fontface="bold") +
    ylim(0.0,ymax) +
    scale_x_continuous(limits=c(0.0,1.0),breaks=c(unique(sort(expected)))) + 
    ylab("Observed") +
    ggtitle(paste0("CP-corrected")) +
    geom_abline(method="lm",formula=as.formula(linm)) +
    stat_smooth(method = "lm",se=FALSE,colour="green",size=3) +
    stat_smooth(method = "lm", formula = y ~ hyp_fun(b,y0,y1,x),se=FALSE,colour="red",size=1) +
    theme(axis.text.x = element_text(size=12,vjust=2,colour="black"),
          axis.text.y = element_text(size=12,hjust=2,,colour="black"),
          plot.margin = unit(c(0.1,0.1,0.1,0.1),"in"),
          plot.title = element_text(size=12,vjust=1,face="bold"),
          axis.title.x = element_text(size=14,vjust=-1,colour="black"),
          axis.title.y = element_text(size=14,vjust=2,colour="black")) +
    annotate("text",x=.9,y=0.20,label="Linear",colour="green",fontface="bold") +
    annotate("text",x=.9,y=0.15,label="Hyperbolic",colour="red",fontface="bold")+
    annotate("text",x=.15,y=0.98,label=paste0("rsqd=",RSQ_CP),colour="black",size=5) 
  
  ymax<-max(df_hb$y_hb)
  if(ymax > 1.0){
    ymax<-ymax + 0.05
  }else{
    ymax<-1.0
  }
  P3<-ggplot(df_hb,aes(x=expected,y=y_hb)) +
    geom_point(shape=19,size=4,fontface="bold") +
    ylim(0.0,ymax) +
    scale_x_continuous(limits=c(0.0,1.0),breaks=c(unique(sort(expected)))) + 
    ylab("") +
    ggtitle(paste0("HB-corrected")) +
    geom_abline(method="lm",formula=as.formula(linm)) +
    stat_smooth(method = "lm",se=FALSE,colour="green",size=1) +
    stat_smooth(method = "lm", formula = y ~ pol_fun(a,c,d,e,x),se=FALSE,colour="blue",size=1) +
    theme(axis.text.x = element_text(size=12,vjust=2,colour="black"),
          axis.text.y = element_text(size=12,hjust=2,,colour="black"),
          plot.margin = unit(c(0.1,0.1,0.1,0.1),"in"),
          plot.title = element_text(size=12,vjust=1,face="bold"),
          axis.title.x = element_text(size=14,vjust=-1,colour="black"),
          axis.title.y = element_text(size=14,vjust=2,colour="black")) +
    annotate("text",x=.9,y=0.20,label="Linear",colour="green",fontface="bold") +
    annotate("text",x=.9,y=0.15,label="CubicPoly",colour="blue",fontface="bold") +
    annotate("text",x=.15,y=0.98,label=paste0("rsqd=",RSQ_HB),colour="black",size=5)
  

  # Create Plot File
  print(paste0(plot_file))
  png(filename=paste0(plot_file),width=880,height=880,units='px')
  
  grid.newpage()
  vp0 <- viewport(width=1,height=1,x=0.5,y=0.5)
  vp1 <- viewport(width=0.5,height=0.5,x=0.5,y=0.75)
  vp2 <- viewport(width=0.45,height=0.45,x=0.25,y=0.25)
  vp3 <- viewport(width=0.45,height=0.45,x=0.75,y=0.25)
  pushViewport(vp1)
  upViewport(0)
  print(P1,vp=vp1)
  pushViewport(vp2)
  upViewport(0)
  print(P2,vp=vp2)
  pushViewport(vp3)
  upViewport(0)
  print(P3,vp=vp3)
  upViewport(0)
  grid.text(paste0(title),x=0.13,y=0.98,gp=gpar(fontsize=12, col="black"))  
  dev.off()
  
  }, # end tryCatch
  error=function(cond){
  	  message(paste0("Couldn't plot: ",title))
  	  message(paste0(cond,"..continuing\n"))
  	  msg<-paste0("couldn't plot: ",title,", err msg: \n",cond)
  	  cat(msg,file=log_file,append=TRUE)
  }
  )
  message(paste0("plotting ",title))
  return(out)
}

#### MAIN #####################################################################
FILES<-read.table(paste0(in_file),header=FALSE)
names(FILES) <- c("expected","file")

AMPS <- read.table(paste0(in_amps),header=FALSE)
names(AMPS) <- c("chr","start","end","name")

AMPS$chr<-as.character(AMPS$chr)
AMPS$start<-as.numeric(AMPS$start)
AMPS$end<-as.numeric(AMPS$end)
AMPS$name<-as.character(AMPS$name)

for(i in 1:(nrow(AMPS))){
  chr<-AMPS$chr[i]
  start<-AMPS$start[i]
  end<-AMPS$end[i] 
  name<-AMPS$name[i]
  D <-data.frame(expected=numeric(),observed=numeric())
  for(j in 1:(nrow(FILES))){
    lvl<-FILES$expected[j]
    f<-read.table(paste0(FILES$file[j]),header=TRUE,sep="\t")
    names(f)<-c("chr","pos","depth","observed","tr","br","tm","tum","bm","bum")

    # filter for depth...
    f<-f[f$depth >= 10,]

    # filter for that which belongs to current amplicon...
    f<-f[f$chr == chr,]
    f<-f[(f$pos >= start & f$pos <= end),]
    # create a dataframe for current level, and fill it...
    df<-data.frame(expected=numeric(nrow(f)),observed=numeric(nrow(f)),stringsAsFactors=FALSE)
    if (nrow(f) > 0){
      for(k in 1:(nrow(f))){
        df$expected[k] <- lvl
        df$observed[k] <- as.numeric(f$observed[k])
      }
     # append current level data frame to D, which is data frame for all levels
     D<-rbind(D,df)
    }
  }

  if (nrow(D) > 0){
	msg<-paste0("CpGs in amp: ",name," is ",nrow(D),", plot it..\n")
  	cat(msg,file=log_file,append=TRUE)
    write.table(D,file=paste0(name,".std_crv_data.txt"))
    
    plot_std_crv(D,paste0(name,".png"),name)
    
    
  }else{
	msg<-paste0("CpGs in amp: ",name," is ",nrow(D),", skip!\n")
  	cat(msg,file=log_file,append=TRUE) 
  }
  
}

