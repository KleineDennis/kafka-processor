#!/bin/bash
runuser -l service -c 'java -Xmx6144m \
     -Dconfig.resource=@{AccountName}.conf \
     -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false \
     -Dlisting-images.client-secret=@{ListingImagesApiSecret} \
     -Dstack-name=@{AWS::StackName} -jar @{Service}-@{Version}.jar'

# After the process terminates, we poweroff the machine. The ASG will start a new one if necesarry
logger "No listing-images-kafka-processor terminated. Poweroff!"
sleep 60
poweroff