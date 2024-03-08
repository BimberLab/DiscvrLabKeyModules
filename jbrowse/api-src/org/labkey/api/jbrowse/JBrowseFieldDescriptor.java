package org.labkey.api.jbrowse;

import htsjdk.variant.vcf.VCFHeaderLineType;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class JBrowseFieldDescriptor {
    private final String _fieldName;
    private final VCFHeaderLineType _type;
    private String _label;
    private String _description;
    private boolean _isInDefaultColumns;
    private boolean _isIndexed;
    private boolean _isMultiValued = false;
    private List<String> _allowableValues = null;
    private boolean _isHidden = false;
    private String _colWidth = null;
    private Integer _orderKey = 8;

    // NOTE: this should support "jexl:xxxxxx" syntax, like other JBrowse formatting
    private String _formatString = null;
    private String _category = null;
    private String _url = null;

    private Integer _flex = null;

    private boolean _isLocked = false;

    public JBrowseFieldDescriptor(String luceneFieldName, @Nullable String description, boolean isInDefaultColumns, boolean isIndexed, VCFHeaderLineType type, Integer orderKey) {
        _fieldName = luceneFieldName;
        _label = luceneFieldName;
        _description = description;
        _isInDefaultColumns = isInDefaultColumns;
        _isIndexed = isIndexed;
        _type = type;
        _orderKey = orderKey;
    }

    @Override
    public JBrowseFieldDescriptor clone()
    {
        JBrowseFieldDescriptor ret = new JBrowseFieldDescriptor(_fieldName, _description, _isInDefaultColumns, _isIndexed, _type, _orderKey);
        ret._url = _url;
        ret._label = _label;
        ret._category = _category;
        ret._flex = _flex;
        ret._allowableValues = _allowableValues;
        ret._colWidth = _colWidth;
        ret._formatString = _formatString;
        ret._isHidden = _isHidden;
        ret._isMultiValued = _isMultiValued;

        return ret;
    }

    public JBrowseFieldDescriptor hidden(boolean isHidden) {
        throwIfLocked();

        _isHidden = isHidden;
        return this;
    }

    public JBrowseFieldDescriptor colWidth(String colWidth) {
        throwIfLocked();

        _colWidth = colWidth;
        return this;
    }

    public JBrowseFieldDescriptor formatString(String formatString) {
        throwIfLocked();

        _formatString = formatString;
        return this;
    }

    public JBrowseFieldDescriptor allowableValues(List<String> allowableValues) {
        throwIfLocked();

        _allowableValues = allowableValues == null ? null : Collections.unmodifiableList(allowableValues);

        // Only change the value if we are certain there are multiple values:
        if (_allowableValues != null && !_allowableValues.isEmpty())
        {
            _isMultiValued = true;
        }

        return this;
    }

    public JBrowseFieldDescriptor multiValued(boolean isMultiValued) {
        throwIfLocked();

        _isMultiValued = isMultiValued;
        return this;
    }

    public JBrowseFieldDescriptor label(String label) {
        throwIfLocked();

        _label = label;
        return this;
    }

    private void throwIfLocked()
    {
        if (_isLocked)
        {
            throw new IllegalStateException("This JBrowseFieldDescriptor is locked!");
        }
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
        throwIfLocked();

        _label = label;
    }

    public void setInDefaultColumns(boolean inDefaultColumns)
    {
        throwIfLocked();

        _isInDefaultColumns = inDefaultColumns;
    }

    public void setCategory(String category)
    {
        throwIfLocked();

        _category = category;
    }

    public void setUrl(String url)
    {
        throwIfLocked();

        _url = url;
    }

    public void setMultiValued(boolean multiValued) {
        throwIfLocked();

        _isMultiValued = multiValued;
    }

    public void setHidden(boolean hidden) {
        throwIfLocked();

        _isHidden = hidden;
    }

    public void setIndexed(boolean indexed) {
        throwIfLocked();

        _isIndexed = indexed;
    }

    public void setColWidth(String colWidth) {
        throwIfLocked();

        _colWidth = colWidth;
    }

    public void setOrderKey(Integer orderKey) {
        throwIfLocked();

        _orderKey = orderKey;
    }

    public void setAllowableValues(List<String> allowableValues)
    {
        throwIfLocked();

        _allowableValues = allowableValues;
    }

    public void setFormatString(String formatString)
    {
        throwIfLocked();

        _formatString = formatString;
    }

    public void setDescription(String description)
    {
        throwIfLocked();

        _description = description;
    }

    public void setFlex(Integer flex)
    {
        throwIfLocked();

        _flex = flex;
    }

    public JBrowseFieldDescriptor inDefaultColumns(boolean isInDefaultColumns)
    {
        throwIfLocked();

        _isInDefaultColumns = isInDefaultColumns;
        return this;
    }

    public JBrowseFieldDescriptor lock()
    {
        _isLocked = true;

        return this;
    }

    public JSONObject toJSON() {
        JSONObject fieldDescriptorJSON = new JSONObject();
        fieldDescriptorJSON.put("name", _fieldName);
        fieldDescriptorJSON.put("label", _label == null ? _fieldName : _label);
        fieldDescriptorJSON.put("description", _description);
        fieldDescriptorJSON.put("type", _type == null ? null : _type.toString());
        fieldDescriptorJSON.put("isInDefaultColumns", _isInDefaultColumns);
        fieldDescriptorJSON.put("isIndexed", _isIndexed);
        fieldDescriptorJSON.put("isMultiValued", _isMultiValued);
        fieldDescriptorJSON.put("isHidden", _isHidden);
        fieldDescriptorJSON.put("colWidth", _colWidth);
        fieldDescriptorJSON.put("formatString", _formatString);
        fieldDescriptorJSON.put("orderKey", _orderKey);
        fieldDescriptorJSON.put("allowableValues", _allowableValues);
        fieldDescriptorJSON.put("category", _category);
        fieldDescriptorJSON.put("url", _url);
        fieldDescriptorJSON.put("flex", _flex);

        return fieldDescriptorJSON;
    }
}
