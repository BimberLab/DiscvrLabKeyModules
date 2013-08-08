##
#  Copyright (c) 2011 LabKey Corporation
# 
#  Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
##
#options(echo=TRUE);
library(lattice);
library(Rlabkey);

analysis_ids = unique(labkey.data$rowid);
analysisFilter = paste(analysis_ids, collapse=';');


data <- labkey.selectRows(
    baseUrl=labkey.url.base,
    folderPath = labkey.url.path,
    schemaName="sequenceanalysis",
    containerFilter="CurrentAndSubfolders",
    queryName="nt_snp_by_pos",
    colSelect=c("adjpercent","percent","ref_nt","ref_nt_position","ref_nt_id/name","analysis_id"),
    showHidden = TRUE,
    colNameOpt = 'rname',  #rname
    colFilter = makeFilter(c('analysis_id', 'EQUALS_ONE_OF', analysisFilter), c('ref_nt_insert_index', 'EQUALS', 0))
)
colnames(data)<-c('Adjusted_Percent', 'Raw_Percent', 'Ref NT', 'NT_Position', 'Reference', 'Analysis_Id');
#str(data);
data$"Analysis_Id" = factor(data$"Analysis_Id");
data$"Reference" = factor(data$"Reference");
#data$"NT" = factor(data$"NT");

uniquePlots = paste(data$Analysis_Id, data$Reference, sep=';');
size = length(unique(uniquePlots));

if(size == 0){
    print("No records found");
    q(save="no", status=0, runLast=FALSE);
}

png(filename="${imgout:nt_snp_graph.png}",
    width=800,
    height=(400 * size)
    );

myPlot = xyplot(Adjusted_Percent + Raw_Percent ~ NT_Position | Reference * Analysis_Id,
    data=data,
    #type="o",
    layout=c(1,size),
    xlab="Ref NT Position",
    #type="cairo",
    ylab="Percent Non-WT",
    auto.key = TRUE
    #scales=list(x=list(relation="free", tick.number=10)),
    #par.settings = list(strip.background = list(col = c("light grey")) )
    );

print(myPlot);

dev.off();
