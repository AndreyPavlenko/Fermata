#!/bin/bash

FERMATA_PROJ_DIR="$HOME/Fermata"

create_local_props() {
  if [ -z "$KEY_ALIAS" ]; then
    read -p "Enter key alias [default - fermata]: " KEY_ALIAS
    : ${KEY_ALIAS:='fermata'}
  fi

  read -p "Enter password: " PASSWORD
  if [ ${#PASSWORD} -lt 6 ]; then
    echo "The password must be at least 6 characters long!"
    return 1
  fi

  read -p "Confirm password: " PASSWORD2
  if [ "$PASSWORD" != "$PASSWORD2" ]; then
    echo "Passwords do not match!"
    return 1
  fi

  echo "storeFile=$PWD/res/fermata.jks" > "$1"
  echo "keyAlias=$KEY_ALIAS" >> "$1"
  echo "keyPassword=$PASSWORD" >> "$1"
  echo "storePassword=$PASSWORD" >> "$1"
}

[ -d "$FERMATA_PROJ_DIR" ] || git clone --recurse-submodules https://github.com/AndreyPavlenko/Fermata.git "$FERMATA_PROJ_DIR"
cd "$FERMATA_PROJ_DIR"

while [ ! -f local.properties ]; do
  create_local_props local.properties || true
done

[ -z "$1" ] || exec "$@"
