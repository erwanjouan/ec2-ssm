instanceName:=Ec2ForTest
instanceType:=t2.micro
roleName:=ec2SsmRole
instanceProfileName:=ec2SsmInstanceProfile

deploy:
	@aws iam create-instance-profile --instance-profile-name $(instanceProfileName) || true && \
	aws iam create-role --role-name $(roleName) --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}' || true && \
	aws iam add-role-to-instance-profile --instance-profile-name $(instanceProfileName) --role-name ${roleName} || true && \
	aws iam attach-role-policy --role-name $(roleName) --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore || true && \
	last_amazon_linux_2=$$(aws ec2 describe-images \
		--owners 137112412989 \
		--filters Name=architecture,Values=x86_64 Name=name,Values=amzn2-ami-kernel-* \
		--query 'Images[*].[ImageId]' --output text | sort -k2 -r | head -n1) && \
	sleep 5 && \
	instance_id=$$(aws ec2 run-instances \
		--image-id $${last_amazon_linux_2} \
		--instance-type $(instanceType) \
		--iam-instance-profile Name=$(instanceProfileName) \
		--tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$(instanceName)}]" \
		--query "Instances[0].InstanceId" \
		--output text) && \
	./watch_ec2.sh $${instance_id} running

destroy:
	@instance_id=$$(aws ec2 describe-instances \
		--filters \
			"Name=tag:Name,Values=Ec2ForTest" \
			"Name=instance-state-name,Values=running" \
		--query "Reservations[0].Instances[0].InstanceId" \
		--output text) && \
	echo $${instance_id} && \
	aws ec2 terminate-instances \
		--instance-ids $${instance_id} \
		--query "TerminatingInstances[0].CurrentState.Name"  && \
	./watch_ec2.sh $${instance_id} terminated && \
	aws iam remove-role-from-instance-profile \
		--instance-profile-name $(instanceProfileName) \
		--role-name $(roleName) || true && \
	aws iam delete-instance-profile \
		--instance-profile-name $(instanceProfileName) || true && \
	aws iam detach-role-policy \
		--role-name $(roleName) \
		--policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore || true && \
	aws iam delete-role \
		--role-name $(roleName) || true