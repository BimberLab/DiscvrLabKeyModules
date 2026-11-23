SELECT
  s.Id,
  count(s.Id) as totalProjects,
  group_concat(DISTINCT s.study, char(10)) as allStudiesAndAnalyses,
  group_concat(DISTINCT CASE WHEN s.category = 'Analysis Cohorts' THEN NULL ELSE s.study END, char(10)) as allStudies,
  group_concat(DISTINCT CASE WHEN s.category = 'Analysis Cohorts' THEN s.study ELSE NULL END, char(10)) as analysisGroups,
  group_concat(DISTINCT s.cohortId.studyId.description, char(10)) as studyDescription,
  group_concat(DISTINCT s.category, char(10)) as categories,
  group_concat(DISTINCT s.subgroup, char(10)) as subgroups

FROM study.assignment s
GROUP BY s.Id