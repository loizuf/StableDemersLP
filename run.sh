#!/bin/bash

#source prepare.sh
cd StableDemersLP/code/StableDemers_TVCG
java -Djava.library.path=lib -Xmx8192m -jar StableDemers_TVCG.jar -O testing/outs/ -T data/topo/USA_adjacencies.tsv -L data/locs/USA_locs.tsv -S data/stability/complete-4.tsv -W data/weights/USA/Mixed.tsv -B data/bbs/USA.tsv -N data/norm/Sum-Ind-4.tsv -NoI -NoR -lpC1 -lpC3 1 -lpC7 0.01
