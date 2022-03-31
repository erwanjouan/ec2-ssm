#!/bin/sh

getStatus(){
	aws ec2 describe-instances \
		--instance-ids $1 \
		--query "Reservations[0].Instances[0].State.Name" \
		--output text
}

echo ec2 instance $1

status=$(getStatus $1)

while [ "${status}" != "$2" ]
do
	status=$(getStatus $1)
	printf "|||"
	sleep 5
done
printf " %s " "${status}"
