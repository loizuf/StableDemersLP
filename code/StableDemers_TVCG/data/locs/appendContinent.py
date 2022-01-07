import sys

file1 = open(sys.argv[1], "r", encoding='latin1')
file2 = open(sys.argv[2], "r", encoding='latin1')
file3 = open(sys.argv[1]+"_c", "w", encoding='latin1')

data1 = file1.readlines()
data2 = file2.readlines()

cont = {}

for line in data2:
	tokens = line.split("\t")
	cont[tokens[1].strip()] = tokens[0]

for i in range(1, len(data1)):
	line = data1[i]
	code = line.split("\t")[0]
	file3.write(line.strip() + "\t" + cont[code] + "\n")