
PARAMETERS(GroupName VARCHAR)
SELECT *
FROM core.Users
WHERE UserId in (
   SELECT UserId
   FROM core.Members m
   WHERE m.GroupId.Name = GroupName
)