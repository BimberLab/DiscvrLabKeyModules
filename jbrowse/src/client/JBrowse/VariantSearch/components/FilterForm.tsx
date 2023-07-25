import React, { useState } from 'react';
import { makeStyles } from '@material-ui/core/styles';
import TextField from '@material-ui/core/TextField';
import Button from '@material-ui/core/Button';
import Select from '@material-ui/core/Select';
import MenuItem from '@material-ui/core/MenuItem';
import FormControl from '@material-ui/core/FormControl';
import InputLabel from '@material-ui/core/InputLabel';
import CardActions from '@material-ui/core/CardActions';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import { FieldModel, Filter, getOperatorsForField, searchStringToInitialFilters } from '../../utils';
import KeyboardArrowDownIcon from '@material-ui/icons/KeyboardArrowDown';
import { Box, Menu } from '@material-ui/core';

const useStyles = makeStyles((theme) => ({
  formControl: {
    minWidth: 200, 
    marginRight: theme.spacing(2),
  },
  actionWrapper: {
    display: 'flex',
    gap: theme.spacing(4)
  },
  filterContainer: {
    display: 'flex',
    justifyContent: 'center',
    width: '100%',
    marginTop: theme.spacing(2),
  },
  filterRow: {
    display: "flex",
    alignItems: "center",
    marginTop: theme.spacing(2),
    justifyContent: "center", // Add this line to center the contents of the filter map call
  },
  textField: {
    marginLeft: theme.spacing(2),
    marginRight: theme.spacing(2),
  },
  submitButton: {
    marginTop: theme.spacing(2),
  },
  valueInput: {
    width: 200,
    marginLeft: theme.spacing(2),
  },
  submitAndExternal: {
    display: "flex",
    gap: theme.spacing(2),
  },
  centeredContent: {
    display: "flex",
    flexDirection: "column",
    justifyContent: "flex-start",
    alignItems: "center",
    "& > :not(:first-child)": {
      textAlign: "center",
    },
  },
  arrowPaginationWrapper: {
    maxWidth: "50%",
    display:'flex'
  },
  cardActions: {
    display: 'flex',
    justifyContent: 'flex-end',
  },
  formScroll: {
    width: '100%',
    margin: '0 auto',
    maxHeight: 'calc(100vh - 200px)',
    overflowY: 'auto',
  },
  addFilterExternalWrapper: {
    display: 'flex',
    justifyContent: 'space-between',
    width: '100%',
  },
  card: {
    backgroundColor: 'transparent',
    border: '1px solid rgba(0, 0, 0, 0.12)'
  },
  highlighted: {
    border: '2px solid red',
    borderRadius: '4px'
  }
}));

export declare type FilterFormProps = {
  handleQuery: (filters: Filter[]) => void,
  setFilters: (filters: Filter[]) => void,
  handleClose?: any,
  fieldTypeInfo: FieldModel[],
  allowedGroupNames?: string[],
  promotedFilters?: Map<string, Filter[]>
}

const FilterForm = (props: FilterFormProps ) => {
  const { handleQuery, setFilters, handleClose, fieldTypeInfo, allowedGroupNames, promotedFilters } = props
  const [filters, localSetFilters] = useState<Filter[]>(searchStringToInitialFilters(fieldTypeInfo.map((x) => x.name)));
  const [highlightedInputs, setHighlightedInputs] = useState<{ [index: number]: { field: boolean, operator: boolean, value: boolean } }>({});
  const [commonFilterMenuOpen, setCommonFilterMenuOpen] = useState<boolean>(false)
  const buttonRef = React.useRef(null);

  const classes = useStyles();

  const handleAddFilter = () => {
    localSetFilters([...filters, { field: "", operator: "", value: "" }]);
  };

  const handleRemoveFilter = (index) => {
  // If it's the last filter, just reset its values to default empty values
  if (filters.length === 1) {
    localSetFilters([{ field: "", operator: "", value: "" }]);
  } else {
    // Otherwise, remove the filter normally
    localSetFilters(
      filters.filter((filter, i) => {
        return i !== index;
      })
    );
  }};

  const handleFilterChange = (index, key, value) => {
  const newFilters = filters.map((filter, i) => {
    if (i === index) {
      return { ...filter, [key]: value };
    }
    return filter;
  });

  localSetFilters(newFilters)
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
    const f = promotedFilters[filterLabel]
    console.log(f)
  }

  return (
   <Card className={classes.card} elevation={0}>
     <form>
      <CardContent className={classes.centeredContent}>
        <div className={classes.addFilterExternalWrapper}>
          <Box>
          <Button
            variant="contained"
            color="primary"
            onClick={handleAddFilter}
          >
            Add Search Filter
          </Button>
          <Button
              ref={buttonRef}
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
        </div>

        {/* TODO: this should read the FieldModel and interpret allowableValues, perhaps isMultiValued, etc. */}
        {/* TODO: consider also using something like FieldModel.supportsFilter */}
        <div className={classes.formScroll}>
          {filters.map((filter, index) => (
            <div key={index} className={`${classes.filterRow}`}>
              <FormControl className={`${classes.formControl} ${highlightedInputs[index]?.field ? classes.highlighted : ''}`}>
                <InputLabel id="field-label">Field</InputLabel>
                <Select
                  labelId="field-label"
                  value={filter.field}
                  onChange={(event) =>
                    handleFilterChange(index, "field", event.target.value)
                  }
                >
                  <MenuItem value="">
                    <em>None</em>
                  </MenuItem>
                  {fieldTypeInfo.map((field) => (
                    <MenuItem key={field.name} value={field.name}>
                      {field.label ?? field.name}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              <FormControl className={`${classes.formControl} ${highlightedInputs[index]?.operator? classes.highlighted : ''}`}>
                <InputLabel id="operator-label">Operator</InputLabel>
                <Select
                  labelId="operator-label"
                  value={filter.operator}
                  onChange={(event) =>
                    handleFilterChange(index, "operator", event.target.value)
                  }
                >
                  <MenuItem value="None">
                    <em>None</em>
                  </MenuItem>

                  {getOperatorsForField(fieldTypeInfo.find(obj => obj.name === filter.field)) ? (
                      getOperatorsForField(fieldTypeInfo.find(obj => obj.name === filter.field)).map((operator) => (
                      <MenuItem key={operator} value={operator}>
                        {operator}
                      </MenuItem>
                    ))
                  ) : (
                    <MenuItem></MenuItem>
                  )}

                </Select>
              </FormControl>

              {filter.operator === "in set" ? (
                <FormControl className={`${classes.formControl} ${highlightedInputs[index]?.value? classes.highlighted : ''}`}>
                  <InputLabel id="value-select-label">Value</InputLabel>
                  <Select
                    labelId="value-select-label"
                    value={filter.value}
                    onChange={(event) =>
                      handleFilterChange(index, "value", event.target.value)
                    }
                  >
                    {allowedGroupNames?.map((gn) => (
                      <MenuItem value="{gn}">{gn}</MenuItem>
                    ))}

                  </Select>
                </FormControl>
              ) : fieldTypeInfo.find(obj => obj.name === filter.field)?.allowableValues?.length > 0 ? (
                <FormControl className={`${classes.formControl} ${highlightedInputs[index]?.value? classes.highlighted : ''}`}>
                  <InputLabel id="value-select-label">Value</InputLabel>
                  <Select
                    labelId="value-select-label"
                    value={filter.value}
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
                </FormControl>
              ) : (
                <TextField
                  label="Value"
                  className={`${classes.formControl} ${highlightedInputs[index]?.value? classes.highlighted : ''}`}
                  value={filter.value}
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
            </div>
          ))}
        </div>
      </CardContent>

      <CardActions className={classes.cardActions}>
        <div className={classes.submitAndExternal}>
          <Button
            onClick={handleSubmit}
            type="submit"
            variant="contained"
            color="primary"
          >
            Search
          </Button>
        </div>
      </CardActions>
    </form>
  </Card>
)}


export default FilterForm;
