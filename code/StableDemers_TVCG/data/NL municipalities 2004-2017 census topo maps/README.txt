The following files have been derived from the "wijk en buurt" data openly accessible and provided by the CBS (Dutch Census Bureau / Statistics NL).

*** gem_2004_2017.tsv ***
tsv containing three columns:
1) computed centroid coordinate (given as space-separated x y coordinates)
2) code of the municipality
3) name of the municipality

*** gem_20XX.tsv ***
tsv containing many columns. The above three columns are matched by:
1) COMPUTE_CENTROID
2) GM_CODE
3) GM_NAAM
Each year has its own file. Note that centroid might shift slightly, due to different data files/resolution/boundaries changing a bit. Taking one for a single municipality should be OK... but could also be varying over time ("kinetic data"...)
Note that the later years (after 2010) have many more columns and generally not all data is present throughout all years. The abbreviations are explained in Dutch in the attrached sample of PDF files that came with the original source.

*** gem_20XX_topo.tsv ***
tsv file that just contains a pair of GM_CODE (as in the above files) per line, representing an adjacency. This has been derived from the provided maps, checking whether there is a vertex of region 1 "close enough" to vertex of region 2. This may result in adjacencies being missed (to be checked) and it's not immediately clear how the islands are handled (also might be inconsistent between years -- there is a big jump from 2009 to 2010 in the detail in the maps)

