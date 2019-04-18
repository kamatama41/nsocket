#!/usr/bin/env bash

cd $(dirname $0)
docker-compose build
docker-compose run cert-generator
