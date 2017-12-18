#!/usr/bin/env bash

function stop_docker {
    docker-compose stop
}

trap stop_docker EXIT

if [ "$#" -ne 1 ]; then
    echo "You need to specify a directory"
    exit 0
fi

docker-compose up -d visual-search

docker run -it --mount type=bind,source="$1",target="/images" --mount type=bind,source=$PWD/find_and_index_images.py,target=/find_and_index_images.py  python:3 python /find_and_index_images.py
