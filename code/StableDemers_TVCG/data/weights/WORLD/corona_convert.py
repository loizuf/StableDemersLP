import pandas as pd 
import numpy as np
import sys

All = pd.read_csv("All.tsv", sep='\t')
countries = []
for index, row in All.iterrows():
	countries.append(row["Country Code"])

converter = pd.read_csv("countries_codes_and_coordinates.csv") 
convert_table = {}
for index, row in converter.iterrows():
	convert_table[row["Alpha2"]] = row["Alpha3"]

data = pd.read_csv("WHO-COVID-19-global-data.csv")
out_data = {}
for index, row in data.iterrows():
	day = row["Date_reported"].replace("-", "")
	code = row["Country_code"]
	if code not in convert_table:
		continue
	code = convert_table[code]
	if code not in countries:
		continue

	if day not in out_data:
		out_data[day] = {}

	out_data[day][code] = row[sys.argv[1]]
	if int(row[sys.argv[1]]) < 0:
		print(str(day) + " in " + str(code) + " has " + str(row[sys.argv[1]]) + " " + str(sys.argv[1]))



df = pd.DataFrame.from_dict(out_data)
df = df.apply(pd.to_numeric)

df.index.name='Country Code'

for c in countries:
	if c not in df.index.values.tolist():
		print("no data found for " + c + ". Appending empty row.")
		df.append(pd.Series(name=c))
		for (columnName, columnData) in df.iteritems():
			df.set_value(c, columnName, np.nan)

percentages = {
	"-A": 0.01158,
	"-B": 0.61954,
	"-C": 0.33206,
	"-D": 0.17048,
	"-E": 0.43064,
	"-F": 0.51414,
	"-G": 0.00152,
	"-H": 0.00152,
	"-I": 0.00182,
	"-J": 0.01863,
	"-K": 0.00393,
	"-L": 0.00304,
	"-M": 0.00722,
	"-N": 0.04672,
	"-O": 0.02483
}

for code in percentages:
	print("Adding percentage value for " + code)
	df.append(pd.Series(name=code))
	#counter = 0;
	for (columnName, columnData) in df.iteritems():
		#if counter < 100: 
			#counter += 1
			#continue
		#print("Max is " + str(df[columnName].max()) + " and " + str(percentages[code]) + "\% are: " + str(df[columnName].max()*percentages[code]))
		df.set_value(code, columnName, df[columnName].max()*percentages[code])


#Now we replace the missing values with a rolling average
#for index, row in df.iterrows():
	#print(str(row))


df = df.fillna("null")
df.to_csv(sys.argv[1] + ".tsv", sep='\t')