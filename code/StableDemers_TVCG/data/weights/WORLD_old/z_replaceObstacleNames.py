import sys

adj_file = open(sys.argv[1], "r", encoding='latin1')
mod_adj_file = open(sys.argv[1]+"_2", "w", encoding='latin1')

data = adj_file.read()
for i in range(9):
	data = data.replace("_"+str(i), "-"+str(i))
for i in range(10, 23):
	data = data.replace("_"+str(i), "-"+str(i))
mod_adj_file.write(data)