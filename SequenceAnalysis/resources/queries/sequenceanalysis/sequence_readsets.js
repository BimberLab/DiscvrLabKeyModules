/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

function beforeDelete(row, errors){
    errors._form = 'You cannot directly delete readsets.  To delete these records, use the delete button above the readset grid.'
}