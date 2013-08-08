SELECT
  d.Id,
  g.isU42,
  s.spfStatus,
  s.bloodVol as serologyBloodVol,
  g.parentageBloodDrawVol,
  g.mhcBloodDrawVol,
  g.dnaBloodDrawVol,
  g.totalBloodDrawVol as geneticsBloodVol,
  coalesce(s.bloodVol, 0) + coalesce(g.totalBloodDrawVol, 0) as totalBloodDrawVol

FROM study.demographics d

LEFT JOIN study.processingSerology s ON (d.Id = s.Id)
LEFT JOIN study.processingGeneticsBloodDraws g ON (g.Id = s.Id)