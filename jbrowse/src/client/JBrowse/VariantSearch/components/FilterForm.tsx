import React, { useState } from 'react';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Select from 'react-select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import CardActions from '@mui/material/CardActions';
import Card from '@mui/material/Card';
import { FieldModel, Filter, getOperatorsForField, searchStringToInitialFilters } from '../../utils';
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
        minWidth: 200,
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
    alignItems: "center",
    "& > :not(:first-child)": {
        textAlign: "center",
    }
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
    const [filters, localSetFilters] = useState<Filter[]>(searchStringToInitialFilters(fieldTypeInfo.map((x) => x.name)));
    const [highlightedInputs, setHighlightedInputs] = useState<{ [index: number]: { field: boolean, operator: boolean, value: boolean } }>({});
    const [commonFilterMenuOpen, setCommonFilterMenuOpen] = useState<boolean>(false)
    const buttonRef = React.useRef(null);

    const handleAddFilter = () => {
        localSetFilters([...filters, new Filter()]);
    };

    const handleRemoveFilter = (index) => {
        // If it's the last filter, just reset its values to default empty values
        if (filters.length === 1) {
            localSetFilters([new Filter()]);
        } else {
            // Otherwise, remove the filter normally
            localSetFilters(
                filters.filter((filter, i) => {
                    return i !== index;
                })
            );
        }
    };

    const handleFilterChange = (index, key, value) => {
        const newFilters = filters.map((filter, i) => {
            if (i === index) {
                const updatedFilter = Object.assign(new Filter(), { ...filter, [key]: value });

                if (key === "operator") {
                    if (value === "is empty" || value === "is not empty") {
                        updatedFilter.value = '';
                    }

                    if (value === "in set" || filter.operator === "in set") {
                        updatedFilter.value = ''; 
                    }
                }

                return updatedFilter;
            }
            return filter;
        });

        localSetFilters(newFilters);
    };

    const handleSubmit = (event) => {
        event.preventDefault();
        const highlightedInputs = {};

        filters.forEach((filter, index) => {
            highlightedInputs[index] = { field: false, operator: false, value: false };

            if (filter.field === '') {
                highlightedInputs[index].field = true;
            }

            if (filter.operator === '') {
                highlightedInputs[index].operator = true;
            }

            if (filter.value === '') {
                highlightedInputs[index].value = true;
            }
        });

        const isSingleEmptyFilter = filters.length === 1 && !filters[0].field && !filters[0].operator && !filters[0].value;

        setHighlightedInputs(highlightedInputs);
        if (isSingleEmptyFilter || !Object.values(highlightedInputs).some(v => (v as any).field || (v as any).operator || (v as any).value)) {
            handleQuery(filters);
            setFilters(filters);
            handleClose();
        }
    };

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
                                <FormControlMinWidth sx={ highlightedInputs[index]?.field ? highlightedSx : null }>
                                    <InputLabel id="field-label">Field</InputLabel>
                                        <Select 
                                         menuPortalTarget={document.body}
                                         menuPosition={'fixed'}  // Position menu as fixed to break out of overflow
                                         menuShouldBlockScroll={true}  // Block scroll while the dropdown is open
                                         styles={{ menuPortal: base => ({ ...base, zIndex: 9999 }) }}
                                         options={fieldTypeInfo.map(field => ({ label: field.label ?? field.name, value: field.name }))}
                                         onChange={(selected) => handleFilterChange(index, "field", selected.value)}
                                         value={{ label: filter.field, value: filter.field }}
                                        />
                                </FormControlMinWidth>

                                <FormControlMinWidth sx={ highlightedInputs[index]?.operator ? highlightedSx : null } >
                                    <InputLabel id="operator-label">Operator</InputLabel>
                                    <Select 
                                        menuPortalTarget={document.body}
                                        menuPosition={'fixed'}  // Position menu as fixed to break out of overflow
                                        menuShouldBlockScroll={true}  // Block scroll while the dropdown is open
                                        styles={{ menuPortal: base => ({ ...base, zIndex: 9999 }) }}
                                        options={getOperatorsForField(fieldTypeInfo.find(obj => obj.name === filter.field)).map(operator => ({ label: operator, value: operator }))}
                                        onChange={(selected) => handleFilterChange(index, "operator", selected.value)}
                                        value={{ label: filter.operator, value: filter.operator }}
                                    />
                                </FormControlMinWidth>

                                {filter.operator === "in set" ? (
                                    <FormControlMinWidth sx={ highlightedInputs[index]?.value ? highlightedSx : null } >
                                        <InputLabel id="value-select-label">Value</InputLabel>
                                        <Select
                                            menuPortalTarget={document.body}
                                            menuPosition={'fixed'}  // Position menu as fixed to break out of overflow
                                            menuShouldBlockScroll={true}  // Block scroll while the dropdown is open
                                            styles={{ menuPortal: base => ({ ...base, zIndex: 9999 }) }}
                                            options={allowedGroupNames.map(gn => ({ label: gn, value: gn }))}
                                            onChange={(selected) => handleFilterChange(index, "value", selected.value)}
                                            value={{ label: filter.value, value: filter.value }}
                                        />
                                    </FormControlMinWidth>
                                ) : fieldTypeInfo.find(obj => obj.name === filter.field)?.allowableValues?.length > 0 ? (
                                    <FormControlMinWidth sx={ highlightedInputs[index]?.value ? highlightedSx : null } >
                                        <InputLabel id="value-select-label">Value</InputLabel>
                                        <Select
                                            menuPortalTarget={document.body}
                                            menuPosition={'fixed'}  // Position menu as fixed to break out of overflow
                                            menuShouldBlockScroll={true}  // Block scroll while the dropdown is open
                                            styles={{ menuPortal: base => ({ ...base, zIndex: 9999 }) }}
                                            isMulti={fieldTypeInfo.find(obj => obj.name === filter.field)?.isMultiValued}
                                            options={fieldTypeInfo.find(obj => obj.name === filter.field)?.allowableValues.map(value => ({ label: value, value }))}
                                            onChange={(selected) => handleFilterChange(index, "value", selected?.map(s => s.value).join(',') || '')}
                                            value={filter.value.split(',').map(value => ({ label: value, value }))}
                                        />
                                    </FormControlMinWidth>
                                ) : (
                                    <TextFieldMinWidth
                                        label="Value"
                                        sx={ highlightedInputs[index]?.value ? highlightedSx : null }
                                        value={filter.value}
                                        disabled={filter.operator === "is empty" || filter.operator === "is not empty"}
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
