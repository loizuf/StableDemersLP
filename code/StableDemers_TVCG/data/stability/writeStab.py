import sys


if(sys.argv[1] == "complete"):
	stab_file = open(sys.argv[1] + "-" + sys.argv[2] + ".tsv", "w", encoding='latin1')
	stab_file.write("from\tto\n")

	for i in range(int(sys.argv[2])):
		for j in range(int(sys.argv[2])):
			if(i == j):
				continue
			stab_file.write(str(i) + "\t" + str(j) + "\n")


if(sys.argv[1] == "star"):
	stab_file = open(sys.argv[1] + "-" + sys.argv[2] + ".tsv", "w", encoding='latin1')
	stab_file.write("from\tto\n")
	i = 0
	for j in range(int(sys.argv[2])):
		if(i == j):
			continue
		stab_file.write(str(i) + "\t" + str(j) + "\n")
		stab_file.write(str(j) + "\t" + str(i) + "\n")

if(sys.argv[1] == "star-it"):
	stab_file = open(sys.argv[1] + "-" + sys.argv[2] + ".tsv", "w", encoding='latin1')
	stab_file.write("from\tto\n")
	i = 0
	for j in range(int(sys.argv[2])):
		if(i == j):
			continue
		stab_file.write(str(i) + "\t" + str(j) + "\n")

if(sys.argv[1] == "chain"):
	stab_file = open(sys.argv[1] + "-" + sys.argv[2] + ".tsv", "w", encoding='latin1')
	stab_file.write("from\tto\n")
	for j in range(int(sys.argv[2])-1):
		stab_file.write(str(j) + "\t" + str(j+1) + "\n")
		stab_file.write(str(j+1) + "\t" + str(j) + "\n")

if(sys.argv[1] == "chain-it"):
	stab_file = open(sys.argv[1] + "-" + sys.argv[2] + ".tsv", "w", encoding='latin1')
	stab_file.write("from\tto\n")
	for j in range(int(sys.argv[2])-1):
		stab_file.write(str(j) + "\t" + str(j+1) + "\n")