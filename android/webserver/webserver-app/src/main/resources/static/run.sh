#!/bin/sh

THIS_DIR=$(readlink -f $(dirname $0))
DIST_DIR=$(readlink -f $THIS_DIR/../../../../dist/app)

SHELL_SCRIPT_NAME=http-static.sh
PREDEF_SHELL_SCRIPT=$DIST_DIR/bin/$SHELL_SCRIPT_NAME
SHELL_SCRIPT=${SHELL_SCRIPT:=$SHELL_SCRIPT_NAME}

if which $SHELL_SCRIPT 1>/dev/null ; then
  echo found
else
  if [ -e $PREDEF_SHELL_SCRIPT ] ; then
    SHELL_SCRIPT=$PREDEF_SHELL_SCRIPT
  else
    echo not found $SHELL_SCRIPT_NAME
    exit 1
  fi
fi

$SHELL_SCRIPT threads idle 15s threads min 2 threads max 10 connector idle 15s
