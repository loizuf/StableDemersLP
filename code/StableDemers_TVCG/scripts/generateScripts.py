#lp_scripts = open("scripts")

# content = """
# name="${a}_${b}_${c}_${d}_${e}"
# #echo $name
# echo ${HOSTNAME}

# java -Djava.library.path="/home1/share/ILOG/cplex-12.8.0/cplex/bin/x86-64_linux" -jar /home1/soeren.nickel/StableDemers_TVCG/StableDemers_TVCG.jar -O "${TMPDIR}/TVCG_outs/" -T "/home1/soeren.nickel/StableDemers_TVCG/data/topo/${a}_adjacencies.tsv" -L "/home1/soeren.nickel/StableDemers_TVCG/data/locs/${a}_locs.tsv" -S "/home1/soeren.nickel/StableDemers_TVCG/data/stability/${e}.tsv" -W "/home1/soeren.nickel/StableDemers_TVCG/data/weights/${a}/${b}.tsv" -B "/home1/soeren.nickel/StableDemers_TVCG/data/bbs/${a}.tsv" -N "/home1/soeren.nickel/StableDemers_TVCG/data/norm/Sum-${g}-${f}.tsv" -A "${c}" -NoG -NoM -lpC1 -lpC3 1

# # Create and/or clear directory on home1
# mkdir -p "/home1/soeren.nickel/TVCG_outs/${name}"
# rm -f /home1/soeren.nickel/TVCG_outs/${name}/*

# # compress and move files
# xz "${TMPDIR}/TVCG_outs/${name}/${name}_eval.json"
# mv "${TMPDIR}/TVCG_outs/${name}/${name}_eval.json.xz" "/home1/soeren.nickel/TVCG_outs/${name}/"
# mv "${TMPDIR}/TVCG_outs/${name}/${name}_regions.json" "/home1/soeren.nickel/TVCG_outs/${name}/"
# mv "${TMPDIR}/TVCG_outs/${name}/${name}.ipe" "/home1/soeren.nickel/TVCG_outs/${name}/"
# """

import os
content0 = "#!/bin/bash\n\n"

content1 = """

e="${h}-${f}"

name="${a}_${b}_${c}_${d}(${e})"
#echo $name
echo ${HOSTNAME}

java -Djava.library.path="/home1/share/ILOG/cplex-12.8.0/cplex/bin/x86-64_linux" """

content3 = """-jar /home1/soeren.nickel/StableDemers_TVCG/StableDemers_TVCG.jar -O "${TMPDIR}/TVCG_outs/" -T "/home1/soeren.nickel/StableDemers_TVCG/data/topo/${a}_adjacencies.tsv" -L "/home1/soeren.nickel/StableDemers_TVCG/data/locs/${a}_locs.tsv" -S "/home1/soeren.nickel/StableDemers_TVCG/data/stability/${e}.tsv" -W "/home1/soeren.nickel/StableDemers_TVCG/data/weights/${a}/${b}.tsv" -B "/home1/soeren.nickel/StableDemers_TVCG/data/bbs/${a}.tsv" -N "/home1/soeren.nickel/StableDemers_TVCG/data/norm/Sum-${g}-${f}.tsv" -A "${c}" -NoG -NoM"""

content2 = """# Create and/or clear directory on home1
mkdir -p "/home1/soeren.nickel/TVCG_outs/${name}"
rm -f /home1/soeren.nickel/TVCG_outs/${name}/*

# compress and move files
xz "${TMPDIR}/TVCG_outs/${name}/${name}_eval.json"
mv "${TMPDIR}/TVCG_outs/${name}/${name}_eval.json.xz" "/home1/soeren.nickel/TVCG_outs/${name}/"
mv "${TMPDIR}/TVCG_outs/${name}/${name}_regions.json" "/home1/soeren.nickel/TVCG_outs/${name}/"
mv "${TMPDIR}/TVCG_outs/${name}/${name}.ipe" "/home1/soeren.nickel/TVCG_outs/${name}/"
"""


heap = {
	"USA": 7000,
	"WORLD": 14000,
	"NL": 40000
}


commandline = ""

# path = "/home1/soeren.nickel/StableDemers_TVCG/"
# dataD = "data/"
# outD = "experiments/"

AS=["WORLD","USA","NL"]
BS={
	"WORLD":["ForestArea", "GDP", "GDPCapita", "RuralPopulation", "Mixed_2013", "Mixed_2014", "Mixed_2015", "Mixed_2016", "Cumulative_cases"],
	"USA":["DrugFatalities", "GDP", "GeneralElectionTurnout", "Population", "Mixed_2010", "Mixed_2012", "Mixed_2014", "Mixed_2016"],
	"NL":["AANT_INW", "BEV_DICHTH", "OAD", "OPP_WATER", "Mixed_2013", "Mixed_2014", "Mixed_2015", "Mixed_2016"]
}
# BS={
# 	"WORLD":["Mixed_2013", "Mixed_2014", "Mixed_2015", "Mixed_2016"],
# 	"USA":["Mixed_2010", "Mixed_2012", "Mixed_2014", "Mixed_2016"],
# 	"NL":["Mixed_2013", "Mixed_2014", "Mixed_2015", "Mixed_2016"]
# }
CS=["LP", "Force"]
# DS={
# 	"LP":["13", "123", "134", "1234", "15", "125", "145", "1245", "16", "126", "137", "1237", "1347", "12347", "157", "1257", "1457", "12457", "167", "1267"],
# 	"Force":["12", "13", "124", "134"]
# }
DS={
	"LP":["13", "123", "134", "1234", "16", "126", "137", "1237", "1347", "12347", "167", "1267", "15", "125"],
	"Force":["12", "13", "124", "134"]
}
FS={
	"WORLD":"11",
	"USA":"10",
	"NL":"14",
	"Mixed":"4",
	"Corona": "79"
}
HS=["star", "star-it", "chain", "chain-it", "complete", "none"]
GS=["All", "Ind"]

# counter = 0;
for a in AS:
	for b in BS[a]:
		for c in CS:
			for d in DS[c]:
				for h in HS:
					if b.startswith("Mixed"):
						f = FS["Mixed"]
						g = "Ind"
					elif b == "Cumulative_cases":
						f = FS["Corona"]
						g = "All"
					else:
						f = FS[a]
						g = "All"
					commandline = ""
					# temp = ""
					# if "5" in d:
					# 	temp = "I"
					# name = a + "_" + b + "_" + str(temp) + c + "_" + d + "(" + h + "-" + f + ")"
					name = a + "_" + b + "_" + c + "_" + d + "(" + h + "-" + f + ")"
					print(name)
					commandline += content0 + "a=\"" + a + "\"\nb=\"" + b + "\"\nc=\"" + c + "\"\nd=\"" + d + "\"\nf=\"" + f + "\"\nh=\"" + h + "\"\ne=\"${h}-${f}\"\ng=\"" + g + "\""
					flags = ""
					if c=="LP":
						if "7" not in d and h != "none":
							continue
						if "7" in d and h == "none":
							continue
						if "5" in d and h != "none":
							continue
						for char in d:
							flags += " -lpC" + char
							if int(char) == 3:
								flags += " 1"
							elif int(char) == 4:
								flags += " 0.01"
							elif int(char) == 5:
								flags += " 1"
							elif int(char) == 6:
								flags += " 1"
							elif int(char) == 7:
								flags += " 0.01" 
					elif c=="Force":
						if "-it" in h:
							continue
						if "4" not in d and h != "none":
							continue 
						if "4" in d and h == "none":
							continue 
						for char in d:
							flags += " -fC" + char
							if int(char) == 1:
								flags += " 40"
							elif int(char) == 2:
								flags += " 10"
							elif int(char) == 3:
								flags += " 10"
							elif int(char) == 4:
								flags += " 1"
						flags += " -fMM 0.0001 -fCo 0.0001"
					commandline += content1 + ("", "-Xmx"+str(heap[a])+"M ")[c=="LP"] + content3.rstrip() + flags + "\n" + content2
					# file = open("scripts3/"+a+"_"+temp+c+"/"+name+".sh", "w")

					if not os.path.exists("scripts4/"+a+"_"+c):
						os.makedirs("scripts4/"+a+"_"+c)

					file = open("scripts4/"+a+"_"+c+"/"+name+".sh", "w")
					file.write(commandline)
					# counter += 1