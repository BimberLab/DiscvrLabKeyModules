#!/bin/bash

set -e
set -x

BASE_VERSION=`echo $TRAVIS_BRANCH | sed 's/[^0-9\.]*//g'`
BASE_VERSION_SHORT=`echo $BASE_VERSION | awk '{ print substr($0,1,4) }'`

if [[ -z $BASE_VERSION ]];then
    BASE_VERSION='develop'
    BASE_VERSION_SHORT='develop'
fi

echo "Base version inferred from branch: "$BASE_VERSION
echo "Short base version inferred from branch: "$BASE_VERSION_SHORT

#Determine a unique build dir, based on where we pull from:
BASEDIR=$HOME"/labkey_build/"$BASE_VERSION
if [ ! -e $BASEDIR ];then
    mkdir $BASEDIR
fi
cd $BASEDIR

# Download primary SVN repo
if [ $BASE_VERSION == 'develop' ];then
    SVN_URL=https://svn.mgt.labkey.host/stedi/trunk
    SVN_DIR=${BASEDIR}/trunk
    LK_GIT_BRANCH=develop
    BIMBER_GIT_BRANCH=develop
else
    SVN_URL=https://svn.mgt.labkey.host/stedi/branches/release${BASE_VERSION_SHORT}
    SVN_DIR=${BASEDIR}/release${BASE_VERSION}
    LK_GIT_BRANCH=release${BASE_VERSION_SHORT}
    BIMBER_GIT_BRANCH=$TRAVIS_BRANCH
fi

if [ ! -e $SVN_DIR ];then
    mkdir -p $SVN_DIR
    cd $BASEDIR
    svn co $SVN_URL
else
    cd $SVN_DIR
    svn revert --depth=infinity .
    svn update
fi

export RELEASE_NAME=`grep -e 'labkeyVersion=' ${SVN_DIR}/gradle.properties | sed 's/labkeyVersion=//'`
echo "Release name: "$RELEASE_NAME

function cloneGit {
    GIT_ORG=$1
    REPONAME=$2
    BRANCH=$3
    BASE=/server/modules/
    if [ -n "$4" ];then
        BASE=$4
    fi

    BRANCH_EXISTS=$(git ls-remote --heads https://github.com/${1}/${2}.git ${3} | wc -l)
    if [[ -z $BRANCH_EXISTS ]];then
        echo 'Branch not found, using default: develop'
        BRANCH='develop'
    fi

    TARGET_DIR=${SVN_DIR}${BASE}${REPONAME}
    GIT_URL=https://${GH_TOKEN}@github.com/${GIT_ORG}/${REPONAME}.git
    if [ ! -e $TARGET_DIR ];then
        cd ${SVN_DIR}${BASE}
        git clone -b $BRANCH $GIT_URL
    else
        cd ${SVN_DIR}${BASE}${REPONAME}
        git checkout $BRANCH
        git reset --hard HEAD
        git clean -f -d
        git pull
    fi
}

# Labkey/Platform
cloneGit Labkey platform $LK_GIT_BRANCH

# Labkey/distributions
cloneGit Labkey distributions $LK_GIT_BRANCH /

# Labkey/dataintegration
cloneGit Labkey dataintegration $LK_GIT_BRANCH /server/optionalModules/

# BimberLab/DiscvrLabKeyModules
cloneGit BimberLab DiscvrLabKeyModules $BIMBER_GIT_BRANCH

# BimberLabInternal/LabDevKitModules
cloneGit BimberLabInternal LabDevKitModules $BIMBER_GIT_BRANCH

# BimberLabInternal/BimberLabKeyModules
cloneGit BimberLabInternal BimberLabKeyModules $BIMBER_GIT_BRANCH

# Labkey/ehrModules.  Only retain Viral_Load_Assay
cloneGit Labkey ehrModules $LK_GIT_BRANCH /externalModules/
if [ -e ${SVN_DIR}/server/modules/Viral_Load_Assay ];then
    rm -Rf ${SVN_DIR}/server/modules/Viral_Load_Assay
fi
mv ${SVN_DIR}/externalModules/ehrModules/Viral_Load_Assay ${SVN_DIR}/server/modules/
rm -Rf  ${SVN_DIR}/externalModules/ehrModules

cd $SVN_DIR

# Modify gradle config:
echo "BuildUtils.includeModules(this.settings, rootDir, [BuildUtils.SERVER_MODULES_DIR, BuildUtils.OPTIONAL_MODULES_DIR], [], true)" >> settings.gradle

PROD_OPTS=
if [ -n "$TRAVIS_TAG" ];then
    echo "Performing pre-clean for production"
    PROD_OPTS=" cleanNodeModules cleanDist :server:cleanBuild"
fi

#make distribution
DIST_DIR=${TRAVIS_BUILD_DIR}/lkDist
if [ ! -e $DIST_DIR ];then
    mkdir -p $DIST_DIR ];
fi

GRADLE_OPTS=-Xmx2048m
./gradlew \
    -Dorg.gradle.daemon=false \
    -PincludeVcs \
    -PbuildFromSource=true \
    -PdeployMode=prod \
    $PROD_OPTS deployApp

./gradlew \
    -Dorg.gradle.daemon=false \
    -PincludeVcs \
    -PbuildFromSource=true \
    -PdeployMode=prod \
    -PmoduleSet=distributions \
    -PdistDir=$DIST_DIR \
    $PROD_OPTS :distributions:discvr:dist :distributions:discvr_modules:dist :distributions:prime-seq-modules:dist

mv ./dist/* $DIST_DIR

echo $RELEASE_NAME > ${TRAVIS_BUILD_DIR}/release.txt