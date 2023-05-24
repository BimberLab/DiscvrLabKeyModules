package org.labkey.api.jbrowse;

import htsjdk.variant.vcf.VCFHeaderLineType;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

public class JBrowseFieldDescriptor {
    private final String _fieldName;
    private final VCFHeaderLineType _type;
    private String _label;
    private final String _description;
    private final boolean _isInDefaultColumns;
    private final boolean _isIndexed;

    private boolean _isMultiValued = false;
    private boolean _isHidden = false;
    private String _colWidth = null;
    private Integer _orderKey = null;

    public JBrowseFieldDescriptor(String luceneFieldName, @Nullable String description, boolean isInDefaultColumns, boolean isIndexed, VCFHeaderLineType type, Integer orderKey) {
        _fieldName = luceneFieldName;
        _label = luceneFieldName;
        _description = description;
        _isInDefaultColumns = isInDefaultColumns;
        _isIndexed = isIndexed;
        _type = type;
        _orderKey = orderKey;
    }

    public JBrowseFieldDescriptor hidden(boolean isHidden) {
        _isHidden = isHidden;
        return this;
    }

    public JBrowseFieldDescriptor colWidth(String colWidth) {
        _colWidth = colWidth;
        return this;
    }

    public JBrowseFieldDescriptor multiValued(boolean isMultiValued) {
        _isMultiValued = isMultiValued;
        return this;
    }

    public JBrowseFieldDescriptor label(String label) {
        _label = label;
        return this;
    }

    public String getFieldName() {
        return _fieldName;
    }

    public VCFHeaderLineType getType() {
        return _type;
    }

    public String getLabel() {
        return _label;
    }

    public String getDescription() {
        return _description;
    }

    public boolean isInDefaultColumns() {
        return _isInDefaultColumns;
    }

    public boolean isIndexed() {
        return _isIndexed;
    }

    public boolean isMultiValued() {
        return _isMultiValued;
    }

    public boolean isHidden() {
        return _isHidden;
    }

    public String getColWidth() {
        return _colWidth;
    }

    public void setLabel(String label) {
        _label = label;
    }

    public void setMultiValued(boolean multiValued) {
        _isMultiValued = multiValued;
    }

    public void setHidden(boolean hidden) {
        _isHidden = hidden;
    }

    public void setColWidth(String colWidth) {
        _colWidth = colWidth;
    }

    public void setOrderKey(Integer orderKey) {
        _orderKey = orderKey;
    }

    public JSONObject toJSON() {
        JSONObject fieldDescriptorJSON = new JSONObject();
        fieldDescriptorJSON.put("name", _fieldName);
        fieldDescriptorJSON.put("label", _label == null ? _fieldName : _label);
        fieldDescriptorJSON.put("description", _description);
        fieldDescriptorJSON.put("type", _type.toString());
        fieldDescriptorJSON.put("isInDefaultColumns", _isInDefaultColumns);
        fieldDescriptorJSON.put("isIndexed", _isIndexed);
        fieldDescriptorJSON.put("isMultiValued", _isMultiValued);
        fieldDescriptorJSON.put("isHidden", _isHidden);
        fieldDescriptorJSON.put("colWidth", _colWidth);
        fieldDescriptorJSON.put("orderKey", _orderKey);
        return fieldDescriptorJSON;
    }
}
