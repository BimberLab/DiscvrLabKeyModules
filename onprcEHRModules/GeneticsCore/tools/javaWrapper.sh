#!/bin/bash

# this script is used to wrap the cluster java process.  for OHSU/exacloud
# the purpose is to:
# 1) set umask to 0002
# 2) if an incoming job uses the WEEK_LONG_JOB flag, instead of using the core LK install dir, we
# make a local copy for this job.  this means we can more easily push out new builds while these long jobs are running

set -e
set -u
set -x

JAVA_HOME=/home/groups/prime-seq/exacloud/java/current
JAVA=${JAVA_HOME}/bin/java

umask 0002

TMPDIR=`dirname $(mktemp -u -t tmp.XXXXXXXXXX)`
echo $TMPDIR

#expect args like:
#/home/groups/prime-seq/exacloud/java/current/bin/java -Xmx8g -cp /home/groups/prime-seq/exacloud/labkey/labkeyBootstrap.jar org.labkey.bootstrap.ClusterBootstrap -modulesdir=/home/groups/prime-seq/exacloud/labkey/modules -webappdir=/home/groups/prime-seq/exacloud/labkey/labkeywebapp -configdir=/home/groups/prime-seq/exacloud/labkey/config file:/home/groups/prime-seq/production/Internal/Bimber/19/@files/sequenceAnalysis/SequenceAnalysis_20160603_9/SequenceAnalysis_20160603_9.job.xml

#test if contains WEEK_LONG_JOB
JOB_FILE=${!#}
JOB_FILE=${JOB_FILE//file:/}
JOB_DIR=`dirname $JOB_FILE`
CONDOR_SCRIPT=
for name in `ls ${JOB_DIR}/condor* | sed -e 's/\.submit//' | sort` ; do
	 CONDOR_SCRIPT=$name;
done

CONDOR_SCRIPT=${CONDOR_SCRIPT}.submit

#if true, copy first
#also string replace "/home/groups/prime-seq/exacloud/labkey/" for local dir in arguments
if grep -lq 'WEEK_LONG_JOB' $CONDOR_SCRIPT ;then
	echo "week long job"

	DIR_NAME=`basename $JOB_FILE '.job.xml'`
	DIR_NAME=${TMPDIR}/"LK_"$DIR_NAME
	if [ -e $DIR_NAME ];then
		rm -Rf $DIR_NAME
	fi

	mkdir -p $DIR_NAME

	#try/catch/finally
	{
		#copy relevant code locally
		LK_DIR=/home/groups/prime-seq/exacloud/labkey
		cp $LK_DIR/labkeyBootstrap.jar $DIR_NAME
		cp $LK_DIR/labkeywebapp $DIR_NAME
		cp $LK_DIR/modules $DIR_NAME
		cp $LK_DIR/pipeline-lib $DIR_NAME
		cp $LK_DIR/externalModules $DIR_NAME

		#edit arguments
		updatedArgs=( "$@" )
		for(( a=0; a<${#updatedArgs[@]}-1 ;a++ ));  do
			arg=${updatedArgs[$a]}
			echo $arg

			#skip config dir
			if [[ $arg == *"configdir"* ]];then
				updatedArgs[$a]=$arg
				continue
			fi

			#if matches origial dir, replace path
			TO_SUB=$LK_DIR
			updatedArgs[$a]=${arg//$TO_SUB/$DIR_NAME}
		done

		#also add /externalModules
		lastArg=${updatedArgs[${#updatedArgs[@]} - 1]}
		updatedArgs[${#updatedArgs[@]} - 1]="-Dlabkey.externalModulesDir="${DIR_NAME}"/externalModules"
		updatedArgs[${#updatedArgs[@]}]=$lastArg

		$JAVA ${updatedArgs[@]}
	} || {
		echo "ERROR RUNNING JOB"
	}

	echo "cleaning up temp dir"
	rm -Rf $DIR_NAME
else
	#if not a week long job, just run it
	echo "not week long job"
	$JAVA "$@"
fi

