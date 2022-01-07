import pandas as pd
import os
import sys

path = "/home/soeren.nickel/Work/projects/stable-cartograms/code/StableDemers_TVCG/data/weights/"
names_temp = {
	"NL": ["OAD", "OPP_WATER", "AANT_INW", "BEV_DICHTH"],
	"USA": ["DrugFatalities", "GDP", "GeneralElections", "Population"],
	"WORLD": ["GDP", "GDPCapita", "ForestArea", "RuralPopulation"]
}
names = names_temp[sys.argv[1]]

dfs = []

for name in names:
	df = pd.read_csv(path+sys.argv[1]+"/"+name+".tsv",sep="\t")
	df = df.sort_values(df.columns[0])
	print("df has "+ str(len(df.index)) + " lines")
	dfs.append(df)

# df1 = pd.read_csv(path+names[0]+".tsv",sep="\t")
# df1 = df1.sort_values(df1.columns[0])
# df2 = pd.read_csv(path+names[1]+".tsv",sep="\t")
# df2 = df2.sort_values(df2.columns[0])
# df3 = pd.read_csv(path+names[2]+".tsv",sep="\t")
# df3 = df3.sort_values(df3.columns[0])
# df4 = pd.read_csv(path+names[3]+".tsv",sep="\t")
# df4 = df4.sort_values(df4.columns[0])
# print(len(df1.index))
# print(len(df2.index))
# print(len(df3.index))
# print(len(df4.index))
# print(df1.tail())
# print(df2.tail())
# print(df3.tail())
# print(df4.tail())

# Names1 = set(df1[df1.columns[0]])
# print(len(Names1))
# Names2 = set(df2[df2.columns[0]])
# print(len(Names2))
# Names3 = set(df3[df3.columns[0]])
# print(len(Names3))
# Names4 = set(df4[df4.columns[0]])
# print(len(Names4))

# print("--------------------")
# print(Names1-Names2)
# print(Names1-Names3)
# print(Names1-Names4)

# print(df1[sys.argv[2]])

for date in sys.argv[2:]:
	# print(date)
	keys = ["code", "name"] + list(map(lambda x: str(x)+"_"+date, names))
	# print(keys)
	# print("HO!")
	# print(names)
	columns = []
	for df in dfs:
		columns.append(df[str(date)])
	new_df = pd.concat([dfs[0][dfs[0].columns[0]], dfs[0][dfs[0].columns[1]]]+columns, axis=1, sort=False, keys=keys)
	print(len(new_df.index))
	# new_df.join(df1[str(date)])
	# new_df.join(df2[str(date)])
	# new_df.join(df3[str(date)])
	# new_df.join(df4[str(date)])
	# print(new_df.head())
	new_df.to_csv(path+sys.argv[1]+"/Mixed_"+date+".tsv",sep='\t',na_rep='null',index=False)



