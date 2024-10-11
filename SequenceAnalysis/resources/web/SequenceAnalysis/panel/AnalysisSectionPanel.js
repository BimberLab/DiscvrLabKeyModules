/**
 * @cfg toolConfig The JSON provided by a call to getAnalysisToolDetails
 * @cfg stepType
 * @cfg singleTool This determines how the panel is rendered.  If true, a combo will be rendered, allowing a single tool to be selected (like aligners).  If false, each tool will be given a checkbox, allowing any combination to run (like preprocessing)
 * @cfg comboLabel If running in singleTool mode, this is the label used to render the selector combo
 * @cfg comboValue If running in singleTool mode, this is the starting value for the combo
 * @cfg sectionDescription
 */
Ext4.define('SequenceAnalysis.panel.AnalysisSectionPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.sequenceanalysis-analysissectionpanel',
    singleTool: false,
    allowDuplicateSteps: false,

    initComponent: function (){
        Ext4.apply(this, {
            width: '100%',
            bodyBorder: false,
            bodyStyle: 'padding: 5px;',
            border: true,
            defaults: {
                width: 350
            }
        });

        if (this.stepType){
            this.stepConfig = this.toolConfig[this.stepType];
            this.stepConfig = LDK.Utils.sortByProperty(this.stepConfig, 'label');
            this.items = this.getItems(this.stepConfig);
        }

        this.callParent(arguments);
    },

    getCfgForToolParameters: function(toolConfig){
        var paramCfg = [];
        if (toolConfig.parameters && toolConfig.parameters.length) {
            Ext4.each(toolConfig.parameters, function (i, idx) {
                var paramName = this.stepType + '.' + toolConfig.name + '.' + i.name;
                var o = {
                    xtype: i.fieldXtype,
                    isToolParam: true,
                    fieldLabel: i.label,
                    helpPopup: (i.description || '') + (i.commandLineParam ? '<br>Parameter name: \'' + i.commandLineParam + '\'' : ''),
                    name: paramName,
                    value: LABKEY.ActionURL.getParameter(paramName) ? LABKEY.ActionURL.getParameter(paramName) : i.defaultValue
                };

                if (i.additionalExtConfig){
                    for (var prop in i.additionalExtConfig){
                        var val = i.additionalExtConfig[prop];
                        if (Ext4.isString(val) && val.match(/^js:/)){
                            val = val.replace(/^js:/, '');
                            val = eval("false || " + val);

                            i.additionalExtConfig[prop] = val;
                        }
                    }
                    Ext4.apply(o, i.additionalExtConfig);
                }

                //force checkboxes to submit true instead of 'on'
                if (o.xtype === 'checkbox'){
                    if (!Ext4.isDefined(o.inputValue)){
                        o.inputValue = true;
                    }

                    if (o.value){
                        o.checked = true;
                    }
                }

                paramCfg.push(o);
            }, this);
        }

        return paramCfg;
    },

    getItems: function(toolConfig){
        var items = [];

        if (this.sectionDescription){
            items.push({
                html: this.sectionDescription,
                border: false,
                width: '100%',
                style: 'padding-bottom: 10px;'
            });
        }

        if (this.singleTool) {
            items = items.concat(this.getSingleSelectCombo(toolConfig));
        }
        else if (!Ext4.isEmpty(this.toolIdx)){
            items = this.getSingleToolConfig(toolConfig, this.toolIdx);
        }
        else {
            items.push({
                xtype: 'button',
                text: 'Add Step',
                width: 100,
                style: 'margin-bottom: 10px;',
                scope: this,
                handler: this.addStep
            },{
                itemId: 'toolConfgPanel',
                width: '100%',
                border: false,
                defaults: {
                    width: 450
                }
            });
        }

        return items;
    },

    addStep: function(btn){
        var items = [];
        Ext4.Array.forEach(this.stepConfig, function(step){
            items.push({
                xtype: 'displayfield',
                value: step.label,
                style: 'margin-right: 30px;'
            });

            items.push(this.getDescriptionButton(step));

            items.push({
                xtype: 'ldk-linkbutton',
                text: 'Add to Start',
                linkCls: 'labkey-text-link',
                step: step,
                handler: function(btn){
                    var win = btn.up('window');
                    var owner = win.ownerPanel;
                    var target = owner.down('#toolConfgPanel');

                    //check if exists
                    if (!owner.allowDuplicateSteps && target.down('[stepName=' + btn.step.name + ']')) {
                        Ext4.Msg.alert('Already Added', 'This step has already been added and cannot be used twice.');
                    }
                    else {
                        target.insert(0, owner.getAbbreviatedConfigForItem(btn.step));
                    }
                }
            });

            items.push({
                xtype: 'ldk-linkbutton',
                text: 'Add to End',
                linkCls: 'labkey-text-link',
                step: step,
                handler: function(btn){
                    var win = btn.up('window');
                    var owner = win.ownerPanel;
                    var target = owner.down('#toolConfgPanel');

                    //check if exists
                    if (!owner.allowDuplicateSteps && target.down('[stepName=' + btn.step.name + ']')) {
                        Ext4.Msg.alert('Already Added', 'This step has already been added and cannot be used twice.');
                    }
                    else {
                        target.add(owner.getAbbreviatedConfigForItem(btn.step));
                    }
                }
            });
        }, this);

        if (items.length){
            items = [{
                html: 'Click the button to add a given step.  They will be added in the order you select them.  You will be able to reorder or remove them after the fact if needed.',
                border: false,
                style: 'padding-bottom: 10px;'
            },{
                xtype: 'panel',
                border: false,
                defaults: {
                    border: false,
                    style: 'margin-right: 20px;'
                },
                layout: {
                    type: 'table',
                    columns: 4
                },
                items: items
            }];
        }
        else {
            items.push({
                html: 'There are no steps of this type registered',
                border: false,
                style: 'padding-bottom: 10px;'
            });
        }

        Ext4.create('Ext.window.Window', {
            modal: true,
            ownerPanel: this,
            bodyStyle: 'padding: 5px;',
            closeAction: 'destroy',
            title: 'Add Steps',
            border: false,
            width: 800,
            autoScroll: true,
            maxHeight: '90%',
            items: items,
            buttons: [{
                text: 'Done',
                handler: function(btn){
                    btn.up('window').close();
                }
            }],
            listeners: {
                show: function(win){
                    if (win.getHeight() > window.visualViewport.height) {
                        win.alignTo(Ext4.getBody(), 't-t?');
                    }
                }
            }
        }).show(btn);
    },

    getDescriptionButton: function(cfg){
        var items = [];
        items.push({
            xtype: 'displayfield',
            width: '100%',
            fieldLabel: 'Name',
            value: cfg.label
        });

        items.push({
            xtype: 'displayfield',
            width: '100%',
            fieldLabel: 'Description',
            value: cfg.description
        });

        if (cfg.toolName){
            items.push({
                xtype: 'displayfield',
                width: '100%',
                fieldLabel: 'Tool name',
                value: cfg.toolName
            });
        }

        if (cfg.websiteURL){
            items.push({
                xtype: 'displayfield',
                fieldLabel: 'Website',
                width: '100%',
                value: '<a href="' + cfg.websiteURL + '" target="_blank">Click Here To View More About This Tool</a>',
                border: false,
                style: 'padding-bottom: 5px;'
            });
        }

        return {
            xtype: 'ldk-linkbutton',
            text: 'View Description',
            linkCls: 'labkey-text-link',
            handler: function(btn) {
                Ext4.create('Ext.window.Window', {
                    title: 'Tool Details',
                    modal: true,
                    width: 800,
                    bodyStyle: 'padding: 5px;',
                    items: items,
                    buttons: [{
                        text: 'Done',
                        handler: function(btn){
                            btn.up('window').close();
                        }
                    }]
                }).show(btn);
            }
        }
    },

    getToolStore: function(){
        if (this.toolStore){
            return this.toolStore
        }

        var data = [];
        Ext4.Array.forEach(this.stepConfig, function(i){
            data.push({
                name: i.name,
                label: i.label,
                config: i
            });
        }, this);

        this.toolStore = Ext4.create('Ext.data.JsonStore', {
            fields: ['name', 'label', 'config'],
            data: data
        });
        this.toolStore.sort('label');

        return this.toolStore;
    },

    getSingleSelectCombo: function(toolConfig){
        var listeners = {
            scope: this,
            change: this.onComboSelect
        };

        if (this.comboValue){
            listeners.afterrender = this.onComboSelect
        }

        return [{
            xtype: 'combo',
            fieldLabel: this.comboLabel,
            value: this.comboValue,
            itemId: 'selectorCombo',
            allowBlank: false,
            displayField: 'label',
            valueField: 'name',
            width: 450,
            store: this.getToolStore(),
            listeners: listeners,
            forceSelection: true,
            queryMode: 'local',
            triggerAction: 'all'
        },{
            itemId: 'toolConfgPanel',
            width: '100%',
            border: false,
            defaults: {
                width: 450
            }
        }];
    },

    getSingleToolConfig: function(toolConfig, toolIdx){
        var config = this.getToolStore().getAt(toolIdx).get('config');

        return {
            itemId: 'toolConfgPanel',
            width: '100%',
            border: false,
            defaults: {
                width: 450
            },
            items: this.getCfgForItem(config)
        }
    },

    onComboSelect: function(field) {
        var recIdx = field.store.find('name', field.getValue());
        if (recIdx == -1) {
            return;
        }

        this.loadConfigForTool(recIdx);
    },

    loadConfigForTool: function(recIdx){
        var config = this.getToolStore().getAt(recIdx).get('config');
        var target = this.down('#toolConfgPanel');
        target.removeAll();
        target.add(this.getCfgForItem(config));
    },

    setActiveTools: function(toolNames){
        var combo = this.down('#selectorCombo');
        if (combo) {
            LDK.Assert.assertTrue('AnalysisSectionPanel.setActiveTools() called with more than 1 name', toolNames != null && toolNames.length <= 1);
            combo.setValue(toolNames && toolNames.length == 1 ? toolNames[0] : null);
        }
        else if (!Ext4.isEmpty(this.toolIdx)){
            //this is always the same tool, ignore
        }
        else {
            var toAdd = [];
            Ext4.Array.forEach(toolNames, function(name) {
                LDK.Assert.assertNotEmpty('Variable name is empty: ' + toolNames.join(','), name);
                var recIdx = this.getToolStore().find('name', name);
                if (recIdx > -1) {
                    var config = this.getToolStore().getAt(recIdx).get('config');
                    toAdd.push(this.getAbbreviatedConfigForItem(config));
                }
            }, this);

            var target = this.down('#toolConfgPanel');
            target.removeAll();
            if (toAdd.length) {
                target.add(toAdd);
            }
        }
    },

    getAbbreviatedConfigForItem: function(cfg){
        var items = [{
            layout: {
                type: 'hbox'
            },
            width: '100%',
            border: false,
            style: 'margin-bottom: 10px;',
            items: [this.getDescriptionButton(cfg), {
                xtype: 'ldk-linkbutton',
                linkCls: 'labkey-text-link',
                style: 'margin-left: 5px;',
                text: 'Remove',
                handler: function (btn) {
                    var target = btn.up('fieldset');
                    var owner = target.up('#toolConfgPanel');
                    owner.remove(target);
                }
            },{
                xtype: 'ldk-linkbutton',
                linkCls: 'labkey-text-link',
                style: 'margin-left: 5px;',
                text: 'Move Up',
                handler: function (btn) {
                    var target = btn.up('fieldset');
                    var owner = target.up('#toolConfgPanel');

                    var idx = owner.items.indexOf(target);
                    if (idx > 0){
                        owner.remove(target, false);
                        owner.insert(idx - 1, target);
                    }
                }
            },{
                xtype: 'ldk-linkbutton',
                linkCls: 'labkey-text-link',
                style: 'margin-left: 5px;',
                text: 'Move Down',
                handler: function (btn) {
                    var target = btn.up('fieldset');
                    var owner = target.up('#toolConfgPanel');

                    var idx = owner.items.indexOf(target);
                    idx++;
                    if (idx < (owner.items.getCount())){
                        owner.remove(target, false);
                        owner.insert(idx, target);
                    }
                }
            }]
        }];

        var paramItems = this.getCfgForToolParameters(cfg);
        if (paramItems.length){
            items = items.concat(paramItems);
        }
        else {
            items.push({
                xtype: 'displayfield',
                value: 'This step does not have any other parameters'
            });
        }

        return {
            xtype: 'fieldset',
            title: cfg.label,
            width: '100%',
            style: 'margin-top: 10px;',
            items: [{
                xtype: 'panel',
                width: '100%',
                border: false,
                stepName: cfg.name,
                toolConfig: cfg,
                bodyStyle: 'padding: 5px;',
                defaults: {
                    width: 350
                },
                items: items
            }]
        };
    },

    getCfgForItem: function(cfg){
        var panelId = Ext4.id();
        var paramCfg = [];
        if (cfg.description){
            paramCfg.push({
                xtype: 'displayfield',
                width: '100%',
                fieldLabel: 'Description',
                value: cfg.description
            })
        }

        if (cfg.toolName){
            paramCfg.push({
                xtype: 'displayfield',
                width: '100%',
                fieldLabel: 'Tool Name',
                value: cfg.toolName
            })
        }

        if (cfg.websiteURL) {
            paramCfg.push({
                xtype: 'displayfield',
                fieldLabel: 'Website',
                width: '100%',
                value: '<a href="' + cfg.websiteURL + '" target="_blank">Click Here To View More About This Tool</a>',
                border: false,
                style: 'padding-bottom: 5px;'
            });
        }

        paramCfg = paramCfg.concat(this.getCfgForToolParameters(cfg));

        return {
            xtype: 'panel',
            stepName: cfg.name,
            width: '100%',
            toolConfig: cfg,
            border: false,
            defaults: {
                width: 350
            },
            items: paramCfg
        }
    },

    getActiveSteps: function() {
        var ret = {};
        var stepComponents = this.query('component[stepName]');
        Ext4.Array.forEach(stepComponents, function(i){
            ret[i.stepName] = i;
        }, this);

        return ret;
    },

    toJSON: function(config){
        var ret = {};

        if (this.stepType){
            var stepNames = [];
            var stepComponents = this.query('component[stepName]');
            Ext4.Array.forEach(stepComponents, function(i){
                stepNames.push(i.stepName);
            }, this);

            ret[this.stepType] = stepNames.join(';');
        }

        var params = this.query('component[isToolParam]');
        var stepMap = this.getStepMap();
        if (params && params.length) {
            Ext4.Array.forEach(params, function (p) {
                if (!p.getValue){
                    LDK.Utils.logError('ERROR: AnalysisSectionPanel tool lacks getValue(): ' + p.name);
                    return;
                }

                // Allow specific fields to opt-out from
                if (config && config.skipFieldsNotSavable && p.doNotIncludeInTemplates) {
                    return;
                }

                //check for step #
                var stepIdx = this.getStepIdxForToolParam(p, stepMap);
                ret[p.name + (stepIdx ? '.' + stepIdx : '')] = p.getToolParameterValue ? p.getToolParameterValue () : p.getValue();
            }, this);
        }

        return ret;
    },

    getErrors: function(){
        if (!this.isValid()){
            return ['One or more fields is invalid'];
        }

        return [];
    },

    applySavedValues: function(values, allowUrlOverride){
        if (this.stepType){
            var tools = values[this.stepType] ? values[this.stepType].split(';') : [];
            this.setActiveTools(tools);
        }

        var stepMap = this.getStepMap();
        var params = this.query('component[isToolParam]');
        if (params && params.length) {
            Ext4.Array.forEach(params, function(p) {
                var stepIdx = this.getStepIdxForToolParam(p, stepMap);
                var name = p.name + (stepIdx ? '.' + stepIdx : '');
                if (Ext4.isDefined(values[name])){
                    p.setValue(values[name]);
                }

                if (allowUrlOverride && LABKEY.ActionURL.getParameter(name)) {
                    p.setValue(LABKEY.ActionURL.getParameter(name));
                }
            }, this);
        }
    },

    getStepMap: function(){
        var stepMap = {};
        if (this.stepType){
            var stepNames = [];
            var stepComponents = this.query('component[stepName]');
            Ext4.Array.forEach(stepComponents, function(i, idx){
                stepNames.push(i.stepName);

                stepMap[i.stepName] = stepMap[i.stepName] || [];
                stepMap[i.stepName].push(i);
            }, this);
        }

        return stepMap;
    },

    getStepIdxForToolParam: function(p, stepMap){
        if (this.allowDuplicateSteps && this.stepType){
            var parent = p.up('component[stepName]');
            if (parent && stepMap[parent.stepName]){
                return stepMap[parent.stepName].indexOf(parent);
            }
        }

        return 0;
    }
});
