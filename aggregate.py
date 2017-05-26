#!/usr/bin/python
import sys

def aggregateSortedFile(graphFile):
    """
    We assume that the graphFile has already been sorted alphabetically.
    Thus the duplicated lines are adjacent in the file.
    """
    prev = ""
    count = 0
    fd = open(graphFile)
    for line in fd:
        line = line.strip()
        if line == prev:
            count+=1
        else:
            if count != 0:  # not the first line.
                print prev + "\t" + str(count)
            prev = line
            count = 1

    print prev + "\t" + str(count)

    fd.close()

def removeSelfLink(graphFile):
    fd = open(graphFile)
    for line in fd:
        line = line.strip()
        toks = line.split("\t")
        if toks[0] == toks[1]:
            continue

        print line

    fd.close()

if __name__ == "__main__":
    aggregateSortedFile(sys.argv[1])
#    removeSelfLink(sys.argv[1])

