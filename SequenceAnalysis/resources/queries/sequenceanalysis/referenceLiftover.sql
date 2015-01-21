SELECT
  r.rowid,
  r.name,
  cf.genomeId1,
  cf.genomeId2,

FROM sequenceanalysis.ref_nt_sequences r
JOIN sequenceanalysis.reference_library_members rlm ON (r.rowid = rlm.ref_nt_id)
JOIN sequenceanalysis.reference_libraries rl ON (rl.rowid = rlm.library_id)
JOIN sequenceanalysis.chain_files cf ON (cf.genomeId1 = rl.rowid AND cf.dateDisabled IS NULL)
