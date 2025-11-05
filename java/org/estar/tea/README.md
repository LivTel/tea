# TEA software

## Entrypoint

The Telescope Embedded Agent is run on ltproxy. The normal /etc/init.d startup schema is used, which calls the installed '''/etc/init.d/tea_init'''. This calls '''$DEPLOY_BIN/tea start''' ('''/proxy/bin/tea''') which sets up the enviroment and them invokes Java. The eventual Java command line is currently:

'''
/usr/java/jdk1.6.0_45/bin/java -DTEA -Djava.security.policy=/proxy/tmp/policy.dat -Djava.rmi.server.codebase=file:////proxy/tea/javalib/tea.jar -Dastrometry.impl=ngat.astrometry.TestCalculator -Djava.security.egd=file:/dev/urandom -DregisterHandler=true -Djava.rmi.server.host=ltproxy -Xss2m org.estar.tea.TelescopeEmbeddedAgent lt /proxy/tea/config/tea.properties
'''

## Document handling

'''TelescopeEmbeddedAgent.java''' The '''start''' method creates an instance of '''EnhancedEmbeddedAgentRequestHandler''', which is bound to the RMI end-point: "rmi://localhost/EARequestHandler".

'''EnhancedEmbeddedAgentRequestHandler''' has a series of methods that can be invoked from the NodeAgent2 (as specified in the '''EmbeddedAgentRequestHandler''' interface:
- handleScore
- handleRequest
- handleAbort
- handleUpdate

Each '''EnhancedEmbeddedAgentRequestHandler''' handler creates an instance of '''EnhancedDocumentHandler''', and calls
the appropriate handle method to do the document processing requested.

The '''EnhancedDocumentHandler''' usually creates an instance of '''Phase2ExtractorTNG''' and calls the relevant handle mathod in that instance. Some of the scoring is actually done in '''EnhancedDocumentHandler''', Phase2ExtractorTNG is used to extract the phase2 group from the RTML.

