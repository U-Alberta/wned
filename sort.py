#!/usr/bin/python
import sys
import os
from subprocess import call
from time import time

def line_count(filename):
    count = 0
    fd = open(filename)
    for line in fd:
        count += 1

    fd.close()
    return count

def split(input_file, part_size):
    count = 0
    outfile = "sort.tmp1"

    outfd = open(outfile, 'w')
    fd = open(input_file)
    for line in fd:
        count += 1
        if count % part_size == 0:
            print count
            outfd.close()
            outfile = "sort.tmp" + str(count/part_size + 1)
            outfd = open(outfile, 'w')

        outfd.write(line)

    fd.close()
    outfd.close()

def merge_file_impl(file1, file2):
    filename = "sorted.tmp" + str(time())
    fd1 = open(file1, 'r')
    fd2 = open(file2, 'r')
    outfd = open(filename, 'w')

    line1 = fd1.readline()
    line2 = fd2.readline()
    while len(line1) > 0 and len(line2) > 0:
        if line1 <= line2:
            outfd.write(line1)
            line1 = fd1.readline()
        else:
            outfd.write(line2)
            line2 = fd2.readline()

    if len(line1)==0:
        while len(line2) > 0:
            outfd.write(line2)
            line2 = fd2.readline()
    elif len(line2)==0:
        while len(line1) > 0:
            outfd.write(line1)
            line1 = fd1.readline()

    fd1.close()
    fd2.close()
    outfd.close()

    #remove the file1, file2
    call("rm -rf " + file1 + " " + file2, shell=True)

    return filename

def merge_file(file_list):
    print len(file_list)
    if len(file_list) == 1:
        return file_list[0]

    new_file_list = []

    i = 0
    while (i+1) < len(file_list):
        name = merge_file_impl(file_list[i], file_list[i+1])
        new_file_list.append(name)

        i+=2

    if i < len(file_list):
        new_file_list.append(file_list[i])

    return merge_file(new_file_list)


def sort_file(input_file, num_lines, partitions):
    part_size = num_lines / partitions
    split(input_file, part_size)
    if num_lines % partitions > 0:
        partitions += 1

    for i in range(1, partitions + 1):
        command = "export LC_ALL=C;sort sort.tmp"+str(i) + " > sorted.tmp" + str(i)
        call(command, shell=True)
        call("rm -rf sort.tmp" + str(i), shell=True)

    file_list = ["sorted.tmp" + str(x) for x in range(1, partitions+1)]
    final = merge_file(file_list)

    #rename the file file
    call("mv " + final + " " + input_file + ".sorted", shell=True)

    #clean up
    command = "rm -rf sort.tmp* sorted.tmp*"
    call(command, shell=True)

if __name__ == "__main__":
    """ argv[1] is the file to be sorted
        argv[2] is the number of lines in the file
        argv[3] is the number of partitions we are going to use for the sort"""
    print sort_file(sys.argv[1], int(sys.argv[2]), int(sys.argv[3]))
