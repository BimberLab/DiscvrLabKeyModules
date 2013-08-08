##
#  Copyright (c) 2012 LabKey Corporation
#
#  Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
##
library(lattice);

png(filename="${imgout:barchart}",
    width=1100,
    height=500,
);
str(labkey.data);

data <- table(labkey.data$analysis_id, labkey.data$alleles, labkey.data$lineages, labkey.data$total);
str(data);

colors = c("darkblue","red","yellow","green");

barplot(data,
    main="Allele Frequencies",
    xlab="Alleles",
    col=colors,
    legend = rownames(data),
    beside=TRUE
);

dev.off();

#theTable <- table(factor(labkey.data$id_dataset_demographics_species), labkey.data$category)
#theTable
#write.table(theTable, file = "${tsvout:tsvfile}", sep = "\t", qmethod = "double", col.names=NA)
