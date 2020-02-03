#!/bin/sh

HBROOT=/homeblocks
PIDF="$HBROOT/homeblocks.pid"
LOG="$HBROOT/homeblocks.log"

date > $LOG

if [[ -f "$PIDF" ]]; then
	echo "$PIDF file found. Killing old server..." >> $LOG
	kill `cat "$PIDF"` >> $LOG
fi

cd "$HBROOT/kotlin"
java -classpath target/homeblocks-0.0.1-fat.jar net.homeblocks.MainKt >> $LOG 2>&1 &
echo $! > "$PIDF"
