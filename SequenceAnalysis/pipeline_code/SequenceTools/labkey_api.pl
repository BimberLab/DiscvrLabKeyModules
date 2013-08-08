#export $PERL5LIB = '/home/fedora/workspace/LabKey-API/src/Labkey/Query/lib:$PERL5LIB';

require '/home/fedora/workspace/LabKey-API/src/Labkey/Query/lib/Labkey/Query.pm';
use Data::Dumper;

my $results = Labkey::Query::selectRows(
	-baseUrl => 'https://www.labkey.org',
	-containerPath => 'home/Developer/',
	-schemaName => 'issues',
	-queryName => 'Issues',
	-maxRows => 4,
	-sort => '-id',
	-debug => 1,
	);


my $sql = Labkey::Query::executeSql(
	-baseUrl => 'https://www.labkey.org',
	-containerPath => 'home/Developer/',
	-schemaName => 'issues',
	-sql => 'SELECT i.IssueId FROM issues.Issues i',
	-debug => 1,
	);
	
print Dumper($sql);		