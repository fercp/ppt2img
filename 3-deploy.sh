#!/bin/bash
set -eo pipefail
ARTIFACT_BUCKET=$(cat bucket-name.txt)
TEMPLATE=template.yml

gradle build -i
aws cloudformation package --template-file $TEMPLATE --s3-bucket $ARTIFACT_BUCKET --output-template-file out.yml
aws cloudformation deploy --template-file out.yml --stack-name ppt2img --capabilities CAPABILITY_NAMED_IAM