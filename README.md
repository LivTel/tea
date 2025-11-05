# Telescope Embedded Agent.

The Telescope Embedded Agent is the software running at the telescope end (ltproxy) that receives and actions RTML (Remote Telescope Markup Language) documents from the Node Agent (NA). 

Depending on the RTML document sent to the TEA, a PhaseII group can be submitted into the phase2 database, or if a Target of Oppurtunity document is submitted, the TEA can take direct immediate control of the telescope (via the RCS TOCA agent) and immediately acquire the requested images.

## Building

From the tea directory, '''ant jar''' will build  /home/dev/bin/estar/javalib/tea.jar ,  ready for deployment to ltproxy.

## Deployment

The built tea.jar (/home/dev/bin/estar/javalib/tea.jar) should be deployed to ltproxy:/proxy/tea/javalib/tea.jar.

The tea.properties (/home/dev/src/estar/tea/config/ltproxy.tea.properties) should be deployed to ltproxy:/proxy/tea/config/tea.properties .

The filter-combo.properties (/home/dev/src/estar/tea/config/ltproxy_filter-combo.properties) should be deployed to ltproxy:/proxy/tea/config/filter-combo.properties .

The TEA also requires the following dependencies installing in ltproxy:/proxy/tea/javalib/:
- org_estar_astrometry.jar
- org_estar_cluster.jar
- org_estar_fits.jar
- org_estar_io.jar
- org_estar_io_test.jar
- org_estar_rtml.jar
- org_estar_rtml_test.jar
- org_estar_toop.jar
- org_estar_toop_test.jar
- activation.jar
- mailapi.jar
- smtp.jar

## Software structure

There is a little documentation on RTML document handling [here](java/org/estar/tea/README.md).

