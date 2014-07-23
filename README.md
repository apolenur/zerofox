# primegen

Simple exercise which distribute calculation of primes from 0 to N across multiple workers.
Initially using JeroMQ. Can be extended to use other mechanism for work distribution

## Running

### Option 1

Running directly using gradle 

1. Edit  line 37 of build.gradle 
    `args = [300,12]`
    first parameter is upper limit for primes, second number of workers
2. Execute ./gradlew run.

### Option 2

1. Build a distribution using
`./gradlew install`
2. cd to build/install/primegen/bin
3. execute ./primegen upperLimit [numberOfWorkers]
