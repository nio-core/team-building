# Team Building

## Requirements
* docker
* docker-compose

## Running
`docker-compose -f devmode up` to run the corresponding sawtooth network.

after the network started, the transaction processor can be started from `CSVStringTP.main`  
it should be visible in the terminal that a transaction processor connected.

## Sending text
[Example](https://github.com/nio-core/team-building/blob/eeecf8991f780d9f834e5898cad8c1a59eb3931e/src/test/java/client/TextMessagesTest.java#L16)

## Sending contract
[Example](https://github.com/nio-core/team-building/blob/eeecf8991f780d9f834e5898cad8c1a59eb3931e/src/test/java/client/ContractsTest.java#L44)

### New contract types
New contract types can be added by implementing the `ContractProcessor` interface and adding them to the instance.
Specify the supported operation in the contract.
