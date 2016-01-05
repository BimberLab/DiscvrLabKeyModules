#!/bin/sh
#
# This script is designed to upgrade LabKey on this server
# usage: ./installLabKey.sh ${distribution}
#

BRANCH=LabkeyDISCVR153_Installers
PROJECT_NAME=LabKey15.3DISCVR
DIST_NAME=discvr
MODULE_DIST_NAME=prime-seq-modules
LK_HOME=/usr/local/labkey

cd /usr/local/src

#extra module first
wget -r --trust-server-names --no-check-certificate http://teamcity.labkey.org/guestAuth/repository/download/LabKey_${BRANCH}/.lastSuccessful/${MODULE_DIST_NAME}/${PROJECT_NAME}-{build.number}-ExtraModules.zip
mv ./teamcity.labkey.org/guestAuth/repository/download/${BRANCH}/.lastSuccessful/${MODULE_DIST_NAME}/*.zip ./
rm -Rf ./teamcity.labkey.org
MODULE_ZIP=$(ls -tr | grep '^LabKey.*\.zip$' | tail -n -1)
rm -Rf ${LK_HOME}/externalModules
mkdir -p ${LK_HOME}/externalModules
rm -Rf modules_unzip
unzip $MODULE_ZIP -d ./modules_unzip
MODULE_DIR=$(ls ./modules_unzip | tail -n -1)
cp ./modules_unzip/${MODULE_DIR}/modules/*.module ${LK_HOME}/externalModules
rm -Rf ./modules_unzip
rm -Rf $MODULE_ZIP

GZ=$1
if [ $# -eq 0 ]; then
    wget -r --trust-server-names --no-check-certificate http://teamcity.labkey.org/guestAuth/repository/download/LabKey_${BRANCH}/.lastSuccessful/${DIST_NAME}/${PROJECT_NAME}-{build.number}-${DIST_NAME}-bin.tar.gz
    mv ./teamcity.labkey.org/guestAuth/repository/download/${BRANCH}/.lastSuccessful/${DIST_NAME}/*.gz ./
    rm -Rf ./teamcity.labkey.org
    GZ=$(ls -tr | grep '^LabKey.*\.gz$' | tail -n -1)
fi

echo "Installing LabKey using: $GZ"
echo "Unzipping $GZ"
gunzip $GZ
TAR=`echo $GZ | sed -e "s/.gz$//"`
echo "TAR: $TAR"
tar -xf $TAR
DIR=`echo $TAR | sed -e "s/.tar$//"`
echo "DIR: $DIR"
cd $DIR
./manual-upgrade.sh -u labkey -c /usr/local/tomcat -l $LK_HOME --noPrompt

# clean up
cd ../
echo "Removing folder: $DIR"
rm -Rf $DIR
echo "GZipping distribution"
gzip $TAR

echo "cleaning up installers, leaving 5 most recent"
ls -tr | grep '^LabKey.*\.gz$' | head -n -5 | xargs rm
