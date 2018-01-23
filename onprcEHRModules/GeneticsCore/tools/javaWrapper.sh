#!/bin/bash

# this script is used to wrap the cluster java process for OHSU/exacloud
# the purpose is to:
# 1) set umask to 0002
# 2) if an incoming job uses the WEEK_LONG_JOB flag, instead of using the core LK install dir, we
# make a local copy for this job.  this means we can more easily push out new builds while these long jobs are running
# 3) if  an incoming job uses the WEEK_LONG_JOB flag, we also make an alternate TEMP directory on lustre (the default is the node's local disk)
# and change the stripe

set -e
set -u
set -x

echo $JAVA_HOME

ORIG_WORK_DIR=$(pwd)

JAVA_HOME=/home/exacloud/lustre1/prime-seq/java/current
JAVA=${JAVA_HOME}/bin/java
LK_DIR=/home/exacloud/lustre1/prime-seq/labkey
LK_SRC_DIR=/home/exacloud/lustre1/prime-seq/src
TEMP_BASEDIR=/home/exacloud/lustre1/prime-seq/tempDir
PATH=${JAVA_HOME}/bin/:$PATH
GZ_PREFIX=LabKey17.3

umask 0006

#expect args like:
#/home/groups/prime-seq/exacloud/java/current/bin/java -Xmx8g -cp /home/groups/prime-seq/exacloud/labkey/labkeyBootstrap.jar org.labkey.bootstrap.ClusterBootstrap -modulesdir=/home/groups/prime-seq/exacloud/labkey/modules -webappdir=/home/groups/prime-seq/exacloud/labkey/labkeywebapp -configdir=/home/groups/prime-seq/exacloud/labkey/config file:/home/groups/prime-seq/production/Internal/Bimber/19/@files/sequenceAnalysis/SequenceAnalysis_20160603_9/SequenceAnalysis_20160603_9.job.xml

JOB_FILE=${!#}
JOB_FILE=${JOB_FILE//file:/}

# Note: to make updates easier, copy code for every job, not just week-long ones
# also string replace "/home/groups/prime-seq/exacloud/labkey/" for local dir in arguments

BASENAME=`basename $JOB_FILE '.job.xml'`

#make new temp directory
TEMP_DIR=`mktemp -d --tmpdir=$TEMP_BASEDIR --suffix=$BASENAME`
echo $TEMP_DIR


function finish {
    echo "cleaning up temp dir"
    rm -Rf $TEMP_DIR

    if [ -e $LABKEY_HOME ];then
        rm -Rf $LABKEY_HOME
    fi
}

trap finish EXIT SIGHUP SIGINT SIGTERM SIGKILL SIGQUIT SIGSTP

mkdir -p $TEMP_DIR
lfs setstripe -c 1 $TEMP_DIR

export TEMP_DIR=$TEMP_DIR
export TMPDIR=$TEMP_DIR
export TMP=$TEMP_DIR
export TEMP=$TEMP_DIR

#this should let us verify the above worked
LOCAL_TEMP_DIR=`mktemp -d --tmpdir=/tmp --suffix=$BASENAME`
LABKEY_HOME=${LOCAL_TEMP_DIR}/"labkey"
if [ -e $LABKEY_HOME ];then
    rm -Rf $LABKEY_HOME
fi

mkdir -p $LABKEY_HOME

#try/catch/finally
{
    #copy relevant code locally

    #NOTE: instead of copying exploded files, copy archive and explode on node to reduce network I/O
    #cp -R -p $LK_DIR/labkeyBootstrap.jar $LABKEY_HOME
    #cp -R -p $LK_DIR/labkeywebapp $LABKEY_HOME
    #cp -R -p $LK_DIR/modules $LABKEY_HOME
    #cp -R -p $LK_DIR/pipeline-lib $LABKEY_HOME
    #cp -R -p $LK_DIR/externalModules $LABKEY_HOME
    #cp -R -p $LK_DIR/config $LABKEY_HOME

    cd $LABKEY_HOME
    MODULE_ZIP=$(ls -tr $LK_SRC_DIR | grep "^${GZ_PREFIX}.*\.zip$" | tail -n -1)
    rm -Rf ${LABKEY_HOME}/externalModules
    mkdir -p ${LABKEY_HOME}/externalModules
    if [ -e modules_unzip ];then
        rm -Rf modules_unzip
    fi

    cp ${LK_SRC_DIR}/$MODULE_ZIP ./

    unzip $MODULE_ZIP -d ./modules_unzip
    MODULE_DIR=$(ls ./modules_unzip | tail -n -1)
    cp ./modules_unzip/${MODULE_DIR}/modules/*.module ${LABKEY_HOME}/externalModules
    rm -Rf ./modules_unzip
    rm -Rf $MODULE_ZIP

    GZ=$(ls -tr $LK_SRC_DIR | grep "^${GZ_PREFIX}.*\.gz$" | tail -n -1)
    cp ${LK_SRC_DIR}/$GZ ./

    gunzip $GZ
    TAR=`echo $GZ | sed -e "s/.gz$//"`
    echo "TAR: $TAR"
    tar -xf $TAR
    DIR=`echo $TAR | sed -e "s/.tar$//"`
    echo "DIR: $DIR"
    cd $DIR

    print_error()
    {
        echo ""
        echo " ERROR: The upgrade did not complete successfully. See the error message"
        echo " above for more information "
        echo ""
        exit 1

    }

    echo ""
    echo " Remove the currently installed LabKey binaries from the $LABKEY_HOME directory"
    rm -rf $LABKEY_HOME/modules
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed
    rm -rf $LABKEY_HOME/labkeywebapp
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed
    rm -rf $LABKEY_HOME/pipeline-lib
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed

    #
    # Install the new version of LabKey in LABKEY_HOME
    #
    echo ""
    echo " Install the new version of LabKey into $LABKEY_HOME directory "
    cp -R modules $LABKEY_HOME
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed
    cp -R labkeywebapp $LABKEY_HOME
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed
    cp -R pipeline-lib $LABKEY_HOME
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed
    cp -f tomcat-lib/labkeyBootstrap.jar $LABKEY_HOME
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed

    cp -R $LK_DIR/config $LABKEY_HOME
    if [ $? != 0 ]; then print_error; fi # exit if the last command failed

    cd $ORIG_WORK_DIR
    rm -Rf $DIR
    rm -Rf $TAR


    #edit arguments
    updatedArgs=( "$@" )
    for(( a=0; a<${#updatedArgs[@]}-1 ;a++ ));  do
        arg=${updatedArgs[$a]}
        #echo $arg

        #if matches origial dir, replace path
        TO_SUB=$LK_DIR
        updatedArgs[$a]=${arg//$TO_SUB/$LABKEY_HOME}
    done

    #also add /externalModules
    lastArg=${updatedArgs[${#updatedArgs[@]} - 1]}
    updatedArgs[${#updatedArgs[@]} - 1]="-Dlabkey.externalModulesDir="${LABKEY_HOME}"/externalModules"
    updatedArgs[${#updatedArgs[@]}]=$lastArg

    #add -Djava.io.tmpdir
    ESCAPE=$(echo $TEMP_DIR | sed 's/\//\\\//g')
    sed -i 's/<!--<entry key="JAVA_TMP_DIR" value=""\/>-->/<entry key="JAVA_TMP_DIR" value="'$ESCAPE'"\/>/g' ${LABKEY_HOME}/config/pipelineConfig.xml
    $JAVA -Djava.io.tmpdir=${TEMP_DIR} ${updatedArgs[@]}

} || {
    echo "ERROR RUNNING JOB"
}