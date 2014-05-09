var ldkUtils = require("ldk/Utils").LDK.Server.Utils;

function beforeInsert(row, errors){
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