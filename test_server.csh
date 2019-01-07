setenv CLASSPATH ${CLASSPATH}:/occ/tea/javalib/org_estar_io.jar:/occ/tea/javalib/org_estar_io_test.jar
setenv CLASSPATH ${CLASSPATH}:/occ/tea/javalib/org_estar_rtml.jar:/occ/tea/javalib/org_estar_rtml_test.jar
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:3 -iaport 1234 -observation -name m5#1 -target_ident test-ident -ra 15:18:33.8 -dec +02:04:58.0 -exposure 30000 ms 1 -start_date 2005-05-09T12:00:00 -end_date 2005-05-13T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > localtest_3.rtml
java org.estar.io.test.SendFile localhost 8080 ~/localtest_3.rtml > ~/localtest_3_score_reply.rtml

java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:3 -iaport 1234 -observation -name m5#1 -target_ident test-ident -ra 15:18:33.8 -dec +02:04:58.0 -exposure 30000 ms 1 -start_date 2005-05-09T12:00:00 -end_date 2005-05-13T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.79 -completion_time 2005-05-10T09:04:17 > localtest_3_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/localtest_3_request.rtml > ~/localtest_3_request_reply.rtml

# expired test
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:4 -iaport 1234 -observation -name m5#1 -target_ident test-ident -ra 15:18:33.8 -dec +02:04:58.0 -exposure 30000 ms 1 -start_date 2005-05-09T12:00:00 -end_date 2005-05-10T10:10:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.79 -completion_time 2005-05-10T09:04:17 > localtest_4_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/localtest_4_request.rtml > ~/localtest_4_request_reply.rtml

# ftn m86
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m86:1 -iaport 1234 -observation -name m86#1 -target_ident r60 -ra 12:26:11.5 -dec +12:56:45.0 -exposure 60000 ms 1 -start_date 2005-05-19T12:00:00 -end_date 2005-05-31T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > ~/tmp/localtestm86_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_1_score.rtml > ~/tmp/localtestm86_1_score_reply.rtml

java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m86:1 -iaport 1234 -observation -name m86#1 -target_ident r60 -ra 12:26:11.5 -dec +12:56:45.0 -exposure 60000 ms 1 -start_date 2005-05-19T12:00:00 -end_date 2005-05-31T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.91 -completion_time 2005-05-19T11:09:58 > ~/tmp/localtestm86_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_1_request.rtml > ~/tmp/localtestm86_1_request_reply.rtml

# ftn localtest:m86:2
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m86:2 -iaport 1234 -observation -name m86#1 -target_ident v60 -ra 12:26:11.5 -dec +12:56:45.0 -exposure 60000 ms 1 -start_date 2005-05-11T12:00:00 -end_date 2005-05-12T18:00:00 -device ratcam camera optical V -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > ~/tmp/localtestm86_2_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_2_score.rtml > ~/tmp/localtestm86_2_score_reply.rtml

java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m86:2 -iaport 1234 -observation -name m86#1 -target_ident v60 -ra 12:26:11.5 -dec +12:56:45.0 -exposure 60000 ms 1 -start_date 2005-05-11T12:00:00 -end_date 2005-05-12T18:00:00 -device ratcam camera optical V -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.91 -completion_time 2005-05-12T10:44:49 > ~/tmp/localtestm86_2_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_2_request.rtml > ~/tmp/localtestm86_2_request_reply.rtml

# ftn localtest:m86:3
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m86:3 -iaport 1234 -observation -name m86#3 -target_ident r60 -ra 12:26:11.5 -dec +12:56:45.0 -exposure 60000 ms 1 -start_date 2005-05-19T12:00:00 -end_date 2005-05-31T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.91 -completion_time 2005-05-19T11:09:58 > ~/tmp/localtestm86_3_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_3_request.rtml > ~/tmp/localtestm86_3_request_reply.rtml

# ftn localtest:m86:5
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m86:5 -iaport 1234 -observation -name m86 -target_ident r10 -ra 12:26:11.5 -dec +12:56:45.0 -exposure 10000 ms 1 -start_date 2005-06-01T12:00:00 -end_date 2005-07-01T18:00:00 -series_constraint_count 5 -series_constraint_interval PT3H -series_constraint_tolerance PT2H50M -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.91 -completion_time 2005-05-19T11:09:58 > ~/tmp/localtestm86_5_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_5_request.rtml > ~/tmp/localtestm86_5_request_reply.rtml

# ftn localtest:m15:1
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m15:1 -iaport 1234 -observation -name m15:1 -target_ident r30 -ra 21:29:58.3 -dec +12:10:01.0 -exposure 30000 ms 1 -start_date 2005-05-25T12:00:00 -end_date 2005-05-31T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > ~/tmp/localtestm15_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm15_1_score.rtml > ~/tmp/localtestm15_1_score_reply.rtml

request
-------
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m15:1 -iaport 1234 -observation -name m15:1 -target_ident r30 -ra 21:29:58.3 -dec +12:10:01.0 -exposure 30000 ms 1 -start_date 2005-05-25T12:00:00 -end_date 2005-05-31T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.90 -completion_time 2005-05-25T13:17:46 > ~/tmp/localtestm15_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm15_1_request.rtml > ~/tmp/localtestm15_1_request_reply.rtml

# ftn localtest:m67:1
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m67:1 -iaport 1234 -observation -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -start_date 2005-05-26T12:00:00 -end_date 2005-05-31T18:00:00 -series_constraint_count 10 -series_constraint_interval PT1H -series_constraint_tolerance PT50M -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > ~/tmp/localtestm67_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm67_1_score.rtml > ~/tmp/localtestm67_1_score_reply.rtml

request
-------
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m67:1 -iaport 1234 -observation -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -start_date 2005-05-26T12:00:00 -end_date 2005-05-31T18:00:00 -series_constraint_count 10 -series_constraint_interval PT1H -series_constraint_tolerance PT50M -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.90 -completion_time 2005-05-26T11:36:55 > ~/tmp/localtestm67_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm67_1_request.rtml > ~/tmp/localtestm67_1_request_reply.rtml

# ftn localtest:m67:2
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m67:2 -iaport 1234 -observation -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -start_date 2005-06-01T12:00:00 -end_date 2005-06-30T18:00:00 -series_constraint_count 10 -series_constraint_interval PT2H -series_constraint_tolerance PT1H50M -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > ~/tmp/localtestm67_2_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm67_2_score.rtml > ~/tmp/localtestm67_2_score_reply.rtml

request
-------
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m67:2 -iaport 1234 -observation -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -start_date 2005-06-01T12:00:00 -end_date 2005-06-30T18:00:00 -series_constraint_count 10 -series_constraint_interval PT2H -series_constraint_tolerance PT1H50M -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.90 -completion_time 2005-06-01T11:36:55 > ~/tmp/localtestm67_2_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm67_2_request.rtml > ~/tmp/localtestm67_2_request_reply.rtml

# ftn localtest:107599:1
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:107599:1 -iaport 1234 -observation -name 107-599 -target_ident r30 -ra 15:39:09 -dec -00:14:28.0 -exposure 30000 ms 1 -start_date 2005-06-09T12:00:00 -end_date 2005-06-30T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > ~/tmp/localtest107599_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtest107599_1_score.rtml > ~/tmp/localtest107599_1_score_reply.rtml

request
-------
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:107599:1 -iaport 1234 -observation -name 107-599 -target_ident r30 -ra 15:39:09 -dec -00:14:28 -exposure 30000 ms 1 -start_date 2005-06-09T12:00:00 -end_date 2005-06-30T18:00:00 -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.76 -completion_time 2005-06-01T11:36:55 > ~/tmp/localtest107599_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtest107599_1_request.rtml > ~/tmp/localtest107599_1_request_reply.rtml

# ltproxy test
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m86:1 -iaport 1234 -observation -name m86#1 -target_ident test-ident -ra 12:26:11.5 -dec +12:56:45.0 -exposure 60000 ms 1 -start_date 2005-05-10T12:00:00 -end_date 2005-05-20T12:00:00 -device ratcam camera optical R -binning 2 2 -project "TEA01" -contact -contact_name "Chris Mottram" -contact_user TEST/estar > ~/tmp/localtestm86_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_1_score.rtml > ~/tmp/localtestm86_1_score_reply.rtml

java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m86:1 -iaport 1234 -observation -name m86#1 -target_ident test-ident -ra 12:26:11.5 -dec +12:56:45.0 -exposure 60000 ms 1 -start_date 2005-05-10T12:00:00 -end_date 2005-05-20T12:00:00 -device ratcam camera optical R -binning 2 2 -project "TEA01" -contact -contact_name "Chris Mottram" -contact_user TEST/estar -document_score 0.82426 -completion_time 2005-05-10T20:00:27 > ~/tmp/localtestm86_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm86_1_request.rtml > ~/tmp/localtestm86_1_request_reply.rtml


# multi-observation test m67:3
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m67:3 -iaport 1234 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -observation -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -start_date 2005-05-26T12:00:00 -end_date 2005-05-31T18:00:00 -series_constraint_count 10 -series_constraint_interval PT1H -series_constraint_tolerance PT50M -device ratcam camera optical R -binning 2 2 -observation -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -start_date 2005-05-26T12:00:00 -end_date 2005-05-31T18:00:00 -series_constraint_count 10 -series_constraint_interval PT1H -series_constraint_tolerance PT50M -device ratcam camera optical V -binning 2 2 -observation -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -start_date 2005-05-26T12:00:00 -end_date 2005-05-31T18:00:00 -series_constraint_count 10 -series_constraint_interval PT1H -series_constraint_tolerance PT50M -device ratcam camera optical B -binning 2 2 -document_score 0.90 -completion_time 2005-05-26T11:36:55 > ~/tmp/localtestm67_3_request.rtml

# toop score tests
# m67 on ftn is SET in the afternoons
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:toop:1 -iaport 1234 -project "Planetsearch1" -contact -contact_name "Chris Mottram" -contact_user Robonet/keith.horne -observation -toop -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -device ratcam camera optical R -binning 2 2 > ~/tmp/localtesttoop_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtesttoop_1_score.rtml > ~/tmp/localtesttoop_1_score_reply.rtml
# this one should fail
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:toop:2 -iaport 1234 -project "Planetsearch1" -contact -contact_name "Chris Mottram" -contact_user Robonet/keith.horne -observation -toop -name m67 -target_ident r10 -ra 08:50:50.1 -dec +11:48:45.0 -exposure 10000 ms 1 -device ratcam camera optical R -binning 2 2 -start_date 2005-05-26T12:00:00 -end_date 2005-05-31T18:00:00 -series_constraint_count 10 -series_constraint_interval PT1H -series_constraint_tolerance PT50M > ~/tmp/localtesttoop_2_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtesttoop_2_score.rtml > ~/tmp/localtesttoop_2_score_reply.rtml
# m2 on ftn is up in the afternoons DO NOT USE - uses microlensing proposal
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:toop:3 -iaport 1234 -project "Planetsearch1" -contact -contact_name "Chris Mottram" -contact_user Robonet/keith.horne -observation -toop -name m2 -target_ident r10 -ra 21:33:27.2 -dec -00:49:24.0 -exposure 10000 ms 1 -device ratcam camera optical R -binning 2 2 > ~/tmp/localtesttoop_3_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtesttoop_3_score.rtml > ~/tmp/localtesttoop_3_score_reply.rtml
# m2 on ftn is up toop
#score
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:toop:4 -iaport 1234 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -observation -toop -name m2 -target_ident r10 -ra 21:33:27.2 -dec -00:49:24.0 -exposure 10000 ms 1 -device ratcam camera optical R -binning 2 2 > ~/tmp/localtesttoop_4_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtesttoop_4_score.rtml > ~/tmp/localtesttoop_4_score_reply.rtml
#toop observation - this will interrupt people!
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:toop:4 -iaport 1234 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -observation -toop -name m2 -target_ident r10 -ra 21:33:27.2 -dec -00:49:24.0 -exposure 10000 ms 1 -device ratcam camera optical R -binning 2 2 > ~/tmp/localtesttoop_4_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtesttoop_4_request.rtml > ~/tmp/localtesttoop_4_request_reply.rtml

# ftn localtest:m11:1
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m11:1 -iaport 1234 -observation -name m11:1 -target_ident r10 -ra 18:51:06.0 -dec -06:16:00.0 -exposure 10000 ms 1 -start_date 2005-06-20T12:00:00 -end_date 2005-07-31T18:00:00 -series_constraint_count 30 -series_constraint_interval PT12H -series_constraint_tolerance PT11H50M -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar > ~/tmp/localtestm11_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm11_1_score.rtml > ~/tmp/localtestm11_1_score_reply.rtml

request
-------
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m11:1 -iaport 1234 -observation -name m11:1 -target_ident r10 -ra 18:51:06.0 -dec -06:16:00.0 -exposure 10000 ms 1 -start_date 2005-06-20T12:00:00 -end_date 2005-07-31T18:00:00 -series_constraint_count 30 -series_constraint_interval PT12H -series_constraint_tolerance PT11H50M -device ratcam camera optical R -binning 2 2 -project "agent_test" -contact -contact_name "Chris Mottram" -contact_user TMC/estar -document_score 0.7 -completion_time 2005-07-31T17:00:00 > ~/tmp/localtestm11_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm11_1_request.rtml > ~/tmp/localtestm11_1_request_reply.rtml

# lt localtest:m11:1 (series)
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m11:1 -iaport 1234 -observation -name m11 -target_ident r10 -ra 18:51:06.0 -dec -06:16:00.0 -exposure 10000 ms 1 -start_date 2005-07-27T00:00:00 -end_date 2005-07-31T18:00:00 -series_constraint_count 30 -series_constraint_interval PT12H -series_constraint_tolerance PT11H50M -device ratcam camera optical R -binning 2 2 -project "TEA01" -contact -contact_name "Chris Mottram" -contact_user TEST/estar > ~/tmp/localtestm11_1_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm11_1_score.rtml > ~/tmp/localtestm11_1_score_reply.rtml

request
-------
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m11:1 -iaport 1234 -observation -name m11 -target_ident r10 -ra 18:51:06.0 -dec -06:16:00.0 -exposure 10000 ms 1 -start_date 2005-07-27T00:00:00 -end_date 2005-08-31T18:00:00 -series_constraint_count 30 -series_constraint_interval PT12H -series_constraint_tolerance PT11H50M -device ratcam camera optical R -binning 2 2 -project "TEA01" -contact -contact_name "Chris Mottram" -contact_user TEST/estar -document_score 0.6 -completion_time 2005-07-31T17:00:00 > ~/tmp/localtestm11_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm11_1_request.rtml > ~/tmp/localtestm11_1_request_reply.rtml


#lt toop observation of m11 - this will interrupt people!
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:toop:m11:1 -iaport 1234 -project "TEA01" -contact -contact_name "Chris Mottram" -contact_user TEST/estar -observation -toop -name m11 -target_ident r10 -ra 18:51:06 -dec -06:16:00.0 -exposure 10000 ms 1 -device ratcam camera optical R -binning 2 2 > ~/tmp/localtesttoopm11_1_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtesttoopm11_1_request.rtml > ~/tmp/localtesttoopm11_1_request_reply.rtml

# lt localtest:m11:2 (flexible)
score
-----
java org.estar.rtml.test.TestCreate -score -iahost localhost -iaid localtest:m11:2 -iaport 1234 -observation -name m11 -target_ident r10 -ra 18:51:06.0 -dec -06:16:00.0 -exposure 10000 ms 1 -start_date 2005-08-04T00:00:00 -end_date 2005-08-31T18:00:00 -device ratcam camera optical R -binning 2 2 -project "TEA01" -contact -contact_name "Chris Mottram" -contact_user TEST/estar > ~/tmp/localtestm11_2_score.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm11_2_score.rtml > ~/tmp/localtestm11_2_score_reply.rtml

request
-------
java org.estar.rtml.test.TestCreate -request -iahost localhost -iaid localtest:m11:2 -iaport 1234 -observation -name m11 -target_ident r10 -ra 18:51:06.0 -dec -06:16:00.0 -exposure 10000 ms 1 -start_date 2005-08-04T00:00:00 -end_date 2005-08-31T18:00:00 -device ratcam camera optical R -binning 2 2 -project "TEA01" -contact -contact_name "Chris Mottram" -contact_user TEST/estar -document_score 0.6 -completion_time 2005-08-31T18:00:00 > ~/tmp/localtestm11_2_request.rtml

java org.estar.io.test.SendFile localhost 8080 ~/tmp/localtestm11_2_request.rtml > ~/tmp/localtestm11_2_request_reply.rtml
