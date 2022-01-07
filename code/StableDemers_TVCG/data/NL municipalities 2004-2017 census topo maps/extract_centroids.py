import sys

file1 = open(sys.argv[1], "r", encoding='latin1')
file2 = open("new"+sys.argv[1], "w", encoding='latin1')

Lines = file1.readlines() 

tokens = Lines[0].split()

fileline = "GM_CODE\tCOMPUTE_CENTROID_x\tCOMPUTE_CENTROID_y\n";

for i in range(1, len(Lines)): 
	line = Lines[i]
	print(line)
	tokens = line.split()
	fileline += tokens[2] + "\t" + tokens[0] + "\t" + tokens[1] + "\n"

file2.write(fileline)