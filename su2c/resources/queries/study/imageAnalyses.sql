SELECT
  a.imageId,
  group_concat((a.subset || ': ' || a.cellNumber || ' / ' || a.biomarker || ': ' || a.total), chr(10)) as analyses

FROM study.Quantitative_Image_Analysis a

GROUP BY a.imageId