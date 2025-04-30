import React, { useState } from 'react';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Select from '@mui/material/Select';
import ReactSelect from 'react-select';
import AsyncSelect from 'react-select/async';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import CardActions from '@mui/material/CardActions';
import Card from '@mui/material/Card';
import { FieldModel, Filter, searchStringToInitialFilters } from '../../utils';
import { OperatorKey, OperatorRegistry } from '../operators';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import { Box, Menu } from '@mui/material';
import { styled } from '@mui/material/styles';

export declare type FilterFormProps = {
    handleQuery: (filters: Filter[]) => void,
    setFilters: (filters: Filter[]) => void,
    handleClose?: any,
    fieldTypeInfo: FieldModel[],
    allowedGroupNames?: string[],
    promotedFilters?: Map<string, Filter[]>
}

const FormControlMinWidth = styled(FormControl)(({ theme }) => ({
        width: 200,
        marginRight: theme.spacing(2)
}))

const TextFieldMinWidth = styled(TextField)(({ theme }) => ({
    minWidth: 200,
    marginRight: theme.spacing(2)
}))

const CardTransparent = styled(Card)(({ theme }) => ({
    backgroundColor: 'transparent',
    border: '1px solid rgba(0, 0, 0, 0.12)'
}))

const CardActionsJustify = styled(CardActions)(({ theme }) => ({
    display: 'flex',
    justifyContent: 'flex-end',
}))

const AddFilterExternalWrapper = styled('div')(({ theme }) => ({
    display: 'flex',
    justifyContent: 'space-between',
    width: '100%'
}))

const FilterRow = styled('div')(({ theme }) => ({
    display: "flex",
    alignItems: "center",
    marginTop: theme.spacing(2),
    justifyContent: "center", // Add this line to center the contents of the filter map call
}))

const CardActionsCenteredContent = styled(CardActions)(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    justifyContent: "flex-start",
    alignItems: "center"
}))

const FormScroll = styled('div')(({ theme }) => ({
    width: '100%',
    margin: '0 auto',
    maxHeight: 'calc(100vh - 200px)',
    overflowY: 'auto'
}))

const SubmitAndExternal = styled('div')(({ theme }) => ({
    display: "flex",
    gap: theme.spacing(2)
}))

const FilterForm = (props: FilterFormProps ) => {
    const { handleQuery, setFilters, handleClose, fieldTypeInfo, allowedGroupNames, promotedFilters } = props
    const initial = searchStringToInitialFilters(fieldTypeInfo.map(x => x.name))
    const [filters, localSetFilters] = useState<Filter[]>(
        initial.length ? initial : [ new Filter('', OperatorKey.None, '') ]
    )
    const [highlightedInputs, setHighlightedInputs] = useState<{ [index: number]: { field: boolean, operator: boolean, value: boolean } }>({});
    const [commonFilterMenuOpen, setCommonFilterMenuOpen] = useState<boolean>(false)
    const buttonRef = React.useRef(null);

    const handleAddFilter = () => {
        localSetFilters([...filters, new Filter('', OperatorKey.None, '')]);
    };

    const handleRemoveFilter = (index) => {
        // If it's the last filter, just reset its values to default empty values
        if (filters.length === 1) {
            localSetFilters([new Filter('', OperatorKey.None, '')]);
        } else {
            // Otherwise, remove the filter normally
            localSetFilters(
                filters.filter((filter, i) => {
                    return i !== index;
                })
            );
        }
    };

    const handleFilterChange = (
      index: number,
      key: 'field' | 'operator' | 'value',
      value: any
    ) => {
      const newFilters = filters.map((filter, i) => {
        if (i !== index) return filter;

        const updatedFilter = Object.assign(
          new Filter('', OperatorKey.None, ''),
          { ...filter, [key]: value }
        );

        if (key === 'field') {
          const fieldInfo = fieldTypeInfo.find(f => f.name === value);
          const defaultOp = fieldInfo?.getDefaultOperator() ?? OperatorRegistry[OperatorKey.None];
          updatedFilter.operator = defaultOp;
          updatedFilter.value = '';
        }
        else if (key === 'operator') {
          updatedFilter.operator = OperatorRegistry[value as OperatorKey];
          updatedFilter.value = '';
        }

        return updatedFilter;
      });

      localSetFilters(newFilters);
    };


    const handleSubmit = (event) => {
      event.preventDefault()
      const highlighted: Record<number, { field: boolean; operator: boolean; value: boolean }> = {}

      filters.forEach((filter, i) => {
        highlighted[i] = { field: false, operator: false, value: false }

        filter.field = filter.field ?? ''
        filter.value = filter.value ?? ''

        if (!filter.field) {
          highlighted[i].field = true
        }

        if (!filter.operator.key) {
          highlighted[i].operator = true
        }

        if (
          filter.operator.key === OperatorKey.IsEmpty ||
          filter.operator.key === OperatorKey.IsNotEmpty
        ) {
          filter.value = ''
        } else if (!filter.value) {
          highlighted[i].value = true
        }
      })

      const isSingleEmpty =
        filters.length === 1 &&
        !filters[0].field &&
        !filters[0].operator.key &&
        !filters[0].value

      setHighlightedInputs(highlighted)
      if (
        isSingleEmpty ||
        !Object.values(highlighted).some(v => v.field || v.operator || v.value)
      ) {
        handleQuery(filters)
        setFilters(filters)
        handleClose?.()
      }
    }

    const handleMenuClose = () => {
        setCommonFilterMenuOpen(false)
    }

    const handleMenuClick = (filterLabel: string) => {
        handleMenuClose()
        const f = promotedFilters.get(filterLabel)
        let toAdd = [...filters].filter((f) => f.isEmpty())
        toAdd = Filter.deduplicate(toAdd.concat(f))

        localSetFilters(toAdd)
    }

    const highlightedSx = {
        border: '2px solid red',
        borderRadius: '4px'
    }

    return (
        <CardTransparent elevation={0}>
            <form>
                <CardActionsCenteredContent>
                    <AddFilterExternalWrapper>
                        <Box padding={'5px'}>
                            <Button
                                variant="contained"
                                color="primary"
                                onClick={handleAddFilter}
                            >
                                Add Search Filter
                            </Button>
                            <Button
                                ref={buttonRef}
                                style={{marginLeft: '5px'}}
                                variant="contained"
                                color="primary"
                                hidden={!!promotedFilters}
                                onClick={() => setCommonFilterMenuOpen(!commonFilterMenuOpen)}
                                endIcon={<KeyboardArrowDownIcon />}
                            >
                                Common Filters
                            </Button>
                            <Menu open={commonFilterMenuOpen} onClose={handleMenuClose} anchorEl={buttonRef.current}>
                                {Array.from(promotedFilters?.keys()).map((label) => (
                                    <MenuItem key={label} onClick={(e) => handleMenuClick(label)}>{label}</MenuItem>
                                ))}
                            </Menu>
                        </Box>
                    </AddFilterExternalWrapper>

                    <FormScroll>
                        {filters.map((filter, index) => (
                            <FilterRow key={index} >
                                <FormControlMinWidth sx={highlightedInputs[index]?.field ? highlightedSx : null}>
                                    <ReactSelect
                                        inputId={`field-select-${index}`}
                                        aria-labelledby={`field-label`}
                                        placeholder="Select field..."
                                        menuPortalTarget={document.body}
                                        menuPosition="fixed"
                                        styles={{
                                            menuPortal: (base) => ({ ...base, zIndex: 9999 }),
                                        }}
                                        options={fieldTypeInfo.map(field => ({
                                            value: field.name,
                                            label: field.label ?? field.name,
                                        }))}
                                        onChange={(selected) =>
                                            handleFilterChange(index, 'field', selected?.value ?? '')
                                        }
                                        value={
                                            filter.field
                                                ? {
                                                    value: filter.field,
                                                    label: fieldTypeInfo.find(f => f.name === filter.field)?.label ?? filter.field
                                                }
                                                : null
                                        }
                                        isClearable
                                    />
                                </FormControlMinWidth>

                                <FormControlMinWidth sx={highlightedInputs[index]?.operator ? highlightedSx : null}>
                                  <InputLabel id="operator-label">Operator</InputLabel>
                                  <Select
                                    labelId="operator-label"
                                    label="Operator"
                                    value={filter.operator.key}
                                    onChange={event =>
                                      handleFilterChange(index, "operator", event.target.value as OperatorKey)
                                    }
                                  >
                                    <MenuItem value={OperatorKey.None}>
                                      <em>None</em>
                                    </MenuItem>
                                    {(() => {
                                      const ops = fieldTypeInfo.find(f => f.name === filter.field)?.getOperators() ?? [];
                                      return ops.length > 0
                                        ? ops.map(op => (
                                            <MenuItem key={op.key} value={op.key}>
                                              {op.label}
                                            </MenuItem>
                                          ))
                                        : <MenuItem />;
                                    })()}
                                  </Select>
                                </FormControlMinWidth>

                                {filter.operator.key === OperatorKey.EqualsOneOf ? (
                                    <FormControlMinWidth sx={ highlightedInputs[index]?.value ? highlightedSx : null } >
                                        <InputLabel id="value-select-label">Value</InputLabel>
                                        <Select
                                            labelId="value-select-label"
                                            label="Value"
                                            id={`value-select-${index}`}
                                            value={filter.value}
                                            onChange={(event) =>
                                                handleFilterChange(index, "value", event.target.value)
                                            }
                                        >
                                            {allowedGroupNames?.map((gn) => (
                                                <MenuItem value={gn} key={gn}>{gn}</MenuItem>
                                            ))}
                                        </Select>
                                    </FormControlMinWidth>
                                ) : fieldTypeInfo.find(obj => obj.name === filter.field)?.allowableValues?.length > 1 ? (
                                    <FormControlMinWidth sx={ highlightedInputs[index]?.value ? highlightedSx : null } >
                                        <AsyncSelect
                                            key={`async-${index}-${filter.operator.key}`}
                                            id={`value-select-${index}`}
                                            inputId={`value-select-${index}`}
                                            aria-labelledby={`value-select-${index}`}
                                            noOptionsMessage={() => 'Type to search...'}
                                            menuPortalTarget={document.body}
                                            menuPosition={'fixed'}
                                            isDisabled={filter.operator.key === OperatorKey.IsEmpty || filter.operator.key === OperatorKey.IsNotEmpty}
                                            menuShouldBlockScroll={true}
                                            // See here: https://stackoverflow.com/questions/77625507/my-react-project-with-react-18-2-0-version-and-react-select-5-4-0-v
                                            styles={{ menuPortal: (base: any) => ({ ...base, zIndex: 9999 }) }}
                                            isMulti={fieldTypeInfo.find(obj => obj.name === filter.field)?.isMultiValued}
                                            defaultOptions={fieldTypeInfo.find(obj => obj.name === filter.field)?.allowableValues?.length < 20}
                                            loadOptions={(inputValue, callback) => {
                                                const fieldInfo = fieldTypeInfo.find(obj => obj.name === filter.field);

                                                callback(
                                                    (fieldInfo?.allowableValues || [])
                                                    .filter(value => value.toLowerCase().includes(inputValue.toLowerCase()))
                                                    .map(value => ({label: value, value}))
                                                );
                                            }}
                                            onChange={(selected) => {
                                                const arr = Array.isArray(selected) ? selected : [selected].filter(Boolean)
                                                const val = arr.map(s => s.value).join(',')
                                                handleFilterChange(index, 'value', val)
                                            }}
                                            value={
                                                filter.value
                                                  ? (filter.value as string).split(',').map(v => ({ label: v, value: v }))
                                                  : null
                                              }
                                        />
                                    </FormControlMinWidth>
                                ) : fieldTypeInfo.find(obj => obj.name === filter.field)?.allowableValues?.length > 0 ? (
                                    <FormControlMinWidth sx={ highlightedInputs[index]?.value ? highlightedSx : null } >
                                        <InputLabel id="value-select-label">Value</InputLabel>
                                        <Select
                                            labelId="value-select-label"
                                            label="Value"
                                            id={`value-select-${index}`}
                                            value={filter.value}
                                            disabled={filter.operator.key === OperatorKey.IsEmpty || filter.operator.key === OperatorKey.IsNotEmpty}
                                            onChange={(event) =>
                                                handleFilterChange(index, "value", event.target.value)
                                            }
                                        >
                                            {fieldTypeInfo.find(obj => obj.name === filter.field)?.allowableValues?.map(allowableValue => (
                                                <MenuItem key={allowableValue} value={allowableValue}>
                                                    {allowableValue}
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormControlMinWidth>
                                ) : (
                                    <TextFieldMinWidth
                                        label="Value"
                                        id={`value-select-${index}`}
                                        sx={ highlightedInputs[index]?.value ? highlightedSx : null }
                                        variant="outlined"
                                        value={filter.value}
                                        disabled={filter.operator.key === OperatorKey.IsEmpty || filter.operator.key === OperatorKey.IsNotEmpty}
                                        onChange={(event) =>
                                            handleFilterChange(index, 'value', event.target.value)
                                        }
                                    />
                                )}

                                <Button
                                    variant="contained"
                                    color="primary"
                                    onClick={() => handleRemoveFilter(index)}
                                >
                                    Remove Filter
                                </Button>
                            </FilterRow>
                        ))}
                    </FormScroll>
                </CardActionsCenteredContent>

                <CardActionsJustify>
                    <SubmitAndExternal>
                        <Button
                            onClick={handleSubmit}
                            type="submit"
                            className="filter-form-select-button"
                            variant="contained"
                            color="primary"
                        >
                            Search
                        </Button>
                    </SubmitAndExternal>
                </CardActionsJustify>
            </form>
        </CardTransparent>
    )}


export default FilterForm;
