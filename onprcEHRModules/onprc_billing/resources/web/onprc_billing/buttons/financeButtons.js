/**
 * A button specific to the charges form
 */
EHR.DataEntryUtils.registerDataEntryFormButton('FINANCESUBMIT', {
    text: 'Submit',
            name: 'submit',
            requiredQC: 'Completed',
            targetQC: 'Completed',
            errorThreshold: 'ERROR',
            successURL: LABKEY.ActionURL.getParameter('srcURL') || LABKEY.ActionURL.buildURL('onprc_billing', 'financeManagement.view'),
            disabled: true,
            itemId: 'submitBtn',
            handler: function(btn){
        var panel = btn.up('ehr-dataentrypanel');
        Ext4.Msg.confirm('Finalize Form', 'You are about to finalize this form.  Do you want to do this?', function(v){
            if(v == 'yes')
                this.onSubmit(btn);
        }, this);
    },
    disableOn: 'ERROR'
});