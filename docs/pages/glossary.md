---
layout: gh-basic
title: Glossary
---

## D

### Deploy Plan
A Deploy Plan explicity defines the order in which work needs to be performed in order to successfully deploy a given service.

## H

### Health Check
A health check is a command that can be executed periodically to determine whether or not a given task is healthy. 

## P

### Plan
A `Plan` orders work across pods and assists the Service Scheduler to perform orchestration. A Plan is composed of one or many `Phase`(s).

### Phase
A `Phase` is an ordered collection of `Step`(s).

### Step
A `Step` is work that needs to be performed for a given `Pod` instance.

### Pod
A `Pod` is a group of tasks that share the same resources (e.g., CPU, memory, volumes) and are operated on together.

## R

### Reservation
Resource Reservation ensures that the resources allocated to a given task aren't reallocated to any other task in the event of failure, task restart, etc.

### Resource Set
A Resource Set is a collection of resources, like CPUs, memory, ports, volumes, etc, that are pre-reserved and can be used to launch task(s) in a given Pod instance. A Pod can be composed of tasks using different resource sets.

## S

### Strategy
Strategy determines whether children of a given Plan (i.e., Phase) or Phase (i.e., Step) are executed in serial or parallel.

## T

### Task
A Task is a unit of execution of work.

## V

### Volume
A Volume represents reserved and persistent storage that is available to a framework across task executions.
