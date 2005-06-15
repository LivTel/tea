#!/bin/csh
# killhup.csh <Process Name>
# Kills the specified process.
# Doesn't work for grep!
if ( $#argv != 1 ) then
    echo "$0 <Process Name>"
    echo "No process name specified."
    exit 1
endif
set process_name = $1
# See autobooter.properties for details
# 15 is kill -SIGTERM = 143
# 1 is hangup -SIGHUP = 129
set signo = "-HUP"
# Used to use ps -eaf : doesn't work on ftnproxy / RedHat 7.2
# Added more 'w' for tea - it has a long command line
set pid_list = `ps wwwwaux | grep ${process_name} | grep -v grep | grep -v "$0" | awk ' { print $2}'`
if ( "${pid_list}" == "" ) then
    echo "$0 : No processes of name ${process_name} found to kill."
    exit 2
endif
foreach pid ( ${pid_list} )
    echo "Attempting to kill ${process_name} (${pid}) with signal ${signo}."
    kill ${signo} ${pid}
    set kill_status = $status
    if ( ${kill_status} != 0) then
	echo "$0 ${signo} ${pid} failed (${process_name}): return value ${kill_status}"
	exit 3
    endif
end
