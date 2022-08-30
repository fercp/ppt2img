#!/bin/bash
set -eo pipefail
gradle -q packageLibs
mv build/distributions/ppt2img.zip build/ppt2img-lib.zip