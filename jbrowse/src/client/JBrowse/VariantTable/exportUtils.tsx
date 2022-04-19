// This file contains export functions for a couple different filetypes, ripped from the
// ReactDataGrid documentation/demo here: https://github.com/adazzle/react-data-grid/blob/main/website/demos/exportUtils.tsx

// Likely, these won't need to be edited.

import { cloneElement } from 'react'
import type { ReactElement } from 'react'

import type { DataGridProps } from 'react-data-grid'

import { utils, writeFile } from "xlsx"
import { jsPDF } from 'jspdf'
import autoTable from 'jspdf-autotable'
import { renderToStaticMarkup } from 'react-dom/server'


export async function exportToCsv<R, SR>(
  gridElement: ReactElement<DataGridProps<R, SR>>,
  fileName: string
) {
  const { head, body, foot } = await getGridContent(gridElement);

  const content = [...head, ...body, ...foot]
    .map((cells) => cells.map(serialiseCellValue).join(','))
    .join('\n');

  downloadFile(fileName, new Blob([content], { type: 'text/csv;charset=utf-8;' }));
}

export async function exportToXlsx<R, SR>(
  gridElement: ReactElement<DataGridProps<R, SR>>,
  fileName: string
) {
  const [{ head, body, foot }] = await Promise.all([
    getGridContent(gridElement)
  ]);
  const wb = utils.book_new();
  const ws = utils.aoa_to_sheet([...head, ...body, ...foot]);
  utils.book_append_sheet(wb, ws, 'Sheet 1');
  writeFile(wb, fileName);
}

export async function exportToPdf<R, SR>(
  gridElement: ReactElement<DataGridProps<R, SR>>,
  fileName: string
) {
  const [{ head, body, foot }] = await Promise.all([
    await getGridContent(gridElement)
  ]);
  const doc = new jsPDF({
    orientation: 'l',
    unit: 'px'
  });

  autoTable(doc, {
    head,
    body,
    foot,
    horizontalPageBreak: true,
    styles: { cellPadding: 1.5, fontSize: 8, cellWidth: 'wrap' },
    tableWidth: 'wrap'
  });
  doc.save(fileName);
}

async function getGridContent<R, SR>(gridElement: ReactElement<DataGridProps<R, SR>>) {
  const grid = document.createElement('div');
  grid.innerHTML = renderToStaticMarkup(
    cloneElement(gridElement, {
      enableVirtualization: false
    })
  );

  return {
    head: getRows('.rdg-header-row'),
    body: getRows('.rdg-row:not(.rdg-summary-row)'),
    foot: getRows('.rdg-summary-row')
  };

  function getRows(selector: string) {
    return Array.from(grid.querySelectorAll<HTMLDivElement>(selector)).map((gridRow) => {
      return Array.from(gridRow.querySelectorAll<HTMLDivElement>('.rdg-cell')).map(
        (gridCell) => gridCell.innerText
      );
    });
  }
}

function serialiseCellValue(value: unknown) {
  if (typeof value === 'string') {
    const formattedValue = value.replace(/"/g, '""');
    return formattedValue.includes(',') ? `"${formattedValue}"` : formattedValue;
  }
  return value;
}

function downloadFile(fileName: string, data: Blob) {
  const downloadLink = document.createElement('a');
  downloadLink.download = fileName;
  const url = URL.createObjectURL(data);
  downloadLink.href = url;
  downloadLink.click();
  URL.revokeObjectURL(url);
}
