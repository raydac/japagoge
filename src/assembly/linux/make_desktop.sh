#!/bin/bash

# Script just generates free desktop descriptor to start application

JAPAGOGE_HOME="$(realpath $(dirname ${BASH_SOURCE[0]}))"
TARGET=$JAPAGOGE_HOME/japagoge.desktop

echo [Desktop Entry] > $TARGET
echo Encoding=UTF-8 >> $TARGET
echo Name=Japagoge >> $TARGET
echo Comment=Japagoge grabber >> $TARGET
echo GenericName=Japagoge >> $TARGET
echo Exec=$JAPAGOGE_HOME/run.sh >> $TARGET
echo Terminal=false >> $TARGET
echo Type=Application >> $TARGET
echo Icon=$JAPAGOGE_HOME/icon.svg >> $TARGET
echo "Categories=Application;" >> $TARGET
echo "Keywords=apng;grabber;japagoge;gif;png;peek;" >> $TARGET
echo StartupWMClass=JapagogeGrabber >> $TARGET
echo StartupNotify=true >> $TARGET

echo Desktop script has been generated: $TARGET

if [ -d ~/.gnome/apps ]; then
    echo copy to ~/.gnome/apps
    cp -f $TARGET ~/.gnome/apps
fi

if [ -d ~/.local/share/applications ]; then
    echo copy to ~/.local/share/applications
    cp -f $TARGET ~/.local/share/applications
fi

if [ -d ~/Desktop ]; then
    echo copy to ~/Desktop
    cp -f $TARGET ~/Desktop
fi

