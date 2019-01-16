#!/bin/sh
#
# This script is designed to upgrade LabKey on this server
# usage: ./installLabKey.sh ${distribution}
#

set -x

labkey_home=/usr/local/labkey
cd /usr/local/src

#NOTE: corresponding changes must be made in javaWrapper.sh
MAJOR=18
MINOR=2
BRANCH=Discvr${MAJOR}${MINOR}_Installers
ARTIFACT=LabKey${MAJOR}.${MINOR}
MODULE_DIST_NAME=prime-seq-modules
PREMIUM=premium-${MAJOR}.${MINOR}.module
TOMCAT_HOME=/usr/share/tomcat

#first download
DATE=$(date +"%Y%m%d%H%M")
MODULE_ZIP=${ARTIFACT}-ExtraModules-${DATE}.zip
rm -Rf $MODULE_ZIP
wget --trust-server-names --no-check-certificate -O $MODULE_ZIP http://teamcity.labkey.org/guestAuth/repository/download/LabKey_${BRANCH}/.lastSuccessful/${MODULE_DIST_NAME}/${ARTIFACT}-{build.number}-ExtraModules.zip

GZ=${ARTIFACT}-${DATE}-discvr-bin.tar.gz
rm -Rf $GZ
wget --trust-server-names --no-check-certificate -O $GZ http://teamcity.labkey.org/guestAuth/repository/download/Labkey_${BRANCH}/.lastSuccessful/discvr/${ARTIFACT}-{build.number}-discvr-bin.tar.gz

#extract, find name
tar -xf $GZ
DIR=$(ls -tr | grep "^${ARTIFACT}*" | grep 'discvr-bin$' | tail -n -1)
echo "DIR: $DIR"
BASENAME=$(echo ${DIR} | sed 's/-discvr-bin//')
mv $GZ ./${BASENAME}-discvr-bin.tar.gz
mv $MODULE_ZIP ./${BASENAME}-ExtraModules.zip
GZ=${BASENAME}-discvr-bin.tar.gz
MODULE_ZIP=${BASENAME}-ExtraModules.zip

systemctl stop labkey.service

#extra modules first
rm -Rf ${labkey_home}/externalModules
mkdir -p ${labkey_home}/externalModules
rm -Rf modules_unzip
unzip $MODULE_ZIP -d ./modules_unzip
MODULE_DIR=$(ls ./modules_unzip | tail -n -1)
echo $MODULE_DIR
cp ./modules_unzip/${MODULE_DIR}/modules/*.module ${labkey_home}/externalModules
rm -Rf ./modules_unzip

#premium
if [ -e $PREMIUM ];then
        cp $PREMIUM ${labkey_home}/externalModules
fi

#main server
echo "Installing LabKey using: $GZ"
cd $DIR
./manual-upgrade.sh -u labkey -c /usr/local/tomcat -l $labkey_home --noPrompt
cd ../
systemctl start labkey.service

# clean up
echo "Removing folder: $DIR"
rm -Rf $DIR

echo "cleaning up installers, leaving 5 most recent"
ls -tr | grep "^${ARTIFACT}.*\.gz$" | head -n -5 | xargs rm

echo "cleaning up ZIP, leaving 5 most recent"
ls -tr | grep "^${ARTIFACT}.*\.zip$" | head -n -5 | xargs rm