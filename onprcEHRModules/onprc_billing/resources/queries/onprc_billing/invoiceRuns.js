/*
 * Copyright (c) 2012-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

function beforeDelete(row, errors){
    errors._form = 'You cannot directly delete invoice runs.  To delete these records, use the delete button above the grid.'
}