#!/usr/bin/env bash
THIS_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))
WEBSRV_BIN_DIR=$(cd $THIS_DIR/../../android/webserver/webserver-app/dist/app/bin && pwd)
source $WEBSRV_BIN_DIR/configure.sh

PORT=8080
DATA_HOME_URL_PATH=/home

http-static.sh \
  port ${PORT} \
  index file dist/index.html \
  index set p1 = v1 \
  index set p2 = v2 \
  index set MIDIED_MIDI_READ_HTTP = http://localhost:${PORT}/client \
  bind /emu/ui ../../android/webserver/webserver-app/src/main/resources/static/ \
  bind / ${THIS_DIR}/dist \
  bind ${DATA_HOME_URL_PATH} $THIS_DIR/test-data rw \
  index set MIDIED_FS_HOME_TYPE = http \
  index set MIDIED_FS_HOME_MOUNT = http://localhost:${PORT}${DATA_HOME_URL_PATH} \
  threads idle 15s \
  threads min 2 \
  threads max 10 \
  connector idle 15s
