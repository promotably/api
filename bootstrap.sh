#!/usr/bin/env bash

apt-get update
apt-get -y upgrade

# Generic Stuff, needed for VirtualBox Guest Addition
apt-get -y install linux-headers-generic build-essential dkms

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

sudo -u postgres psql -f /vagrant/vagrant-bootstrap/bootstrap.sql

wget https://bootstrap.pypa.io/get-pip.py
python get-pip.py
pip install awscli

export AWS_ACCESS_KEY_ID=AKIAJ3FZLZXLJNTGE77Q
export AWS_SECRET_ACCESS_KEY=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

# Java
aws s3 cp s3://promotably-java-stash/jdk-7u67-linux-x64.gz /usr/local/
cd /usr/local && tar -xzvf jdk-7u67-linux-x64.gz
ln -s /usr/local/jdk1.7.0_67/bin/java /usr/local/bin/java
rm /usr/local/jdk-7u67-linux-x64.gz

# Kafka & Zookeeper
useradd --system --shell /bin/bash kafka-zookeeper
wget http://apache.mirrors.pair.com/kafka/0.8.1.1/kafka_2.10-0.8.1.1.tgz -O /usr/local/kafka_2.10-0.8.1.1.tgz
cd /usr/local && tar -xzvf kafka_2.10-0.8.1.1.tgz
ln -s /usr/local/kafka_2.10-0.8.1.1 /usr/local/kafka
chown -R kafka-zookeeper /usr/local/kafka
rm /usr/local/kafka_2.10-0.8.1.1.tgz
cp /vagrant/vagrant-bootstrap/zookeeper.conf /etc/init/
cp /vagrant/vagrant-bootstrap/kafka.conf /etc/init/

start zookeeper

KAFKA_CONFIG="/usr/local/kafka/config/server.properties"

# Edit kafka.conf to change advertised host name to 127.0.0.1:
sed -i "s/#advertised.host.name=<hostname routable by clients>/advertised.host.name=127.0.0.1/" "$KAFKA_CONFIG"

# Redis
cd ~/ && wget http://download.redis.io/releases/redis-2.8.17.tar.gz
tar -xzvf redis-2.8.17.tar.gz
cd ~/redis-2.8.17 && make
make install

useradd --system --shell /bin/bash redis

mkdir /etc/redis
cp /vagrant/vagrant-bootstrap/redis-config.conf /etc/redis
cp /vagrant/vagrant-bootstrap/redis.conf /etc/init/

start kafka
start redis
