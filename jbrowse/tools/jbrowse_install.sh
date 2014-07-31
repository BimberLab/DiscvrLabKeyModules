#!/bin/bash

set -e
set -u
INSTALL_DIR=

while getopts "d:" arg;
do
  case $arg in
    d)
       INSTALL_DIR=$OPTARG
       INSTALL_DIR=${INSTALL_DIR%/}
       echo "INSTALL_DIR = ${INSTALL_DIR}"
       ;;
    *)
       echo "The following arguments are supported:"
       echo "-d: the path to the labkey install, such as /usr/local/labkey.  If only this parameter is provided, tools will be installed in bin/ and src/ under this location."
       echo "-f: optional.  If provided, all tools will be reinstalled, even if already present"
       echo "Example command:"
       echo "./jbrowse_install.sh -d /var/www/jbrowse"
       exit 1;
      ;;
  esac
done

if [ -z $INSTALL_DIR ];
then
    echo "Must provide the install location using the argument -d"
    exit 1;
fi


echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Install location"
echo ""
echo "INSTALL_DIR: $INSTALL_DIR"

mkdir -p $INSTALL_DIR
cd $INSTALL_DIR

wget http://jbrowse.org/releases/JBrowse-1.11.4.zip
unzip JBrowse-1.11.4.zip
rm JBrowse-1.11.4.zip
mv JBrowse-1.11.4/* ./
rm -Rf JBrowse-1.11.4
./setup.sh

