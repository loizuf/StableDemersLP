#!/bin/bash

yes | apt install unzip
source javainstaller.sh

yes | apt install git
git clone "https://github.com/loizuf/StableDemersLP.git"
