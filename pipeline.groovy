def instanceName = "Ec2SsmConnection"
def instanceType = "t2.micro"
def roleName = "ec2RoleWithSsmCoreEnabled"
def instanceProfileName = "ec2InstanceProfileWithSsmCoreEnabled"

pipeline {
    agent { label 'aws-cli' }
    environment {
        AWS_ACCESS_KEY_ID = credentials('aws_access_key_id')
        AWS_SECRET_ACCESS_KEY = credentials('aws_secret_access_key')
        AWS_DEFAULT_REGION='eu-west-1'
    }
    stages {
        stage('Create Role') {
            when { expression { return params.Create } }
            steps {
                sh """
                    aws iam create-instance-profile --instance-profile-name ${instanceProfileName} || true && \
                    aws iam create-role --role-name ${roleName} --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}' || true && \
                    aws iam add-role-to-instance-profile --instance-profile-name ${instanceProfileName} --role-name ${roleName} || true && \
    	            aws iam attach-role-policy --role-name ${roleName} --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore || true && \
    	            sleep 5
                """
            }
        }
        stage('Create EC2') {
            when { expression { return params.Create } }
            steps {
                script {
                    def last_amazon_linux_2 = sh(script: "aws ec2 describe-images --owners 137112412989 --filters Name=architecture,Values=x86_64 Name=name,Values=amzn2-ami-kernel-* --query 'Images[*].[ImageId]' --output text | sort -k2 -r | head -n1", returnStdout: true).trim()
                    sh "echo ${last_amazon_linux_2}"
                    sh """
                        aws ec2 run-instances \
                            --image-id ${last_amazon_linux_2} \
                            --instance-type ${instanceType} \
                            --iam-instance-profile Name=${instanceProfileName} \
                            --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${instanceName}}]" \
                            --query "Instances[0].InstanceId" \
                            --output text
                    """
                }
            }
        }
        stage('Destroy EC2') {
            when { expression { return !params.Create } }
            steps {
                script {
                    def instance_id = sh(script: "aws ec2 describe-instances --filters Name=tag:Name,Values=${instanceName} Name=instance-state-name,Values=running --query 'Reservations[0].Instances[0].InstanceId' --output text", returnStdout: true).trim()
                    sh "aws ec2 terminate-instances --instance-ids ${instance_id}"
                }
            }
        }
        stage('Delete Role') {
            when { expression { return !params.Create } }
            steps {
                sh """
                    aws iam remove-role-from-instance-profile --instance-profile-name ${instanceProfileName} --role-name ${roleName} || true && \
	                aws iam delete-instance-profile --instance-profile-name ${instanceProfileName} || true && \
	                aws iam detach-role-policy --role-name ${roleName} --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore || true && \
	                aws iam delete-role --role-name ${roleName} || true
                """
            }
        }
    }
}
