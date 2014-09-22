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

sudo -u postgres psql -f /vagrant/bootstrap.sql

wget https://bootstrap.pypa.io/get-pip.py
python get-pip.py
pip install awscli

export AWS_ACCESS_KEY_ID=AKIAJ3FZLZXLJNTGE77Q
export AWS_SECRET_ACCESS_KEY=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

# Java
aws s3 cp s3://promotably-java-stash/jdk-7u67-linux-x64.gz /usr/local/

cd /usr/local && tar -xzvf jdk-7u67-linux-x64.gz
ln -s /usr/local/jdk1.7.0_67/bin/java /usr/local/bin/java

# Kafka & Zookeeper
wget http://apache.mirrors.pair.com/kafka/0.8.1.1/kafka_2.10-0.8.1.1.tgz -O /usr/local/kafka_2.10-0.8.1.1.tgz
cd /usr/local && tar -xzvf kafka_2.10-0.8.1.1.tgz
ln -s /usr/local/kafka_2.10-0.8.1.1/ /usr/local/kafka
