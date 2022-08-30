#!/bin/bash
set -eo pipefail
BUCKET=$(aws cloudformation describe-stack-resource --stack-name ppt2img --logical-resource-id bucket --query 'StackResourceDetail.PhysicalResourceId' --output text)
aws s3 cp ppt/sample.pptx s3://$BUCKET/inbound/