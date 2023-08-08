import React from 'react';
import IconButton from '@mui/material/IconButton';
import ArrowBackIosIcon from '@mui/icons-material/ArrowBackIos';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';

interface ArrowPaginationProps {
  offset: number;
  onOffsetChange: (newOffset: number) => void;
}

const ArrowPagination: React.FC<ArrowPaginationProps> = ({ offset, onOffsetChange }) => {
  const handleLeftClick = () => {
    if (offset > 0) {
      onOffsetChange(offset - 1);
    }
  };

  const handleRightClick = () => {
    onOffsetChange(offset + 1);
  };

  return (
    <>
      <IconButton onClick={handleLeftClick}>
        <ArrowBackIosIcon />
      </IconButton>
      <span style={{marginTop: "4px"}}><strong>Page {offset+1}</strong></span>
      <IconButton onClick={handleRightClick}>
        <ArrowForwardIosIcon />
      </IconButton>
    </>
  );
};

export default ArrowPagination;
