import sys

adj_file = open(sys.argv[1], "r", encoding='latin1')
wght_file = open(sys.argv[2], "r", encoding='latin1')
mod_adj_file = open(sys.argv[2]+"_2", "w", encoding='latin1')


lines = adj_file.readlines()
percentages = {}

for line in lines:
	tokens = line.strip().split("\t")
	if tokens[0].startswith("-"):
		percentages[tokens[0]] = tokens[1]

#print(percentages)

lines = wght_file.readlines()
weights = {}
name = {}

mod_adj_file.write(lines[0])

for line_n in range(1,len(lines)):
	line = lines[line_n]
	tokens = line.strip().split("\t")
	name[tokens[0]] = tokens[1]
	if(not tokens[0].startswith("-")):
		weights[tokens[0]] = [tokens[1], tokens[2], tokens[3], tokens[4], tokens[5], tokens[6], tokens[7], tokens[8], tokens[9], tokens[10], tokens[11], tokens[12]]
	else:
		weights[tokens[0]] = [0] * 12
	#print(weights[tokens[0]])

for i in range(1, 12):
	max = 0.0
	for code, weight_line in weights.items():
		if (code.startswith("-")):
			continue
		if (max < float(weight_line[i])):
			max = float(weight_line[i])

	for code, weight_line in weights.items():
		if (not code.startswith("-")):
			continue
		weights[code][i] = (float(percentages[code])*max)
#print(weights)

for code, weight_line in weights.items():
	line = str(code)
	for i in range(1, 12):
		line += "\t" + str(weights[code][i])
	mod_adj_file.write(line + "\n")
mod_adj_file.close()