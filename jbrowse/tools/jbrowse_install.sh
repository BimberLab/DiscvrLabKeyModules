#!/bin/bash

echo ""
echo ""
echo "%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%"
echo "Installing JBrowse to the current working directory"
echo ""

wget http://jbrowse.org/releases/JBrowse-1.11.4.zip
unzip JBrowse-1.11.4.zip
rm JBrowse-1.11.4.zip
mv JBrowse-1.11.4/* ./
rm -Rf JBrowse-1.11.4
./setup.sh

