# Glossary

## D

### Deploy Plan
A Deploy Plan defines explicit ordering of work that needs to be performed in order to successfully deploy a given service.

## H

### Health Check
A health check represents a command that can be executed periodically to determine whether a given task is healthy or not. 

## P

### Plan
A `Plan` represents ordering of work across pods and assists Service Scheduler to perform orchestration. A Plan is composed of one or many `Phase`(s).

### Phase
A `Phase` is an ordered collection of `Step`(s).

### Step
A `Step` represents work that needs to be performed for a given `Pod` instance.

### Pod

## R

### Reservation
A Reservation of resources ensures that the resources allocated to a given task aren't reallocated to any other task in an event of failure, task restart, etc.

### Resource Set
A Resource Set is a collection of resources, like cpus, memory, ports, volumes, etc, that are pre-reserved and can be used to launch task(s) in a given pod-instance. A Pod can be composed of tasks using different resource sets.

## S

### Strategy

## T

### Task

## V

### Volume
