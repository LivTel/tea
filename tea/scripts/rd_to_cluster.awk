#!/bin/awk -f
# rd_to_cluster.awk
# $Header: /space/home/eng/cjm/cvs/tea/scripts/rd_to_cluster.awk,v 1.1 2005-05-24 15:12:31 cjm Exp $
# Reads in astrometric fit lines of the form:
# 17:55:52.650 -29:28:01.53 J2000  779.175  978.388 	12.2939 0.0003 	5.98009e+06 2151.81 718.002 1.056 -81.0 7.61 4
# and converts them into cluster data lines of the form:
# 1 n 17 55 52.650 -29 28 01.53 779.175 978.388 12.2939 0.0003 0

 {
	 printf " 1 "
	 printf " "NR" "
	 ra = $1
	 dec = $2
         x_pos = $4
         y_pos = $5
	 mag = $6
         mag_error = $7
	 flag = 0
	 gsub( ":", " ", ra )
	 gsub( ":", " ", dec )
#   "$1 | /bin/sed 's/\(.*\):\(.*\):\(.*\) .*/\1 \2 \3/g'" | getline ra
#   ra = $1
	 printf ra" "dec" "x_pos" "y_pos" "mag" "mag_error" "flag
	 printf "\n"
}
#
# $Log: not supported by cvs2svn $
#
