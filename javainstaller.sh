#!/bin/bash

home_path = "$(getent passwd $SUDO_USER | cut -d: -f6)"

cd home_path && wget https://raw.githubusercontent.com/chrishantha/install-java/master/install-java.sh

chmod +x install-java.sh

wget https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.tar.gz

yes | sudo -E ./install-java.sh -f ./jdk-17_linux-x64_bin.tar.gz -p /usr/lib/jvm
