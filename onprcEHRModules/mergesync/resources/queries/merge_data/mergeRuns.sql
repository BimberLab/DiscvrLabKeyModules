select
t.TS_Stat as status,

pr.PR_FNAME,
pr.PR_LNAME,

o.O_ACCNUM as accession,
p.Pt_Lname as animalId,
o.O_DATE as date,
i.INS_NAME as project,
o.O_COLLDT as datecollected,
ti.T_ABBR as servicename_abbr,
ti.T_NAME as servicename

--one row per batch of orders
FROM Orders o

--many panels could be ordered at a time
--TODO: what about failures and repeats?
LEFT JOIN tests t ON (
	t.TS_ACCNR = o.O_ACCNUM
)

--contains reference info about that panel
LEFT JOIN TESTINFO ti ON (
	ti.T_TSTNUM = t.TS_TNUM
)

--there should only be 1 patient per order
LEFT JOIN patients p ON (
	o.O_PTNUM = p.PT_NUM
	--and Isnumeric(p.pt_lname) = 1   ---- Only process numeric last names
)

--performed by
LEFT JOIN PRSNL pr ON (
	o.O_DOCTOR = pr.pr_num
)

--there should only be 1 visit per order
LEFT JOIN VISITS v ON (
	o.O_VID = v.V_ID
)

--join to visit/insurance
LEFT JOIN Vis_Ins vi ON (
	vi.VINS_VID = v.V_ID
)

--join to insurance for project
left join INSURANCE i ON (
	i.INS_INDEX = vi.VINS_INS1
)