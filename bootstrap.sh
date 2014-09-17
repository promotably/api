#!/usr/bin/env bash

apt-get update
apt-get -y upgrade

PG_VERSION=9.3

apt-get -y install "postgresql-$PG_VERSION" "postgresql-contrib-$PG_VERSION"

PG_CONF="/etc/postgresql/$PG_VERSION/main/postgresql.conf"
PG_HBA="/etc/postgresql/$PG_VERSION/main/pg_hba.conf"
PG_DIR="/var/lib/postgresql/$PG_VERSION/main"

# Edit postgresql.conf to change listen address to '*':
sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" "$PG_CONF"

# Append to pg_hba.conf to add password auth:
echo "host    all             all             all                     md5" >> "$PG_HBA"

# Restart so that all new config is loaded:
service postgresql restart

# Kafka & Zookeeper
wget http://apache.mirrors.pair.com/kafka/0.8.1.1/kafka_2.10-0.8.1.1.tgz -O kafka_2.10-0.8.1.1.tgz
tar -xzvf kafka_2.10-0.8.1.1.tgz
sudo -u postgres psql -f /vagrant/bootstrap.sql

