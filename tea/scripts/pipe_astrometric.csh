#!/bin/tcsh
# @(#) Fits WCS to LT dp(rt) output 
# @(#)
#
# v 0.1	2003/02/14	Based on LXH's pipe.script v 5.0.
#			All I do is remove some of his clever bits, since we
#			here do not want accurate photometry.
# 
# 2005/05/27		Attempt by RJS to remove GRB specific sections and use script as 
#			generic WCS maker.
#
# 2006/07/26		Most GRB stuff removed. Most noticeboard removed.
#			Lots of error checking added. Should really have a new
#			name by now. It is no longer compatible in any way with the
#			GRB pipeline
#
# 2006/08/05		Will attempt to use 2MASS if the USNOB match fails
#
# 2007/04/18		* Tried to more clearly distinguish variable names for ROTSKYPA from CROTA 
#				which are measured in opposite directions
#			* Always create a first guess nominal WCS in the FITS header before passing
#				the image to imwcs. imwcs works better for reasons I do not yet understand
#			* Changed variable names from ROTCENTX to POINT_CENTX in anticipation of
#				use of APERTURE commands to tweak up the telescope pointing.
#
# If USNOB local copy is to be used, then set properly env variable USNOB_LOCAL_DIR
#

# TABLE OF POSSIBLE RETURNS OF THIS SCRIPT
# ----------------------------------------
#  The intention is to always struggle on if at all possible. The only exit conditions are those
#  that totally prevent us being able to do anything with the image at all.
#  0 : no error occurred
#  5 : Input file missing
#  7 : No pixel scale in FITS header
# ----------------------------------------

# To Do
#
# Handling command line paramters better
# Syntax description



#
# Parse command line options
#
if ( ($# == 0) || ($# > 2) ) goto syntax
if ( $1 == '-v') then
	echo "verbose is set."
	set DEBUG = 1
	shift #moves the $1 along one so we still use $1 as the file
else
	set DEBUG = 0
endif

set inpfile = "$1"
if ( ! -e  $inpfile ) then
  echo No such file : $1
  exit 5
endif


#
# Global variables
#
set ASFIT_STATUS = 0  # 0 if the astrometric fit is OK, 1 when it fails
set NOMINAL_NEEDED = 0 # if this is set, a nominal WCS needs creating

#
# Setup and Config Parameters
#
set MAX_SEx_STARS	= 500	# If Sextractor returns more than this, we think there is a problem and incease detection sigma from the default
set MAX_N_STARS 	= 20    # Max number of stars to select from the CCD image. The brightest N will be used in the wcs fit
set THR_FRACTION 	= 50	# (%) Per centage of catalogue stars which must bet matched in image for good fit
set MIN_N_STARS  	= 3	# Fewest number of stars in a field for it to be worth attempting a fit
set MAX_ROT_CHANGE 	= 5     # (degrees) Largest field rotation tolerated before we suspect WCS fit failure
#set MAX_POINT_CHANGE 	= 60	# (arcsec) Largest apparent pointing error before we suspect WCS fit failure
set MAX_POINT_CHANGE 	= 120	# (arcsec) Largest apparent pointing error before we suspect WCS fit failure

set BACKUPDIR		= WCS_Backup
set TMP_DIR 		= /data/Dprt/WCS
set MYBIN 		= /usr/local/bin/wcs/
setenv PATH ${PATH}:${MYBIN}

if ( ! -d $TMP_DIR ) then
    set TMP_DIR = ${PWD}
endif

# These are effectively #defines
# They will be used to determine which catalogue we are currently cross matching against
# as we loop iteratively through the script trying each one in turn. Only catalogues with
# ID numbers less than NUMB_OF_CATS will be used.
set USE_CDS_USNOB = 1
set USE_2MASS = 2
set USE_USNOB = 3
set NUMB_OF_CATS = 2 # i.e., do not use cat 3 which is USE_USNOB

# Initialising variable
set N_STARS		= 0
set N_STARS_TAKEN	= 0
set NMATCH		= 0



#
# Make a backup copy before I destroy the original data!
#
#if ( ! -d $BACKUPDIR ) mkdir $BACKUPDIR
#if ( ! -e ${BACKUPDIR}/$inpfile ) cp -f $inpfile ${BACKUPDIR}/.

#
# We want a clean file without the WCS towards the end of the script to act as the 
# basis of building the final output file. To provide this we make a copy of the original
# input file. If the input file already has a WCS, we run remove_wcs.csh on it
cp $inpfile ${inpfile}.bak
#set wcs_done = `${MYBIN}gethead -u ${inpfile}.bak WCS_ERR `
#if ( "$wcs_done" != "___" ) then
#  if ( $DEBUG ) echo Stripping the old WCS out of ${inpfile}.bak
#  /usr/local/bin/remove_wcs.csh ${inpfile}.bak
#endif

if ( $DEBUG ) echo "$0 starts processing ${inpfile}"


###################################################
#set TELESCOP = `${MYBIN}gethead -u TELESCOP $inpfile|/bin/awk '{print $1}'`
#switch ( $TELESCOP )
#    case 'FTN' :
#	set TELESCOP = ft1
#	breaksw
#    case 'LT' :
#	set TELESCOP = lt
#	breaksw
#    case 'Liverpool' :
#	set TELESCOP = lt
#	breaksw
#    case 'Faulkes Telescope South' :
#	set TELESCOP = fts
#	breaksw
#    default :
#	echo Telescope not recognized. 
#        set TELESCOP = unknown
#	#exit 5
#endsw

set INSTRUMENT = `${MYBIN}gethead -u INSTRUME $inpfile|/bin/awk '{print $1}'`
switch ( $INSTRUMENT )
    case 'RATCam' :
	set TELESCOP = lt
	breaksw
    case 'HawkCam' :
	set TELESCOP = ft1
	breaksw
    case 'HawkCam2' :
	set TELESCOP = ft2
	breaksw
    case 'DillCamNorth' :
	set TELESCOP = ft1
	breaksw
    case 'DillCamSouth' :
	set TELESCOP = ft2
	breaksw
    default :
	echo Instrument not recognized. 
        set TELESCOP = unknown
	exit 5
endsw


###################################################################
# From Jan 27, 2005 Flipping of FTN images is no longer required! 
###################################################################
#switch ( $TELESCOP )
#    case 'Faulkes' :
##       Faulkes Telescope image: need to flip it
#        echo "Faulkes Telescope image: need to flip it"
#	# If image already flipped, do nothing!
#	setenv FAUROT `${MYBIN}gethead -u FAUROT $inpfile|awk '{print $1}'`
#	switch ( $FAUROT )
#	    case 'YES' :
#		echo "Faulkes: image ALREADY inverted!"
#	    breaksw
#	    case '___' :
#		${MYBIN}imrot -o -l $inpfile
#		${MYBIN}sethead $inpfile FAUROT='YES ' / 'Faulkes image already flipped'
#		echo "Faulkes: inverted image!"
#		breaksw
#	    default :
#		${MYBIN}imrot -o -l $inpfile
#		${MYBIN}sethead $inpfile FAUROT='YES ' / 'Faulkes image already flipped'
#		echo "Faulkes: flipped image!"
#		breaksw
#	endsw
#	breaksw
#    case 'Liverpool' :
#        echo "Liverpool Telescope image"
#	breaksw
#    case '___' :
#	echo "No TELESCOP keyword set: I need to know that, anyway I continue..."
#	breaksw
#    default :
#	echo "TELESCOP keyword set to $TELESCOP"
#	breaksw
#endsw
#######################################################


############# Correct for bad pixel column on LT ############
# This would be nice to do to make the SExtractor run more robust, but 
# i) It has to be done on a temp file, not the archive data
# ii) It is binning dependent
#
#
set do_fixpix = 0
set BINNING = `${MYBIN}gethead -u CCDXBIN $inpfile`
if ( ${INSTRUMENT} == 'RATCam' ) then
  if ( $BINNING == 2 ) then
    set do_fixpix = 1
    set fixpix_param = "362 479 362 1024"
  else if ( $BINNING == 1 ) then
    set do_fixpix = 1
    set fixpix_param = "723 958 723 2048"
  else
    if ( $DEBUG ) echo "We only have fixpix parameters for LT, unbinned or 2x2"
  endif

else if ( ${INSTRUMENT} == 'DillCamSouth' ) then
  switch ( $BINNING )
    case '1' :
      set do_fixpix = 0
      set fixpix_param = ""
      breaksw
    case '2' :
      set do_fixpix = 1
      #Three regions 
      #367 595 368 683 
      #411 592 411 670 
      #772 795 772 1024
      set fixpix_param = "367 595 368 683 411 592 411 670 772 795 772 1024"
      breaksw
    default :
  endsw

else if ( ${INSTRUMENT} == 'HawkCam' ) then
  # Cosmetically perfect chip
  set do_fixpix = 0
  set fixpix_param = ""

else if ( ${INSTRUMENT} == 'HawkCam2' ) then
  # Cosmetically perfect chip
  set do_fixpix = 0
  set fixpix_param = ""

endif



if ( $do_fixpix ) then
  ${MYBIN}fixpix ${inpfile} $fixpix_param 
endif


# The ideal number of connected pixels in source detection varies with binning.
# The config file is set up to assume 2x2 binning, but if the image is unbinned, we 
# want to demand a few more connected pixels for a source solid enough to WCS fit 
# against.
# This parameter is cu
set BINNING = `${MYBIN}gethead -u CCDXBIN $inpfile`
if ( $BINNING == 2 ) then
    set DETECT_MINAREA = 9
else if ( $BINNING == 1 ) then
    set DETECT_MINAREA = 18
else
    if ( $DEBUG ) echo "We only have fixpix parameters for LT, unbinned or 2x2"
endif

############# SExtractor #############
if ( $DEBUG ) echo "Starting SEx Pipe: $inpfile"
if ( $DEBUG ) echo "${MYBIN}sex_LT -c ${MYBIN}astrom_pipe.sex $inpfile"
${MYBIN}sex_LT -c ${MYBIN}astrom_pipe.sex -DETECT_MINAREA $DETECT_MINAREA $inpfile
############# ---------- #############


###Below: managing the sex files image.dat->wcs.cat
###       Preparing the extracted source catalog for matching

# Flags to identify specific error states. These are not used much. 
set SEx_failed = 0
set too_few_stars = 0
set SEx_format_error = 0

if ( ! -s image.cat ) then
  echo 'SEx did not write image.cat : No WCS fit will be possible'
  set SEx_failed = 1
  set NOMINAL_NEEDED = 2	# No point even trying the imwcs
  set N_STARS = 0
else
  /bin/mv -f image.cat             ${TMP_DIR}/${inpfile:r:t}_image.cat
  set N_STARS = `/bin/awk '! /^#/' ${TMP_DIR}/${inpfile:r:t}_image.cat|wc -l | /bin/awk '{print $1}'`
  if ( $DEBUG )  echo "Number of sources extracted by SEx: $N_STARS"

  # This little block of code tried to rerun SExtractor with a variety of different detection thresholds
  # until it found one with a reasonable number of objects
  #set SEx_THRESH = 5
  #while ( ( $N_STARS > $MAX_SEx_STARS ) && ( $SEx_THRESH < 50 ) )
  #  ${MYBIN}sex_LT -c ${MYBIN}astrom_pipe.sex -DETECT_THRESH $SEx_THRESH -CATALOG_NAME ${TMP_DIR}/${inpfile:r:t}_image.cat $inpfile
  #  set N_STARS = `/bin/awk '! /^#/' ${TMP_DIR}/${inpfile:r:t}_image.cat|wc -l | /bin/awk '{print $1}'`
  #  if ( $DEBUG )  echo "Number of sources extracted by SEx at $SEx_THRESH sigma : $N_STARS"
  #  @ SEx_THRESH ++
  #end

  if ( $N_STARS > $MAX_SEx_STARS ) then
    set SKY_RMS = `/usr/local/bin/iterstat 5 $inpfile | awk -F = 'NR==4 {print $2}' | awk '{print $1}'`
    set SEx_THRESH = `echo "$SKY_RMS 5 * p" | dc`
    if ( $DEBUG ) echo ${MYBIN}sex_LT -c ${MYBIN}astrom_pipe.sex -THRESH_TYPE ABSOLUTE -DETECT_THRESH $SEx_THRESH -DETECT_MINAREA $DETECT_MINAREA -CATALOG_NAME ${TMP_DIR}/${inpfile:r:t}_image.cat $inpfile
    ${MYBIN}sex_LT -c ${MYBIN}astrom_pipe.sex -THRESH_TYPE ABSOLUTE -DETECT_THRESH $SEx_THRESH -DETECT_MINAREA $DETECT_MINAREA -CATALOG_NAME ${TMP_DIR}/${inpfile:r:t}_image.cat $inpfile
    set N_STARS = `/bin/awk '! /^#/' ${TMP_DIR}/${inpfile:r:t}_image.cat|wc -l | /bin/awk '{print $1}'`
    if ( $DEBUG )  echo "Number of sources extracted by SEx at $SEx_THRESH=$SEx_THRESH (5 iterstat sigma) : $N_STARS"
  endif

  # CASE: DO NOT TRY ASTROMETRIC FIT for too few sources extracted
  if ( $N_STARS < $MIN_N_STARS ) then
    echo "Too few sources. Astrometric fit not possible."
    set too_few_stars = 1 
    set NOMINAL_NEEDED = 2
endif




#There is only any point parsing these files if SEx ran and seems to have worked OK
if ( $NOMINAL_NEEDED == 0 ) then
  #sorting compilation with MAG_BEST
  setenv MAG_BEST_FIELD `/bin/awk '($3=="MAG_BEST") {print $2}' ${TMP_DIR}/${inpfile:r:t}_image.cat`
  setenv MAG_BEST_FIELD `echo $MAG_BEST_FIELD | /bin/awk '{print $1-1}'`
  if ( ${MAG_BEST_FIELD} < 0 ) then
    echo 'MAG_BEST not set in ${TMP_DIR}/${inpfile:r:t}_image.cat'
    set NOMINAL_NEEDED = 2	# No point even trying imwcs since we will not have an object list
    set SEx_format_error = 1
  else
    setenv MAG_BEST_FIELD `echo $MAG_BEST_FIELD | /bin/awk '{print $1+1}'`
    @ MAG_SORT_FIELD = $MAG_BEST_FIELD - 1
  endif
endif

if ( $NOMINAL_NEEDED == 0 ) then
  #Reformatting to wcs.cat just to extract x,y,mag
  setenv X_IMAGE_FIELD `/bin/awk '($3=="X_IMAGE") {print $2}' ${TMP_DIR}/${inpfile:r:t}_image.cat`
  if ( "$X_IMAGE_FIELD" == "" ) then
    echo 'X_IMAGE not set in ${TMP_DIR}/${inpfile:r:t}_image.cat'
    # No point even trying imwcs since we will not have an object list
    set NOMINAL_NEEDED = 2
    set SEx_format_error = 1
  endif
endif

if ( ${NOMINAL_NEEDED} == 0 ) then
  setenv Y_IMAGE_FIELD `/bin/awk '($3=="Y_IMAGE") {print $2}' ${TMP_DIR}/${inpfile:r:t}_image.cat`
  if ( "${Y_IMAGE_FIELD}" == "" ) then
    echo 'Y_IMAGE not set in ${TMP_DIR}/${inpfile:r:t}_image.cat'
    # No point even trying imwcs since we will not have an object list
    set NOMINAL_NEEDED = 2
    set SEx_format_error = 1
  endif
endif

if ( $NOMINAL_NEEDED == 0 ) then
  setenv FWHM_IMAGE `/bin/awk '($3=="FWHM_IMAGE") {print $2}' ${TMP_DIR}/${inpfile:r:t}_image.cat`
  if ( "$FWHM_IMAGE" == "" ) then
    echo 'FWHM_IMAGE not set in ${TMP_DIR}/${inpfile:r:t}_image.cat'
    set NOMINAL_NEEDED = 2	# No point even trying imwcs since we will not have an object list
    set SEx_format_error = 1
  endif
endif

if ( $NOMINAL_NEEDED == 0 ) then
  setenv SEx_FLAG `/bin/awk '($3=="FLAGS") {print $2}' ${TMP_DIR}/${inpfile:r:t}_image.cat`
  if ( "$SEx_FLAG" == "" ) then
    echo 'FLAGS not set in ${TMP_DIR}/${inpfile:r:t}_image.cat'
    set NOMINAL_NEEDED = 2	# No point even trying imwcs since we will not have an object list
    set SEx_format_error = 1
  endif
endif

if ( ${NOMINAL_NEEDED} == 0 ) then
  if ( $DEBUG )  echo "Used content of ${TMP_DIR}/${inpfile:r:t}_image.cat written by SEx:"
  if ( $DEBUG )  echo "X_IMAGE_FIELD : $X_IMAGE_FIELD"
  if ( $DEBUG )  echo "Y_IMAGE_FIELD : $Y_IMAGE_FIELD"
  if ( $DEBUG )  echo "MAG_BEST_FIELD: $MAG_BEST_FIELD"
  if ( $DEBUG )  echo "FWHM_IMAGE: $FWHM_IMAGE"
else 
  echo "Some sort of error in SExtractor execution of output formatting"
  echo "  SEx_failed = $SEx_failed "
  echo "  too_few_stars = $too_few_stars "
  echo "  SEx_format_error = $SEx_format_error "
endif



###Change FITS keywords from LT standards to those used by wcstools
## We also need the value of PIXSCALE in order to test the SEx catalogue contents are sane
##
# If not set yet, PIXSCALE is set now
set PIXSCALE = `${MYBIN}gethead -u $inpfile PIXSCALE `
if ( "$PIXSCALE" == "___" ) then
    set PIXSCALE = `${MYBIN}gethead -u $inpfile CCDSCALE `
    if ( "$PIXSCALE" == "___" ) then
	echo "Neither PIXSCALE nor CCDSCALE keywords defined. "
	echo "Given this, we cannot even create a nominal WCS. EXIT."
	exit 7
    else
	${MYBIN}sethead $inpfile PIXSCALE=${PIXSCALE} / '[arcsec/pixel] Scale of CCD pixel on sky'
    endif
endif
set PIXSCALE_DEGREES = `echo "10 k $PIXSCALE 3600 / p" | dc`


#
# Check the seeing parameter in the header and use this to exclude non-stellar sources from the SExtractor output
# The various seeing parameters in the FITS header are
# 	L1_SEEING	The pipeline's measurement
#	ESTSEE		The scheduler's best guess of what you should achieve in this exposure
#	SCHEDSEE	The value used by the scheduler to choose this group. (Scaled to r-band zenith)
#	PREDSEE		[Deprecated] Same as SCHEDSEE, before we introduced the zenith/wavelength scaling
#
set SEEING = `${MYBIN}gethead -u $inpfile ESTSEE `
if ( "$SEEING" == "___" ) then
  set SEEING = `${MYBIN}gethead -u $inpfile L1_SEEING `
  if ( "$SEEING" == "___" ) then
    # Arbitrarily set 2 arcsec
    set SEEING = 2 
  else
    # Convert L1_SEEING (pixels) into arcsec
    set SEEING = `echo "3 k 2  $PIXSCALE * p" | wc `
  endif
endif  
set SEEING_PIX = `echo "3 k $SEEING $PIXSCALE / p" | dc`
if ( $DEBUG ) echo "SEEING_PIX = $SEEING_PIX "

#
# Strip out just the $MAX_N_STARS best stars from *.dat
# Then reformat the data  -> *.cat which will get used in the WCS
#
if ( $NOMINAL_NEEDED == 0 ) then
  # Sort by magnitude
  /bin/sort -n +$MAG_SORT_FIELD ${TMP_DIR}/${inpfile:r:t}_image.cat > ${TMP_DIR}/tmp1

  # Keep only those with FWHM < twice the actual seeing, but always keep any sources < 3" whatever
  # These numbers need to be intergers for the following comparator, hence the "1 + 0 k 1 /" in the dc script
  # setenv is used instead of set because it allows us to use ENVIRON in the following awk
  setenv FWHM_IMAGE_LIMIT `echo "2.5 $SEEING_PIX * 1 + 0 k 1 / p" | dc`
  set THREE_ARCSEC_PIX = `echo "0 k 3 $PIXSCALE / 1 + p" | dc`
  if ( $FWHM_IMAGE_LIMIT < $THREE_ARCSEC_PIX ) setenv FWHM_IMAGE_LIMIT $THREE_ARCSEC_PIX
  if ( $DEBUG ) echo FWHM_IMAGE_LIMIT = $FWHM_IMAGE_LIMIT
  #/bin/awk '( ( ! /^#/ ) && ( $ENVIRON["FWHM_IMAGE"] > 1 ) && ( $ENVIRON["FWHM_IMAGE"] < ENVIRON["FWHM_IMAGE_LIMIT"] ) )' ${TMP_DIR}/tmp1 > ${TMP_DIR}/tmp2
  /bin/awk '( ( $ENVIRON["SEx_FLAG"] == 0 )  && ( ! /^#/ ) && ( $ENVIRON["FWHM_IMAGE"] > 1 ) && ( $ENVIRON["FWHM_IMAGE"] < ENVIRON["FWHM_IMAGE_LIMIT"] ) )' ${TMP_DIR}/tmp1 > ${TMP_DIR}/tmp2
  /bin/awk '( $ENVIRON["MAG_BEST_FIELD"] < 99 ) ' ${TMP_DIR}/tmp2 > ${TMP_DIR}/tmp3

  # Keep only $MAX_N_STARS best
  head -$MAX_N_STARS ${TMP_DIR}/tmp3 > ${TMP_DIR}/tmp4
  # Reformat the file to what is required by imwcs
  /bin/awk '{printf "%s %s %s\n", $ENVIRON["X_IMAGE_FIELD"],$ENVIRON["Y_IMAGE_FIELD"],$ENVIRON["MAG_BEST_FIELD"]}' ${TMP_DIR}/tmp4 > ${TMP_DIR}/${inpfile:r:t}_wcs.cat
  rm ${TMP_DIR}/tmp1 ${TMP_DIR}/tmp2 ${TMP_DIR}/tmp3 ${TMP_DIR}/tmp4

  set N_STARS_TAKEN = `wc -l ${TMP_DIR}/${inpfile:r:t}_wcs.cat | /bin/awk '{print $1}'` 
  if ( $DEBUG ) echo N_STARS_TAKEN = $N_STARS_TAKEN
endif

#
# Read the rotator centre from the header. 
# If the keywords are missing, we can guess at the chip centre.
#
set POINT_CENTX = `${MYBIN}gethead -u $inpfile ROTCENTX`
if ( "$POINT_CENTX" == "___" ) @ POINT_CENTX = `${MYBIN}gethead $inpfile NAXIS1` / 2

set POINT_CENTY = `${MYBIN}gethead -u $inpfile ROTCENTY`
if ( "$POINT_CENTY" == "___" ) @ POINT_CENTY = `${MYBIN}gethead $inpfile NAXIS2`  / 2



###Below: Pointing catalog compilation (only if not already there)
###       Need to retrieve the USNOB file      Author: CRG
##
# There is one really ugly bit in here. dc always outputs values <1 without the leading 0.
# It represents the number 0.34 as .34
# This means we have to manually stick a 0 on the front for the benefit of the CDS query client which 
# will not accept numbers in this format.
set RA = `${MYBIN}gethead $inpfile RA`
set DE = `${MYBIN}gethead $inpfile DEC`
set INPUT_RA = 0`echo $RA | /bin/awk -F : '{print "3 k ",$1,$2,$3," 240 / r 4 / + r 15 * + p"}' | dc `
set tmp_var = `echo $DE | sed 's/[+-]//' | /bin/awk -F : '{print "3 k ",$1,$2,$3," 3600 / r 60 / + r + p"}' | dc `
# Trap the case of -ve dec (including -0)
if ( `echo $DE | grep -c -` ) then
    set tmp_var2 = `echo $tmp_var | awk '{print "3 k ",$1," _1 * p"}' | dc `
    set INPUT_DEC = `echo $tmp_var2 | sed 's/-/-0/' `
else 
    set INPUT_DEC = 0$tmp_var 
endif
set RASTR = `echo $RA|sed 's/[:=&]//g'`
set DESTR = `echo $DE|sed 's/[:=&]//g'`
 











#
# Here we use a goto. The script will run through first time using USNOB as the 
# reference catalogue. If at the end it does not like the fit, we will return to
# this label and go through the whole thing again using 2MASS. In principle in 
# future we could add more catalogues and loop through again.  
#
set REFCAT = 1
LOOP_START:

if ( $DEBUG ) echo "Starting on REFCAT $REFCAT"

set THIS_CAT_FAILED = 0
set ASFIT_STATUS = 0

# When something goes wrong, we need to respond differently if this is our last catalogue to
# try. If we still have more cats to have a go at, we won't want to generate any critical errors.
if ( $REFCAT == $NUMB_OF_CATS ) then
  set last_chance = 1
else
  set last_chance = 0
endif

if ( $REFCAT == $USE_2MASS ) then

  if ( `echo $INPUT_DEC | grep -c -` ) then
    set CAT_SEARCH_CENTRE = ${INPUT_RA}${INPUT_DEC}
  else
    set CAT_SEARCH_CENTRE = ${INPUT_RA}+${INPUT_DEC}
  endif
  if ( $DEBUG ) echo "2MASS search centre is $CAT_SEARCH_CENTRE"
  set CAT_SEARCH_RADIUS = 4

  # If the disk file contains no matches, we try again. We'll probably get 0 again, but worth a try
  if ( ! -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass ) then
    set CDS_GOOD = `grep matches ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass | /bin/awk '{print $2}'`
    if ( $CDS_GOOD == 0 ) rm ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass
  endif
  # Do catalogue search if the file is not already available on disk
  set DO_GERNERATE_STARB = 0
  if ( ! -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass ) then 
    echo "/usr/local/bin/wcs/find2mass -r $CAT_SEARCH_RADIUS -c $CAT_SEARCH_CENTRE -m 5000 > ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass "
    /usr/local/bin/wcs/find2mass -r $CAT_SEARCH_RADIUS -c $CAT_SEARCH_CENTRE -m 5000 > ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass
    if ( $DEBUG ) echo CDS returned `wc -l ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass | /bin/awk '{print $1}'` objects 
    set DO_GERNERATE_STARB = 1
  else
    if ( $DEBUG ) echo "2MASS catalogue ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass already exists"
    touch ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass
  endif

  # Construct the starb file if it is not already available on disk
  # If we just downloaded a new CDS catalogue, we force creation of a new STARB too
  if ( ( ! -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb) || ( $DO_GENERATE_STARB) ) then
    if ( -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass ) then
      echo "Table" > ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      echo "image $inpfile" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      echo "catalog 2MASS" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      echo "radecsys FK5" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      echo "equinox 2000.0000" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      echo "epoch 2000.0000" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      echo "ra      	dec       	magJ" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      echo "----------	-----------	----" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      # 2MASS catalogue format:
      #   1,2 = RA,dec
      #   7 = J mag
      #   54 = B mag
      #   55 = R mag
      /bin/awk '( (! /aclient/) && (! /#/) ) {print $1,$2,$7,$54,$55}' ${TMP_DIR}/${CAT_SEARCH_CENTRE}.2mass > ${TMP_DIR}/tmp3
      /bin/awk -f ${MYBIN}/convert_cds_starb_1.awk ${TMP_DIR}/tmp3 >  ${TMP_DIR}/tmp4
      # Force the existence of both + and - symbols on the dec
      /bin/awk '/-/ {print $1"\tM"$2"\t"$3}; ! /-/ {print $1"\tP"$2"\t"$3}' ${TMP_DIR}/tmp4 > ${TMP_DIR}/tmp5
      /bin/sed 's/-//g ; s/+//g'  ${TMP_DIR}/tmp5 >  ${TMP_DIR}/tmp6
      /bin/sed 's/M/-/g ; s/P/+/g'  ${TMP_DIR}/tmp6 >>   ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
      rm -f ${TMP_DIR}/tmp3 ${TMP_DIR}/tmp4 ${TMP_DIR}/tmp5 ${TMP_DIR}/tmp6
    else
      echo "2MASS catalogue failed. WCS fit will not be possible."
      set THIS_CAT_FAILED = 1
    endif

  else
    if ( $DEBUG ) echo "2MASS starb file ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb already exists"
    touch ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
  endif

endif

if ( $REFCAT == $USE_CDS_USNOB ) then

  if ( `echo $INPUT_DEC | grep -c -` ) then
    set CAT_SEARCH_CENTRE = ${INPUT_RA}${INPUT_DEC}
  else
    set CAT_SEARCH_CENTRE = ${INPUT_RA}+${INPUT_DEC}
  endif
  if ( $DEBUG ) echo "CDS USNOB search centre is $CAT_SEARCH_CENTRE"
  set CAT_SEARCH_RADIUS = 4

  # If the disk file contains no matches, we try again. We'll probably get 0 again, but worth a try
  if ( ! -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob ) then
    set CDS_GOOD = `grep matches ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob | /bin/awk '{print $2}'`
    if ( $CDS_GOOD == 0 ) rm ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob
  endif
  # Do catalogue search if the file is not already available on disk and file is not empty
  set DO_GENERATE_STARB = 0
  if ( ! -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob ) then
    if ( $DEBUG) echo "findusnob1 -r $CAT_SEARCH_RADIUS -c $CAT_SEARCH_CENTRE -m 5000 -smr2 > ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob "
    ${MYBIN}findusnob1 -r $CAT_SEARCH_RADIUS -c $CAT_SEARCH_CENTRE -m 5000 -smr2 > ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob
    if ( $DEBUG ) echo CDS returned `wc -l ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob | /bin/awk '{print $1}'` objects 
    set DO_GENERATE_STARB = 1
  else
    if ( $DEBUG ) echo "CDS USNOB catalogue ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob already exists"
    touch ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob
  endif

  # Construct the starb file if it is not already available on disk
  # If we just downloaded a new CDS catalogue, we force creation of a new STARB too
  if ( ( ! -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb ) || ( $DO_GENERATE_STARB ) ) then
    if ( -s ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob ) then
      echo "Table" > ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      echo "image $inpfile" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      echo "catalog USNOB" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      echo "radecsys FK5" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      echo "equinox 2000.0000" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      echo "epoch 2000.0000" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      echo "ra      	dec       	magR" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      echo "----------	-----------	----" >> ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      # USNOB catalogue format:
      #   2 = RA,dec
      #   15,25 = B mag
      #   20,30 = R mag
      /bin/awk '( (! /aclient/) && (! /#/) ) {print $2,$15,$25,$20,$30}' ${TMP_DIR}/${CAT_SEARCH_CENTRE}.usnob > ${TMP_DIR}/tmp1
      /bin/awk -f ${MYBIN}/cds_extract_mags.awk ${TMP_DIR}/tmp1 >  ${TMP_DIR}/tmp2
      /bin/sed 's/+/ +/ ; s/-/ -/' ${TMP_DIR}/tmp2 > ${TMP_DIR}/tmp3
      /bin/awk -f ${MYBIN}/convert_cds_starb_1.awk ${TMP_DIR}/tmp3 >  ${TMP_DIR}/tmp4
      # Force the existence of both + and - symbols on the dec
      /bin/awk '/-/ {print $1"\tM"$2"\t"$3}; ! /-/ {print $1"\tP"$2"\t"$3}' ${TMP_DIR}/tmp4 > ${TMP_DIR}/tmp5
      /bin/sed 's/-//g ; s/+//g'  ${TMP_DIR}/tmp5 >  ${TMP_DIR}/tmp6
      /bin/sed 's/M/-/g ; s/P/+/g'  ${TMP_DIR}/tmp6 >>   ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
      rm -f ${TMP_DIR}/tmp3 ${TMP_DIR}/tmp4 ${TMP_DIR}/tmp5 ${TMP_DIR}/tmp6
    else
      echo "CDS USNOB catalogue failed. WCS fit will not be possible."
      set THIS_CAT_FAILED = 1
    endif

  else
    if ( $DEBUG ) echo "CDS USNOB starb file ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb already exists"
    touch ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
  endif

endif

if ( $REFCAT == $USE_USNOB ) then
  # XXX Once you get the usnob file, check it is valid and retry if need be 
  if ( ! -s ${TMP_DIR}/${RASTR}${DESTR}.usnob ) then
    setenv SIZE_X `${MYBIN}gethead $inpfile NAXIS1`  # equivalent to CCDXIMSI
    setenv SIZE_Y `${MYBIN}gethead $inpfile NAXIS2`  # equivalent to CCDYIMSI
    setenv PIXSC  `${MYBIN}gethead $inpfile CCDSCALE`

    setenv TRUE_SIZE_X  10.0    # arcmin
    setenv TRUE_SIZE_Y  10.0    # arcmin

    if ( $DEBUG )  echo "Retrieving USNOB file according to the following:"
    if ( $DEBUG )  echo "Center RA: $RA (${INPUT_RA})"
    if ( $DEBUG )  echo "Center DEC: $DE (${INPUT_DEC})"
    if ( $DEBUG )  echo "X Size (minutes): $TRUE_SIZE_X"
    if ( $DEBUG )  echo "Y Size (minutes): $TRUE_SIZE_Y"
    if ( $DEBUG )  echo "........"

    # First check if a local copy of USNOB is available.
    # If yes, access it, otherwise go through internet.
    # CRG, 01/2005
    setenv USNOB_STATUS 1
    if ( ${?USNOB_LOCAL_DIR} ) then
	if ( $DEBUG )  echo "Get USNOB from local copy"
	if ( $DEBUG )  echo "${MYBIN}/get_local_usnob.csh $RA  $DE  $TRUE_SIZE_X  ${TMP_DIR}/${RASTR}${DESTR}.usnob"
	${MYBIN}/get_local_usnob.csh $RA  $DE  $TRUE_SIZE_X  ${TMP_DIR}/${RASTR}${DESTR}.usnob
	setenv USNOB_STATUS $status
	if ( ! ${USNOB_STATUS} ) then
	    if ( ! -s ${TMP_DIR}/${RASTR}${DESTR}.usnob ) then
		setenv USNOB_STATUS 1
	    else
		setenv USNOB_GOOD_LINES `/bin/awk '! /^#/' ${TMP_DIR}/${RASTR}${DESTR}.usnob|wc -l|/bin/awk '{print $1}'`
		if ( ! ${USNOB_GOOD_LINES} ) then
		    setenv USNOB_STATUS 1
		endif
	    endif
	endif
	if ( ${USNOB_STATUS} ) echo "USNOB local copy: error"
    endif

    if ( ${USNOB_STATUS} ) then
	if ( $DEBUG )  echo "USNOB local copy not available. Retrieve it from the net..."
	if ( $DEBUG )  echo "${MYBIN}/get_usnob_hhmmss.sh $RA $DE $TRUE_SIZE_X $TRUE_SIZE_Y"
	${MYBIN}/get_usnob_hhmmss.sh $RA $DE $TRUE_SIZE_X $TRUE_SIZE_Y
        if ( -s ${RASTR}${DESTR}.usnob ) mv -f ${RASTR}${DESTR}.usnob ${TMP_DIR}/${RASTR}${DESTR}.usnob
    endif

  else
    if ( $DEBUG )  echo "USNO File already available"
    touch ${TMP_DIR}/${RASTR}${DESTR}.usnob
  endif

  #
  # If usnob file doesn't exist, give up. Theoretically you could carry on to see if there are old copies
  # of the starb files available, but past experience has shown this just causes trouble. It is not robust.
  # If we do not have a usnob file (either old or newly downloaded) delete any existing crop_ and starb files
  # to clean them up and go on to a nominal wcs. Hopefully next time we try, we will get the USNOB file OK.
  #
  if ( ! -s ${TMP_DIR}/${RASTR}${DESTR}.usnob ) then
    echo "Unable to find a valid USNOB file. Use nominal WCS instead."
    set THIS_CAT_FAILED = 1
    if ( -e ${TMP_DIR}/${RASTR}${DESTR}_usnob.starb ) rm ${TMP_DIR}/${RASTR}${DESTR}_usnob.starb
    if ( -e ${TMP_DIR}/crop_${RASTR}${DESTR}.usnob ) rm ${TMP_DIR}/crop_${RASTR}${DESTR}.usnob
    if ( -e ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb ) rm ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb
  endif

  #
  # Create the starb version of the usnob file. The starb is what actually gets read by imwcs
  #
  if ( ( $THIS_CAT_FAILED == 0 ) && ( ! -s ${TMP_DIR}/${RASTR}${DESTR}_usnob.starb ) ) then
    if ( $DEBUG )  echo "${MYBIN}/tostartab_hhmmss.sh ${TMP_DIR}/${RASTR}${DESTR}.usnob >! ${TMP_DIR}/${RASTR}${DESTR}_usnob.starb"
    ${MYBIN}/tostartab_hhmmss.sh ${TMP_DIR}/${RASTR}${DESTR}.usnob >! ${TMP_DIR}/${RASTR}${DESTR}_usnob.starb
  else
    if ( $DEBUG )  echo "Either USNO.starb file already available or web get of USNOB failed"
    touch ${TMP_DIR}/${RASTR}${DESTR}_usnob.starb
  endif


  ####### Start Cropping #######
  #
  if ( ( ! $THIS_CAT_FAILED ) && ( ! -s ${TMP_DIR}/crop_${RASTR}${DESTR}.usnob ) ) then

    if ( $DEBUG )  echo "No cropped catalogue: start cropping USNOB file..."

    setenv CROP_SIZE_X  6.0    # arcmin
    setenv CROP_SIZE_Y  6.0    # arcmin

    ${MYBIN}crop_usnob ${inpfile} ${TMP_DIR}/${RASTR}${DESTR}.usnob ${CROP_SIZE_X} ${TMP_DIR}/crop_${RASTR}${DESTR}.usnob ${TMP_DIR}/usnob_nominal.dat >& /dev/null
    setenv STATUS $status
    if ( ${STATUS} ) then
	echo "Cropping USNOB catalogue failed: use the entire catalogue"
	cp -f ${TMP_DIR}/${RASTR}${DESTR}.usnob ${CROP_SIZE_X} ${TMP_DIR}/crop_${RASTR}${DESTR}.usnob
    else
	if ( $DEBUG )  echo "USNOB catalogue cropped."
    endif
  else
    if ( $DEBUG )  echo "Either cropped USNOB catalogue already available or web get of USNO failed."
    touch ${TMP_DIR}/crop_${RASTR}${DESTR}.usnob
  endif



  if ( ( ! $THIS_CAT_FAILED ) && ( ! -s ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb ) ) then
    if ( $DEBUG ) echo "No cropped starb file. Create ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb "
    ${MYBIN}/tostartab_hhmmss.sh ${TMP_DIR}/crop_${RASTR}${DESTR}.usnob >! ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb
  else
    if ( $DEBUG )  echo "Either cropped USNOB.starb catalogue already available or web get of USNO failed."
    touch ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb
  endif




  ####### End Cropping #######

  #
  # If  crop_*.starb file doesn't exist, that's the end of the imwcs. We'll just need to do nominal
  #
  if ( ! -s ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb ) then
    echo "Unable to find a valid USNOB file. USNOB fit will not be possible."
    set THIS_CAT_FAILED = 1
  endif

endif

# Gets done whichever catalogue is current
if ( ( ! $NOMINAL_NEEDED ) && ( ! $THIS_CAT_FAILED ) ) then

  if ( $REFCAT == $USE_USNOB ) then
    set STARBFILE = ${TMP_DIR}/crop_${RASTR}${DESTR}_usnob.starb
  else if ( $REFCAT == $USE_2MASS ) then
    set STARBFILE = ${TMP_DIR}/${CAT_SEARCH_CENTRE}_2mass.starb
  else if ( $REFCAT == $USE_CDS_USNOB ) then
    set STARBFILE = ${TMP_DIR}/${CAT_SEARCH_CENTRE}_usnob.starb
  else
    echo "Invalid value of REFCAT = $REFCAT "
    set THIS_CAT_FAILED = 1
  endif
    

  set N_STARS_CAT = `/bin/awk '/^[0-9]/' $STARBFILE | wc -l | /bin/awk '{print $1}'`

  if ( $DEBUG )  echo "Input RA               : $RA (${INPUT_RA})"
  if ( $DEBUG )  echo "Input DEC              : $DE (${INPUT_DEC})"
  if ( $DEBUG )  echo "Extracted sources        N_STARS      : $N_STARS"
  if ( $DEBUG )  echo "Used for Astrometric fit N_STARS_TAKEN: $N_STARS_TAKEN"
  if ( $DEBUG )  echo "Catalogue sources number N_STARS_CAT  : $N_STARS_CAT"

  if ($N_STARS_CAT < $MIN_N_STARS ) then
    echo "There are fewer than $MIN_N_STARS in catalogue. We cannot cross correlate against this."
    echo "  Create a nominal WCS instead"
    set THIS_CAT_FAILED = 1
  endif

endif

# 
# Extract first guess at field rotation from FITS header
#
set ROTSKYPA = `${MYBIN}gethead $inpfile ROTSKYPA`
if ( $DEBUG )  echo "ROTSKYPA: $ROTSKYPA"
# ROTSKYPA only gets used again to write into the the final output file as WROTSKY
# Throughout the main body of the script, we only need CROTA and INPUT_ROT for the actual fitting
# Change sign to ROTSKYPA: CROTA is measured in the opposite sense to ROTSKYPA 
set CROTA = `echo "$ROTSKYPA" | /bin/awk '{print -$1}'`
set INPUT_ROT = $CROTA



# Before calling imwcs, write a first guess nominal WCS into teh FITS header based on the TCS parameters.
# imwcs works a lot better if it has a first guess to start from
#
# There is a nasty fudge here. Ugly and not reliable. It could easily break at any time due to small changes in the
# arrangement of FITS keyword headers. imwcs and sethead can get in a mess with long comments and end
# up corrupting the keyword. In order to prevent this, I manually truncate the comments on those keywords
# which routinely cause troubles. Others may do on some occaisions and I have not noticed. The real 
# solution is to fix sethead, but Mink's code is fairly inpenetable.
#${MYBIN}fits_modify_comment_static ${inpfile} RADECSYS '[FK4, FK5] Fundamental coordinate system'
#${MYBIN}fits_modify_comment_static ${inpfile} EQUINOX 'Date of the coordinate system'
${MYBIN}sethead $inpfile   CTYPE1="RA---TAN"		/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CTYPE2="DEC--TAN"		/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CRPIX1=${POINT_CENTX}	/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CRPIX2=${POINT_CENTY}	/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CRVAL1=${INPUT_RA}		/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CRVAL2=${INPUT_DEC}		/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CDELT2=$PIXSCALE_DEGREES	/ 'First guess nominal WCS'
set PIXSCALE_DEGREES = `echo "10 k $PIXSCALE _1 * p" | dc`
${MYBIN}sethead $inpfile   CDELT1=$PIXSCALE_DEGREES	/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CROTA1=$INPUT_ROT		/ 'First guess nominal WCS'
${MYBIN}sethead $inpfile   CROTA2=$INPUT_ROT		/ 'First guess nominal WCS'







########### ASTROMETRIC FIT: begin ###########
if ( ${NOMINAL_NEEDED} != 0 ) then
    echo "Some critical error has occured. Not running the WCS fit. Only a nominal WCS will be available."
    set NMATCH = 0
    set WCS_WAS_RUN = 0
else if ( ${THIS_CAT_FAILED} ) then
    echo "Some error occured on catalogue $REFCAT. Not running the WCS fit."
    set NMATCH = 0
    set WCS_WAS_RUN = 0
else

# 
# Decide how many parameters to fit. If we only have three stars, there is not much point pushing it
#
if ( $N_STARS_TAKEN <= 5 ) then
  # RA, DEC and rotation
  set N_FIT_PARAMS = -125
else
  # RA, DEC, pixscale and rotation
  set N_FIT_PARAMS = -1235
endif


#  set iter_count = 0
#  while ( $iter_count < 4 ) 
#    @ iter_count ++
    set WCS_WAS_RUN = 1
    if ( $DEBUG )  echo "Performing Astrometric Fit..."

    #   Keep stdout and stderr of imwcs_LT separate: (mod. CRG, 11/11/04)
    if ( $DEBUG )  echo "(${MYBIN}imwcs-3.6.4 -n $N_FIT_PARAMS -c $STARBFILE -h 75 -s 2 -p ${PIXSCALE} -vwd ${TMP_DIR}/${inpfile:r:t}_wcs.cat -t 20 -q i  -x $POINT_CENTX $POINT_CENTY -o ${TMP_DIR}/${inpfile:r:t}_temp.fits $inpfile >! ${TMP_DIR}/${inpfile:r:t}.wcsfit) >& ${TMP_DIR}/${inpfile:r:t}.errwcsfit"
    #if ( $DEBUG )  echo "(${MYBIN}imwcs-3.6.4 -n $N_FIT_PARAMS -c $STARBFILE -h 75 -s 2 -p ${PIXSCALE} -a ${CROTA} -vwd ${TMP_DIR}/${inpfile:r:t}_wcs.cat -t 20 -q i  -x $POINT_CENTX $POINT_CENTY -o ${TMP_DIR}/${inpfile:r:t}_temp.fits $inpfile >! ${TMP_DIR}/${inpfile:r:t}.wcsfit) >& ${TMP_DIR}/${inpfile:r:t}.errwcsfit"
  
    (${MYBIN}imwcs-3.6.4 -n $N_FIT_PARAMS -c $STARBFILE -h 75 -s 2 -p ${PIXSCALE} -vwd ${TMP_DIR}/${inpfile:r:t}_wcs.cat -t 20 -q i  -x $POINT_CENTX $POINT_CENTY -o ${TMP_DIR}/${inpfile:r:t}_temp.fits $inpfile >! ${TMP_DIR}/${inpfile:r:t}.wcsfit) >& ${TMP_DIR}/${inpfile:r:t}.errwcsfit
    #(${MYBIN}imwcs-3.6.4 -n $N_FIT_PARAMS -c $STARBFILE -h 75 -s 2 -p ${PIXSCALE} -a ${CROTA} -vwd ${TMP_DIR}/${inpfile:r:t}_wcs.cat -t 20 -q i  -x $POINT_CENTX $POINT_CENTY -o ${TMP_DIR}/${inpfile:r:t}_temp.fits $inpfile >! ${TMP_DIR}/${inpfile:r:t}.wcsfit) >& ${TMP_DIR}/${inpfile:r:t}.errwcsfit



#    set NMATCH = `/bin/awk '/nmatch=/ {nmatch=$3}; END {print nmatch}' ${TMP_DIR}/${inpfile:r:t}.wcsfit`
#    set NSTARS = `/bin/awk '/nmatch=/ {nstars=$5}; END {print nstars}' ${TMP_DIR}/${inpfile:r:t}.wcsfit`
#    set MATCHFRAC = `echo "$NMATCH * 100 / $NSTARS" | bc`
#    if ( $DEBUG )  echo "Fitted Image: Matched fraction = $MATCHFRAC"
#    #Conditions to be satisfied for rejecting the fit
#    echo $NSTARS $NMATCH $MATCHFRAC $ROTSKYPA
#    if(($MATCHFRAC < ${THR_FRACTION}) || ($NMATCH < $MIN_N_STARS)) then
#      set ROTSKYPA = `echo $ROTSKYPA | sed 's/-/_/'`
#      set ROTSKYPA = `echo "$ROTSKYPA 90 + p" | dc `
#    else
#      set iter_count = 5 # Fit seems OK, so stop the looping
#    endif
#  end
endif

########### ASTROMETRIC FIT: end   ###########

# Define output files name
set fittedfile = ${inpfile}.wcs

########### Sanity checks on the results of the Astrometric Fit ###########


# Check the existence of the output file ${TMP_DIR}/${inpfile:r:t}_temp.fits
if ( ! -s ${TMP_DIR}/${inpfile:r:t}_temp.fits ) then
    if ( ! ${NOMINAL_NEEDED} ) then
	echo 'Astrometric fit failed: no output file written'
	set NMATCH = 0
	set ASFIT_STATUS = 1
        set THIS_CAT_FAILED = 1
        set WCS_WAS_RUN = 0
    endif
else
    ###Below: Check full compliance with WCS and write other WCS keywords
    ##
    setenv CTYPE1 `${MYBIN}gethead ${TMP_DIR}/${inpfile:r:t}_temp.fits CTYPE1`
    if($CTYPE1 != 'RA---TAN') then
	echo "Fitted Image: non-gnomonic Projection: CTYPE1= $CTYPE1"
	set ASFIT_STATUS = 1
	set THIS_CAT_FAILED = 1
    endif

    if ( ${ASFIT_STATUS} == 0 ) then
	setenv CRVAL2 `${MYBIN}gethead ${TMP_DIR}/${inpfile:r:t}_temp.fits CRVAL2`
	setenv I_CRVAL2 `echo "$CRVAL2 / 1"|bc`    # INTEGER PART ONLY (otherwise, use 'bc -l')
    
	if( $I_CRVAL2 < 90) then
	    setenv LONPOLE '180.0'
	else if($I_CRVAL2 == 90) then
	    setenv LONPOLE '0.0'
	else
	    echo "Fitted Image: unacceptable value for CRVAL2: $CRVAL2"
	    set ASFIT_STATUS = 1
	    set THIS_CAT_FAILED = 1
	endif
    endif

    if ( ${ASFIT_STATUS} == 0 ) then
	#set PV2_1 = '0.0'  # Native longitude of the fiducial point
	#set PV2_2 = '90.0' # Native latitude  of the fiducial point
	#set PV2_3 = $LONPOLE # Native longitude of the celestial pole
	#set PV2_4 = $CRVAL2  # Native latitude of the celestial pole

	# Add some WCS keywords
	test -f listkeys.tmp && rm -f listkeys.tmp
	echo "CUNIT1='deg'" > listkeys.tmp
	echo "CUNIT2='deg'" >> listkeys.tmp
	#echo "PV2_1=$PV2_1 / Native longitude of fiducial point" >> listkeys.tmp
	#echo "PV2_2=$PV2_2 / Native latitude of fiducial point" >> listkeys.tmp
	#echo "PV2_3=$PV2_3 / Native longitude of celestial pole" >> listkeys.tmp
	#echo "PV2_4=$PV2_4 / Native latitude of celestial pole" >> listkeys.tmp
	echo "LONPOLE=$LONPOLE / Native longitude of celestial pole" >> listkeys.tmp
	setenv MJDOBS `${MYBIN}gethead -u ${TMP_DIR}/${inpfile:r:t}_temp.fits MJD`
	if ( "$MJDOBS" != "___" ) then
	    echo "MJD-OBS=$MJDOBS / Start time in Modified Julian Days" >> listkeys.tmp
	else
	    echo "MJD undefined"
	endif
	${MYBIN}sethead ${TMP_DIR}/${inpfile:r:t}_temp.fits @listkeys.tmp
	if ( $DEBUG )  cat listkeys.tmp
        rm -f listkeys.tmp


	###Below:  Further Sanity checks on the WCS
	##
	set NMATCH = `/bin/awk '/nmatch=/ {nmatch=$3}; END {print nmatch}' ${TMP_DIR}/${inpfile:r:t}.wcsfit`
	if ( "$NMATCH" == "" ) set NMATCH = 0

	set MATCHFRAC = `echo "$NMATCH * 100 / $N_STARS_TAKEN" | bc`
	if ( $DEBUG )  echo "Fitted Image: Matched fraction = $MATCHFRAC ( $NMATCH of $N_STARS_TAKEN ) "
	#Conditions to be satisfied for rejecting the fit
	if(($MATCHFRAC < ${THR_FRACTION}) || ($NMATCH < $MIN_N_STARS)) then
	    echo "Fitted Image: less than ${THR_FRACTION}% of reference stars matched or less than ${MIN_N_STARS}"
	    set ASFIT_STATUS = 1
            set THIS_CAT_FAILED = 1
	endif
    endif

    # Make sure PIXSCALE is defined in the fitted image
    # This has already been checked once before
    if ( ${ASFIT_STATUS} == 0 ) then
	setenv PIXSCALE `${MYBIN}gethead -u PIXSCALE ${TMP_DIR}/${inpfile:r:t}_temp.fits`
	if ( "$PIXSCALE" == "___" ) then 
	    echo "Fitted Image: pixscale not known"
	    set ASFIT_STATUS = 1
	endif 
    endif

    # Make sure scale change is not greater than 1.0 %
    # Previous version was correct only for rotation angles= 0,90,180,270!
    # Now the check is robust!
    if ( ${ASFIT_STATUS} == 0 ) then
	set CD1_1 = `${MYBIN}gethead ${TMP_DIR}/${inpfile:r:t}_temp.fits CD1_1`
	set CD1_1 = `echo "$CD1_1 * 3600" | bc -l`
	set CD1_2 = `${MYBIN}gethead ${TMP_DIR}/${inpfile:r:t}_temp.fits CD1_2`
	set CD1_2 = `echo "$CD1_2 * 3600" | bc -l`
	set CD2_1 = `${MYBIN}gethead ${TMP_DIR}/${inpfile:r:t}_temp.fits CD2_1`
	set CD2_1 = `echo "$CD2_1 * 3600" | bc -l`
	set CD2_2 = `${MYBIN}gethead ${TMP_DIR}/${inpfile:r:t}_temp.fits CD2_2`
	set CD2_2 = `echo "$CD2_2 * 3600" | bc -l`
	if ( $DEBUG )  echo "PIXSCALE:  $PIXSCALE"
	if ( $DEBUG )  echo "CD1_1   CD1_2:  $CD1_1  $CD1_2"
	if ( $DEBUG )  echo "CD2_1   CD2_2:  $CD2_1  $CD2_2"
	set SCALECHANGE_1 = `echo "(sqrt( $CD1_1 ^ 2 + $CD2_1 ^ 2 ) - $PIXSCALE ) / $PIXSCALE * 1000"| bc -l|sed 's/\..*//g'`
	set SCALECHANGE_2 = `echo "(sqrt( $CD1_2 ^ 2 + $CD2_2 ^ 2 ) - $PIXSCALE ) / $PIXSCALE * 1000"| bc -l|sed 's/\..*//g'`
	if ( $DEBUG )  echo "Fitted Image: Scalechange 1: $SCALECHANGE_1 (0.1%)  Scalechange 2: $SCALECHANGE_2 (0.1%)"
	if(($SCALECHANGE_1 >= 10) || ($SCALECHANGE_2 >= 10)) then 
	    echo 'Fitted Image: CD matrix suggests plate scale changed by more than 1.0%. Bad.'
	    set ASFIT_STATUS = 1
            set THIS_CAT_FAILED = 1
	endif
    endif
 
    # Rename ${TMP_DIR}/${inpfile:r:t}_temp.fits to fittedfile variable
    mv ${TMP_DIR}/${inpfile:r:t}_temp.fits ${fittedfile}

endif


#I think this programme creates sky coordinates from XY using a nominal WCS. It may be useful
#${MYBIN}nominal_LT_xy2sky ${inpfile} ${TMP_DIR}/${inpfile:r:t}_image.cat ${cat_ot_file}


# Following tests are based on output from imwcs, so only get made if an attempt was made
if ( $WCS_WAS_RUN ) then
  if ( $DEBUG ) grep "^ cra" ${TMP_DIR}/${inpfile:r:t}.errwcsfit | tail -1

  # Read from the output files the residuals on the final fit
  set XYRESID = `grep "^# Mean  dx" ${TMP_DIR}/${inpfile:r:t}.wcsfit | tail -1 | /bin/awk '{print $NF}'`
  set RDRESID = `grep "^# Mean dra" ${TMP_DIR}/${inpfile:r:t}.wcsfit | tail -1 | /bin/awk '{print $NF}'`
  if ( $DEBUG ) echo XY residual = $XYRESID RA,dec residual = $RDRESID
  ${MYBIN}sethead ${fittedfile} WCSRDRES="$RDRESID" / '[arcsec] WCS fitting residuals, mean and sigma'

  if ( $DEBUG ) echo "Input RA,Dec, Rot = $INPUT_RA $INPUT_DEC $INPUT_ROT "
  set OUTPUT_RA  = `${MYBIN}gethead ${fittedfile} CRVAL1`
  set OUTPUT_DEC  = `${MYBIN}gethead ${fittedfile} CRVAL2`
  set OUTPUT_ROT  = `${MYBIN}gethead ${fittedfile} CROTA1`
  if ( $DEBUG ) echo "Output RA,Dec, Rot = $OUTPUT_RA $OUTPUT_DEC $OUTPUT_ROT "
  # Calculate cos(dec) which will be required later for working out RA separations
  set COSDEC = `echo $OUTPUT_DEC | awk '{printf("%15.13f\n",cos($1*3.1415927/180))}'`
  set COSDEC = `echo $COSDEC | sed 's/-/_/'`
  # Dispose of the +/- symbol for the benefit of dc
  set INPUT_DEC = `echo $INPUT_DEC | sed 's/-/_/'`
  set OUTPUT_DEC = `echo $OUTPUT_DEC | sed 's/-/_/'`
  set INPUT_ROT = `echo $INPUT_ROT | sed 's/-/_/'`
  set OUTPUT_ROT = `echo $OUTPUT_ROT | sed 's/-/_/'`
  # Calc how far the new WCS poiting is from the nominal telescope pointing
  set DEL_DEC = ` echo " 10 k $OUTPUT_DEC $INPUT_DEC - 3600 * 1 k 1 / p " | dc `  
  set INPUT_DEC = `echo $INPUT_DEC | sed 's/_/-/'`
  set DEL_RA = `echo " 10 k $OUTPUT_RA $INPUT_RA - $COSDEC * 3600 * 1 k 1 / p " | dc `
  set DEL_ROT = ` echo " $OUTPUT_ROT $INPUT_ROT - 360 % 3 k 1 / p " | dc `  
  if ( $DEBUG ) echo "Change induced by WCS fitting:"
  if ( $DEBUG ) echo "  RA change:    $DEL_RA arcsec"
  if ( $DEBUG ) echo "  dec change:    $DEL_DEC arcsec"
  ${MYBIN}sethead ${fittedfile} WCSDELRA=$DEL_RA / '[arcsec] Shift of fitted WCS w.r.t. nominal pointing'
  ${MYBIN}sethead ${fittedfile} WCSDELDE=$DEL_DEC / '[arcsec] Shift of fitted WCS w.r.t. nominal pointing'
  ${MYBIN}sethead ${fittedfile} WCSDELRO=$DEL_ROT / '[deg] Shift of fitted rotator WCS w.r.t. nominal'
  # Combine delRA and delDEC into a pointing error. (First convert the - to _ in the strings)
  set DEL_RA = `echo $DEL_RA | sed 's/-/_/'`
  set DEL_DEC = `echo $DEL_DEC | sed 's/-/_/'`
  set DEL_POINT = `echo " 10 k $DEL_DEC $DEL_DEC * $DEL_RA $DEL_RA * + v 0 k 1 / p" | dc`
  ${MYBIN}sethead ${fittedfile} WCSDELPO=$DEL_POINT / '[arcsec] Shift of fitted WCS w.r.t. nominal'
  if ( $DEBUG ) echo "  Pointing change : $DEL_POINT arcsec"
  if ( $DEBUG ) echo "  Rot change:    $DEL_ROT deg"
  if ( $DEBUG ) echo
  # If these values are unreasonable, we want to scrap the fit and use the nominal
  if ( $DEL_POINT >= $MAX_POINT_CHANGE ) set THIS_CAT_FAILED  = 1

  # Convert DEL_ROT to an abs(integer)
  # This is convoluted! Strip the minus sign. Convert to integer. Convert to range -180 -> +180. Strip the minus sign. Test against limit
  # This is more succint, when you get time to change it set TEST_ROT = `echo $DEL_ROT | sed 's/-//' | sed 's/\..*//' `
  set TEST_ROT = `echo $DEL_ROT | sed 's/-//'`
  set TEST_ROT = `echo "0 k $TEST_ROT 1 / p " | dc `  
  if ( $TEST_ROT > 180 ) @ TEST_ROT -= 360 
  set TEST_ROT = `echo $TEST_ROT | sed 's/-//'`
  if ( $TEST_ROT >= $MAX_ROT_CHANGE ) set THIS_CAT_FAILED = 1

else 
  if ( $DEBUG ) echo "Change induced by WCS fitting:"
  if ( $DEBUG ) echo "  RA change:    <imwcs not run>"
  if ( $DEBUG ) echo "  dec change:    <imwcs not run>"
  if ( $DEBUG ) echo "  Pointing change : <imwcs not run>"
  if ( $DEBUG ) echo "  Rot change:   <imwcs not run> "
endif   


#
# If NOMINAL_NEEDED is already flagged then there was some non reference catalogue dependent error
# and there is no use going back and trying the bext one
#
if ( (! $NOMINAL_NEEDED ) && ( $THIS_CAT_FAILED ) ) then 
  echo At the end of $inpfile and THIS_CAT_FAILED shows an error
  if ( $last_chance ) then
    echo "    and this was our last catalogue. Nomincal WCS will be set "
    set NOMINAL_NEEDED = 2
  else
    echo "    but we still have more reference catalogues to try"
    @ REFCAT++
    #
    # Goto jumps back to before the reference catalogue extraction and imwcs fitting
    #
    goto LOOP_START
  endif
endif

if ( ${ASFIT_STATUS} != 0 ) then 
  echo At the end of $inpfile and ASFIT_STATUS shows an error
endif


if ( ${NOMINAL_NEEDED} == 0 ) then
  echo "    Create new file with WCS"
  set final_file = ${inpfile}.wcs2
  mv ${inpfile}.bak $final_file
# This is a nasty fudge. Ugly and not reliable. It could easily break at any time due to small changes in the
# arrangement of FITS keyword headers. imwcs and sethead can get in a mess with long comments and end
# up corrupting the keyword. In order to prevent this, I manually truncate the comments on those keywords
# which routinely cause troubles. Others may do on some occaisions and I have not noticed. The real 
# solution is to fix sethead, but Mink's code is fairly inpenetable.
  ${MYBIN}fits_modify_comment_static ${final_file} RADECSYS '[FK4, FK5] Fundamental coordinate system'
  ${MYBIN}fits_modify_comment_static ${final_file} EQUINOX 'Date of the coordinate system'
  ${MYBIN}sethead $final_file PIXSCALE=`${MYBIN}gethead $fittedfile PIXSCALE` / '[arcsec/pixel] Nominal pixel scale on sky'
  ${MYBIN}sethead $final_file   CTYPE1=`${MYBIN}gethead $fittedfile CTYPE1` / ''
  ${MYBIN}sethead $final_file   CTYPE2=`${MYBIN}gethead $fittedfile CTYPE2` / ''
  ${MYBIN}sethead $final_file   CRPIX1=`${MYBIN}gethead $fittedfile CRPIX1` / ''
  ${MYBIN}sethead $final_file   CRPIX2=`${MYBIN}gethead $fittedfile CRPIX2` / ''
  ${MYBIN}sethead $final_file   CRVAL1=`${MYBIN}gethead $fittedfile CRVAL1` / '[degrees] World coordinate at the ref pix'
  ${MYBIN}sethead $final_file   CRVAL2=`${MYBIN}gethead $fittedfile CRVAL2` / '[degrees] World coordinate at the ref pix'
  ${MYBIN}sethead $final_file       RA=`${MYBIN}gethead $fittedfile RA` / 'World coordinate at the reference pixel'
  ${MYBIN}sethead $final_file      DEC=`${MYBIN}gethead $fittedfile DEC`	/ 'World coordinate at the reference pixel'
  ${MYBIN}sethead $final_file      WRA=`${MYBIN}gethead $fittedfile WRA`	/ 'Original RA value from TCS before WCS fit'
  ${MYBIN}sethead $final_file     WDEC=`${MYBIN}gethead $fittedfile WDEC`	/ 'Original DEC value from TCS before WCS fit'
  ${MYBIN}sethead $final_file  WROTSKY=`${MYBIN}gethead $fittedfile ROTSKYPA`	/ 'Original sky PA value from TCS before WCS fit'
  set ROTSKYPA = `${MYBIN}gethead $fittedfile CROTA1 | /bin/awk '{print -$1}'`
  ${MYBIN}sethead $final_file ROTSKYPA=${ROTSKYPA}				/ '[degrees] sky position angle'
  ${MYBIN}sethead $final_file  EQUINOX=`${MYBIN}gethead $fittedfile EQUINOX`	/ 'Date of the coordinate system'
  ${MYBIN}sethead $final_file    EPOCH=`${MYBIN}gethead $fittedfile EPOCH`	/ ''
  ${MYBIN}sethead $final_file   CDELT1=`${MYBIN}gethead $fittedfile CDELT1`	/ '[degrees/pixel]'
  ${MYBIN}sethead $final_file   CDELT2=`${MYBIN}gethead $fittedfile CDELT2`	/ '[degrees/pixel]'
  ${MYBIN}sethead $final_file   CROTA1=`${MYBIN}gethead $fittedfile CROTA1` / '[degrees]'
  ${MYBIN}sethead $final_file   CROTA2=`${MYBIN}gethead $fittedfile CROTA2` / '[degrees]'
  ${MYBIN}sethead $final_file   CUNIT1=`${MYBIN}gethead $fittedfile CUNIT1` / ''
  ${MYBIN}sethead $final_file   CUNIT2=`${MYBIN}gethead $fittedfile CUNIT2` / ''
  ${MYBIN}sethead $final_file    CD1_1=`${MYBIN}gethead $fittedfile CD1_1` / 'WCS CD matrix'
  ${MYBIN}sethead $final_file    CD1_2=`${MYBIN}gethead $fittedfile CD1_2` / 'WCS CD matrix'
  ${MYBIN}sethead $final_file    CD2_1=`${MYBIN}gethead $fittedfile CD2_1` / 'WCS CD matrix'
  ${MYBIN}sethead $final_file    CD2_2=`${MYBIN}gethead $fittedfile CD2_2` / 'WCS CD matrix'
  ${MYBIN}sethead $final_file   SECPIX=`${MYBIN}gethead $fittedfile SECPIX` / '[arcsec/pixel] Fitted pixel scale on sky'
  ${MYBIN}sethead $final_file    IMWCS=`${MYBIN}gethead $fittedfile IMWCS` / ''
  ${MYBIN}sethead $final_file WCSRFCAT=`${MYBIN}gethead $fittedfile WCSRFCAT` / ''
  ${MYBIN}sethead $final_file WCSIMCAT=`${MYBIN}gethead $fittedfile WCSIMCAT` / ''
  ${MYBIN}sethead $final_file  WCSNREF=`${MYBIN}gethead $fittedfile WCSNREF` / 'Stars in image available to define WCS'
  ${MYBIN}sethead $final_file WCSMATCH=`${MYBIN}gethead $fittedfile WCSMATCH` / 'Stars in image matched against ref catalogue'
  ${MYBIN}sethead $final_file WCREFCAT=$REFCAT / '0 for failed, 1 for USNO-B, 2 for 2MASS PSC'
  ${MYBIN}sethead $final_file  LONPOLE=`${MYBIN}gethead $fittedfile LONPOLE` / 'Native longitude of celestial pole'
  ${MYBIN}sethead $final_file WCSRDRES=`${MYBIN}gethead $fittedfile WCSRDRES` / '[arcsec] WCS fitting residuals, mean and sigma '
  ${MYBIN}sethead $final_file WCSDELRA=`${MYBIN}gethead $fittedfile WCSDELRA` / '[arcsec] Shift of fitted WCS w.r.t. nominal '
  ${MYBIN}sethead $final_file WCSDELDE=`${MYBIN}gethead $fittedfile WCSDELDE` / '[arcsec] Shift of fitted WCS w.r.t. nominal '
  ${MYBIN}sethead $final_file WCSDELRO=`${MYBIN}gethead $fittedfile WCSDELRO` / '[deg] Rotator shift w.r.t. nominal WCS '
  ${MYBIN}sethead $final_file WCSDELPO=`${MYBIN}gethead $fittedfile WCSDELPO` / '[arcsec] Shift of fitted WCS w.r.t. nominal '
  ${MYBIN}sethead $final_file   WCSSEP=`${MYBIN}gethead $fittedfile WCSSEP` / '[arcsec] Shift of fitted WCS w.r.t. nominal '

else

  echo At the end of $inpfile and NOMINAL_NEEDED says we have to do something
  echo "    Create nominal WCS instead "
  set final_file = ${inpfile}.wcs2
  mv ${inpfile}.bak $final_file
# This is a nasty fudge. Ugly and not reliable. It could easily break at any time due to small changes in the
# arrangement of FITS keyword headers. imwcs and sethead can get in a mess with long comments and end
# up corrupting the keyword. In order to prevent this, I manually truncate the comments on those keywords
# which routinely cause troubles. Others may do on some occaisions and I have not noticed. The real 
# solution is to fix sethead, but Mink's code is fairly inpenetable.
  ${MYBIN}fits_modify_comment_static ${final_file} RADECSYS '[FK4, FK5] Fundamental coordinate system'
  ${MYBIN}fits_modify_comment_static ${final_file} EQUINOX 'Date of the coordinate system'
  ${MYBIN}sethead $final_file      WRA=`${MYBIN}gethead $final_file RA` / 'Original RA value from TCS before WCS fit'
  ${MYBIN}sethead $final_file     WDEC=`${MYBIN}gethead $final_file DEC` / 'Original DEC value from TCS before WCS fit'
  ${MYBIN}sethead $final_file  WROTSKY=`${MYBIN}gethead $final_file ROTSKYPA` / 'Original sky PA value from TCS before WCS fit'
  ${MYBIN}sethead $final_file   CTYPE1=RA---TAN / 'Using nominal WCS from telescope'
  ${MYBIN}sethead $final_file   CTYPE2=DEC--TAN / 'Using nominal WCS from telescope'
  ${MYBIN}sethead $final_file   CRVAL1=$INPUT_RA / 'Using nominal WCS from telescope'
  set INPUT_DEC = `echo $INPUT_DEC | sed 's/_/-/'`
  ${MYBIN}sethead $final_file   CRVAL2=$INPUT_DEC / 'Using nominal WCS from telescope'
  # Set the default reference pixel from the header or as chip centre if ROTCENTX,ROTCENTY missing
  ${MYBIN}sethead $final_file   CRPIX1=$POINT_CENTX / 'Using nominal WCS from telescope'
  ${MYBIN}sethead $final_file   CRPIX2=$POINT_CENTY / 'Using nominal WCS from telescope'
  # Put the minus sign back in ROT if it has been changed to _ for dc
  set INPUT_ROT = `echo $INPUT_ROT | sed 's/_/-/'`
  ${MYBIN}sethead $final_file   CROTA1=$INPUT_ROT / 'Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   CROTA2=$INPUT_ROT / 'Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   SECPIX=$PIXSCALE / 'Use nominal WCS from telescope'
  # Convert pixscale to degrees instead of arcsec
  set PIXSCALE = `echo "10 k $PIXSCALE 3600 / p" | dc `
  ${MYBIN}sethead $final_file   CDELT1=-$PIXSCALE / 'Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   CDELT2=$PIXSCALE / 'Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCSNREF=$N_STARS_TAKEN / 'Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCSMATCH=$NMATCH / 'Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCREFCAT=0 / '0 for failed, 1 for USNO-B, 2 for 2MASS PSC'
  ${MYBIN}sethead $final_file   WCSSEP=0.000 / 'Dummy value: Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCSRDRES="0.0000/0.0000" / 'Dummy value: Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCSDELRA=00.0 / 'Dummy value: Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCSDELDE=00.0 / 'Dummy value: Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCSDELRO=0.000 / 'Dummy value: Use nominal WCS from telescope'
  ${MYBIN}sethead $final_file   WCSDELPO=0.00 / 'Dummy value: Use nominal WCS from telescope'
endif


#
# Over write the old file with the newly created one containing a WCS
#
mv ${final_file} ${inpfile}
# If we have scrapped a fit that was made and we are in DEBUG mode, it is useful
# to keep the fitted file on disk for diagnosis
if ( -e $fittedfile ) then
  if ( (! $DEBUG ) || (! $NOMINAL_NEEDED ) )  rm -f ${fittedfile}
endif 
@ WCS_ERR = $ASFIT_STATUS + $NOMINAL_NEEDED
${MYBIN}sethead ${inpfile} WCS_ERR=$WCS_ERR / 'Error status of WCS fit. 0 for no error'


#
# Delete all the temp files
#
if ( ! $DEBUG ) then 
  rm -f ${TMP_DIR}/${inpfile:r:t}_wcs.cat
  rm -f ${TMP_DIR}/${inpfile:r:t}_image.cat
  rm -f ${TMP_DIR}/${inpfile:r:t}.errwcsfit
  rm -f ${TMP_DIR}/${inpfile:r:t}.wcsfit
endif



if ( $DEBUG ) echo "Astrometric Fit/Catalogue Check script finished."
    
exit 0


syntax:
echo "pipe_astrometric.csh [-v] filename"
echo "  -v optionally turns on verbose output"
exit 1
