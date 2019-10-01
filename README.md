# HyperZMQ

## Requirements
* docker
* docker-compose

## Running
`docker-compose -f sawtooth_devmode up` to run the corresponding sawtooth network.

after the network started, the transaction processor can be started from `CSVStringTP.main`  
it should be visible in the terminal that a transaction processor connected.

now the transaction can be sent (+event subscription) from `Main`
