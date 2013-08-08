##
#  Copyright (c) 2010-2011 LabKey Corporation
# 
#  Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
##
#options(echo=TRUE);
library(lattice);
library(Rlabkey);

analysis_ids = unique(labkey.data$rowid);
analysisFilter = paste(analysis_ids, collapse=';');
#analysisFilter = '68';

coverage <- labkey.selectRows(
    baseUrl=labkey.url.base,
    folderPath = labkey.url.path,
    containerFilter = "CurrentAndSubfolders",
    schemaName="sequenceanalysis",
    queryName="sequence_coverage",
    colSelect=c("rowid","adj_depth","depth","ref_nt_position","ref_nt_id/name","analysis_id"),
    showHidden = TRUE,
    colNameOpt = 'fieldname',  #rname
    colFilter = makeFilter(c('analysis_id', 'EQUALS_ONE_OF', analysisFilter), c('ref_nt_insert_index', 'EQUALS', 0))
)
colnames(coverage)<-c('RowId', 'Adjusted_Depth', 'Depth', 'NT_Position', 'Reference', 'Analysis_Id');

coverage$"Analysis_Id" = factor(coverage$"Analysis_Id");
coverage$"Reference" = factor(coverage$"Reference");

uniquePlots = paste(coverage$Analysis_Id, coverage$Reference, sep=';');
size = length(unique(uniquePlots));

if(size == 0){
    print("No coverage records found");
    q(save="no", status=0, runLast=FALSE);
}

png(filename="${imgout:graph.png}",
    width=800,
    height=(400 * size)
    );

myPlot = xyplot(Adjusted_Depth + Depth ~ NT_Position | Reference * Analysis_Id,
    data=coverage,
    #type="o",
    layout=c(1,size),
    xlab="Ref NT Position",
    #type="cairo",
    ylab="Depth / Adjusted Depth",
    auto.key = TRUE
    #scales=list(x=list(relation="free", tick.number=10)),
    #par.settings = list(strip.background = list(col = c("light grey")) )
    );

print(myPlot);

dev.off();
