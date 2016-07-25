/*
 * Copyright (c) 2010-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * Include the appropriate external scripts and export them
 */
var console = require("console");
exports.console = console;

var LABKEY = require("labkey");
exports.LABKEY = LABKEY;

var SU2C = {};
exports.SU2C = SU2C;

exports.init = function(event, errors){
    console.log('** evaluating: ' + this['javax.script.filename'] + ' for: ' + event);

    if (this.onInit){
        this.onInit.call(this, event, errors, SU2C);
    }
}

exports.beforeInsert = function(row, errors){
    row.objectid = row.objectid || LABKEY.Utils.generateUUID().toUpperCase();

    var handlers = [];
    if (this.onUpsert){
        handlers.push(this.onUpsert);
    }

    if (this.onInsert){
        handlers.push(this.onInsert);
    }

    if (handlers.length){
        for (var i=0;i<handlers.length;i++){
            handlers[i].call(this, row, errors, SU2C);
        }
    }
}

exports.afterInsert = function(row, errors){
    if (this.onAfterInsert){
        this.onAfterInsert.call(this, row, errors, SU2C);
    }
}


exports.beforeUpdate = function(row, oldRow, errors){
    // NOTE: this is designed to merge the old row into the new one.  this would be important if you do an update and only provide
    // the property to be changed.  the other properties would remain unchanged
    for (var prop in oldRow){
        if (!row.hasOwnProperty(prop) && LABKEY.ExtAdapter.isDefined(oldRow[prop])){
            row[prop] = oldRow[prop];
        }
    }

    var handlers = [];
    if (this.onUpsert){
        handlers.push(this.onUpsert);
    }

    if (this.onUpdate)
        handlers.push(this.onUpdate);

    if (handlers.length){
        for (var i=0;i<handlers.length;i++){
            handlers[i].call(this, row, oldRow, errors, SU2C);
        }
    }
}

exports.afterUpdate = function(row, oldRow, errors){
    var handlers = [];
    if (this.onAfterUpsert)
        handlers.push(this.onAfterUpsert);

    if (this.onAfterUpdate)
        handlers.push(this.onAfterUpdate);

    if (handlers && handlers.length){
        for (var i=0;i<handlers.length;i++){
            handlers[i].call(this, row, oldRow, errors, SU2C);
        }
    }
}

exports.beforeDelete = function(row, errors){
    if (this.onDelete)
        this.onDelete.call(this, row, errors, SU2C);

}

exports.afterDelete = function(row, errors){
    if (this.onAfterDelete)
        this.onAfterDelete.call(this, row, errors);

}

exports.complete = function(event, errors) {
    if (this.onComplete)
        this.onComplete.call(this, event, errors, SU2C);

}

exports.initScript = function(scope){
    var props = ['SU2C', 'LABKEY', 'Ext', 'console', 'init', 'beforeInsert', 'afterInsert', 'beforeUpdate', 'afterUpdate', 'beforeDelete', 'afterDelete', 'complete'];
    for (var i=0;i<props.length;i++){
        var prop = props[i];
        scope[prop] = exports[prop];
    }
}
