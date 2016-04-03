var ldkUtils = require("ldk/Utils").LDK.Server.Utils;
var console = require("console");

function beforeInsert(row, errors){
    console.log("Inserting, original subsidy value: " + row.subsidy);
    if (!row.subsidy)
    {
        row.subsidy = 0;
    }
    beforeUpsert(row, errors);
}

function beforeUpdate(row, errors){
    beforeUpsert(row, errors);
}

function beforeUpsert(row, errors){
    if (row.startDate){
        row.startDate = ldkUtils.removeTimeFromDate(row.startDate);
    }

    if (row.endDate){
        row.endDate = ldkUtils.removeTimeFromDate(row.endDate);
    }
}