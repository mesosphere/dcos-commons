# proxylite

This is a temporary routing solution to stretch the functionality that
Adminrouter provides.

## Usage

For more information on how to use this, refer to the developer guide:
https://mesosphere.github.io/dcos-commons/dev-guide/developer-guide.html

## Why this exists

Adminrouter only allows the exposure of 1 HTTP URL. `proxylite` is designed
sit behind this and do additional routing logic based on HTTP routes.
This allows a framework to expose multiple endpoints (for example, multiple
UIs) by having them behind different base HTTP routes.
